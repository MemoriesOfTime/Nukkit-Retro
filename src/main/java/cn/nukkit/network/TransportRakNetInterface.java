package cn.nukkit.network;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerCreationEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ProtocolInfo.SinceProtocol;
import cn.nukkit.network.protocol.ProtocolInfo.SupportedProtocol;
import cn.nukkit.network.protocol.ProtocolInfo.UnsupportedSince;
import cn.nukkit.raknet.RakNet;
import cn.nukkit.raknet.protocol.packet.PING_DataPacket;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.Utils;
import cn.nukkit.utils.Zlib;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.cloudburstmc.netty.channel.raknet.*;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.channel.raknet.config.RakServerCookieMode;
import org.cloudburstmc.netty.channel.raknet.packet.RakMessage;
import org.cloudburstmc.netty.handler.codec.raknet.common.EncapsulatedToMessageHandler;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRouteHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class TransportRakNetInterface implements AdvancedSourceInterface {

    private static final String RAW_DATAGRAM_HANDLER_NAME = "nukkit-raw-datagram-handler";

    private final Server server;
    private final EventLoopGroup group;
    private final RakServerChannel serverChannel;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> networkLatency = new ConcurrentHashMap<>();
    private final Map<Integer, String> identifiers = new ConcurrentHashMap<>();
    private final Map<String, Integer> identifiersACK = new ConcurrentHashMap<>();
    private final Map<String, RakChildChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, String> disconnectReasons = new ConcurrentHashMap<>();
    private final Queue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<>();

    private volatile Network network;

    public TransportRakNetInterface(Server server) {
        this.server = server;
        this.group = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(this.group)
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_GUID, this.server.getServerUniqueId().getLeastSignificantBits())
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, new int[]{RakNet.PROTOCOL})
                .option(RakChannelOption.RAK_UNCONNECTED_MAGIC, Unpooled.wrappedBuffer(RakNet.MAGIC))
                .option(RakChannelOption.RAK_SERVER_COOKIE_MODE, RakServerCookieMode.ACTIVE)
                .option(RakChannelOption.RAK_MAX_CONNECTIONS, this.server.getMaxPlayers())
                .option(RakChannelOption.RAK_ADVERTISEMENT, this.createAdvertisement(this.server.getMotd()))
                .childOption(RakChannelOption.RAK_ORDERING_CHANNELS, Network.CHANNEL_END + 1)
                .handler(new ChannelInitializer<RakServerChannel>() {
                    @Override
                    protected void initChannel(RakServerChannel ch) {
                        ch.pipeline().addAfter(RakServerRouteHandler.NAME, RAW_DATAGRAM_HANDLER_NAME, new RawDatagramHandler());
                    }
                })
                .childHandler(new ChannelInitializer<RakChildChannel>() {
                    @Override
                    protected void initChannel(RakChildChannel ch) {
                        ch.pipeline().addLast(EncapsulatedToMessageHandler.NAME, EncapsulatedToMessageHandler.INSTANCE);
                        ch.pipeline().addLast(new ChildChannelHandler());
                    }
                });

        String bindAddress = this.server.getIp();
        if (bindAddress == null || bindAddress.isEmpty()) {
            bindAddress = "0.0.0.0";
        }

        ChannelFuture bindFuture = bootstrap.bind(new InetSocketAddress(bindAddress, this.server.getPort())).syncUninterruptibly();
        if (!bindFuture.isSuccess()) {
            this.group.shutdownGracefully();
            throw new IllegalStateException("Failed to bind transport-raknet interface", bindFuture.cause());
        }

        this.serverChannel = (RakServerChannel) bindFuture.channel();
    }

    private static String identifierOf(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        boolean work = false;
        Runnable task;
        while ((task = this.mainThreadQueue.poll()) != null) {
            work = true;
            try {
                task.run();
            } catch (Exception e) {
                this.server.getLogger().logException(e);
            }
        }
        return work;
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet) {
        return this.putPacket(player, packet, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK) {
        return this.putPacket(player, packet, needACK, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        if (ProtocolInfo.isBefore0150(player.protocol)) {
            return this.putLegacyPacket(player, packet, needACK, immediate);
        }

        if (!this.identifiers.containsKey(player.rawHashCode())) {
            return null;
        }

        byte[] buffer;
        if (player.useUncompressedBatch && packet.pid() != ProtocolInfo.BATCH_PACKET) {
            packet.tryEncode();
            buffer = packet.getBuffer();
        } else if (packet.pid() == ProtocolInfo.BATCH_PACKET) {
            byte[] payload = ((BatchPacket) packet).payload;
            if (ProtocolInfo.isBefore0160(player.protocol)) {
                buffer = Binary.appendBytes(new byte[]{0x06}, Binary.writeInt(payload.length), payload);
            } else {
                buffer = payload;
            }
        } else if (!needACK) {
            this.server.batchPackets(new Player[]{player}, new DataPacket[]{packet}, true);
            return null;
        } else {
            packet.tryEncode();
            buffer = packet.getBuffer();
            try {
                byte[] raw = Binary.appendBytes(Binary.writeUnsignedVarInt(buffer.length), buffer);
                buffer = player.useRawDeflate ? Zlib.deflateRaw(raw, Server.getInstance().networkCompressionLevel) : Zlib.deflate(raw, Server.getInstance().networkCompressionLevel);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return this.sendWrappedPacket(player, packet, Binary.appendBytes((byte) 0xfe, buffer), needACK, immediate);
    }

    @SinceProtocol(ProtocolInfo.v0_12_0)
    @UnsupportedSince(ProtocolInfo.v0_15_0)
    private Integer putLegacyPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        if (!this.identifiers.containsKey(player.rawHashCode())) {
            return null;
        }

        packet.tryEncode();
        byte[] buffer = packet.getBuffer();
        if (!immediate && !needACK && packet.pid() != ProtocolInfo.BATCH_PACKET && buffer != null && buffer.length >= 512) {
            this.server.batchPackets(new Player[]{player}, new DataPacket[]{packet}, true);
            return null;
        }

        boolean use0x8ePrefix = !ProtocolInfo.isBefore0140(player.protocol);
        byte[] payload = use0x8ePrefix ? Binary.appendBytes((byte) 0x8e, buffer) : buffer;
        return this.sendWrappedPacket(player, packet, payload, needACK, immediate);
    }

    private Integer sendWrappedPacket(Player player, DataPacket packet, byte[] payload, boolean needACK, boolean immediate) {
        String identifier = this.identifiers.get(player.rawHashCode());
        RakChildChannel channel = this.channels.get(identifier);
        if (channel == null || !channel.isOpen()) {
            return null;
        }

        Integer identifierACK = null;
        if (!needACK) {
            if (packet.encapsulatedPacket == null) {
                packet.encapsulatedPacket = new CacheEncapsulatedPacket();
                packet.encapsulatedPacket.identifierACK = null;
                packet.encapsulatedPacket.buffer = payload;
                if (packet.getChannel() != 0) {
                    packet.encapsulatedPacket.reliability = 3;
                    packet.encapsulatedPacket.orderChannel = packet.getChannel();
                    packet.encapsulatedPacket.orderIndex = 0;
                } else {
                    packet.encapsulatedPacket.reliability = 2;
                }
            }
            payload = packet.encapsulatedPacket.buffer;
        } else {
            if (packet.getChannel() != 0) {
                packet.reliability = 3;
                packet.orderChannel = packet.getChannel();
                packet.orderIndex = 0;
            } else {
                packet.reliability = 2;
            }

            int iACK = this.identifiersACK.getOrDefault(identifier, 0) + 1;
            this.identifiersACK.put(identifier, iACK);
            identifierACK = iACK;
        }

        RakReliability reliability = this.resolveReliability(packet.getChannel(), needACK);
        RakPriority priority = immediate ? RakPriority.IMMEDIATE : RakPriority.NORMAL;
        ChannelFuture future = channel.writeAndFlush(new RakMessage(Unpooled.wrappedBuffer(payload), reliability, priority, packet.getChannel()));

        if (identifierACK != null) {
            final int finalIdentifierACK = identifierACK;
            future.addListener(listener -> {
                if (listener.isSuccess()) {
                    this.mainThreadQueue.offer(() -> this.notifyPlayerAck(identifier, finalIdentifierACK));
                }
            });
        }

        return identifierACK;
    }

    @Override
    public int getNetworkLatency(Player player) {
        String identifier = this.identifiers.get(player.rawHashCode());
        if (identifier == null) {
            return 0;
        }
        return this.networkLatency.getOrDefault(identifier, 0);
    }

    @Override
    public void close(Player player) {
        this.close(player, "unknown reason");
    }

    @Override
    public void close(Player player, String reason) {
        String identifier = this.identifiers.remove(player.rawHashCode());
        if (identifier == null) {
            return;
        }

        this.players.remove(identifier);
        this.networkLatency.remove(identifier);
        this.identifiersACK.remove(identifier);
        this.disconnectReasons.remove(identifier);

        RakChildChannel channel = this.channels.remove(identifier);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    public void closeSession(String identifier, String reason) {
        Player player = this.players.remove(identifier);
        this.networkLatency.remove(identifier);
        this.identifiersACK.remove(identifier);
        this.disconnectReasons.remove(identifier);

        RakChildChannel channel = this.channels.remove(identifier);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        if (player != null) {
            this.identifiers.remove(player.rawHashCode());
            player.close(player.getLeaveMessage(), reason);
        }
    }

    @Override
    public void shutdown() {
        if (this.serverChannel != null && this.serverChannel.isOpen()) {
            this.serverChannel.close().syncUninterruptibly();
        }
        this.group.shutdownGracefully().syncUninterruptibly();
    }

    @Override
    public void emergencyShutdown() {
        this.shutdown();
    }

    @Override
    public void blockAddress(String address) {
        this.blockAddress(address, 300);
    }

    @Override
    public void blockAddress(String address, int timeout) {
        InetAddress inetAddress = this.parseAddress(address);
        if (inetAddress == null || this.serverChannel == null) {
            return;
        }

        long duration = timeout < 0 ? 3650L : timeout;
        TimeUnit unit = timeout < 0 ? TimeUnit.DAYS : TimeUnit.SECONDS;
        this.serverChannel.tryBlockAddress(inetAddress, duration, unit);
    }

    @Override
    public void unblockAddress(String address) {
        InetAddress inetAddress = this.parseAddress(address);
        if (inetAddress == null || this.serverChannel == null) {
            return;
        }

        RakServerRateLimiter rateLimiter = this.serverChannel.pipeline().get(RakServerRateLimiter.class);
        if (rateLimiter != null) {
            rateLimiter.unblockAddress(inetAddress);
        }
    }

    @Override
    public void sendRawPacket(String address, int port, byte[] payload) {
        if (this.serverChannel == null || !this.serverChannel.isOpen()) {
            return;
        }

        this.serverChannel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(payload), new InetSocketAddress(address, port)));
    }

    @Override
    public void setName(String name) {
        if (this.serverChannel == null) {
            return;
        }

        this.serverChannel.config().setOption(RakChannelOption.RAK_ADVERTISEMENT, this.createAdvertisement(name));
        QueryRegenerateEvent info = this.server.getQueryInformation();
        this.server.getLogger().debug("Updated transport-raknet advertisement: " + info.getPlayerCount() + "/" + info.getMaxPlayerCount());
    }

    public void openSession(String identifier, String address, int port, long clientID) {
        PlayerCreationEvent ev = new PlayerCreationEvent(this, Player.class, Player.class, clientID, address, port);
        this.server.getPluginManager().callEvent(ev);
        Class<? extends Player> clazz = ev.getPlayerClass();

        try {
            Constructor<? extends Player> constructor = clazz.getConstructor(SourceInterface.class, Long.class, String.class, int.class);
            Player player = constructor.newInstance(this, ev.getClientId(), ev.getAddress(), ev.getPort());
            this.players.put(identifier, player);
            this.networkLatency.put(identifier, 0);
            this.identifiersACK.put(identifier, 0);
            this.identifiers.put(player.rawHashCode(), identifier);
            this.server.addPlayer(identifier, player);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            Server.getInstance().getLogger().logException(e);
        }
    }

    private void handleRakMessage(String identifier, byte[] buffer) {
        Player player = this.players.get(identifier);
        if (player == null) {
            return;
        }

        DataPacket pk = null;
        try {
            if (buffer.length > 0) {
                if (buffer[0] == PING_DataPacket.ID) {
                    PING_DataPacket pingPacket = new PING_DataPacket();
                    pingPacket.buffer = buffer;
                    pingPacket.decode();
                    this.networkLatency.put(identifier, (int) pingPacket.pingID);
                    return;
                }

                pk = this.getPacket(player, buffer);
                if (pk != null) {
                    pk.decode();
                    player.handleDataPacket(pk);
                }
            }
        } catch (Exception e) {
            this.server.getLogger().logException(e);
            if (Nukkit.DEBUG > 1 && pk != null) {
                MainLogger logger = this.server.getLogger();
                logger.debug("Packet " + pk.getClass().getName() + " 0x" + Binary.bytesToHexString(buffer));
            }

            this.blockAddress(player.getAddress(), 5);
        }
    }

    private void notifyPlayerAck(String identifier, int identifierACK) {
        Player player = this.players.get(identifier);
        if (player != null) {
            player.notifyACK(identifierACK);
        }
    }

    private RakReliability resolveReliability(int channel, boolean needACK) {
        if (channel == 0) {
            return needACK ? RakReliability.RELIABLE_WITH_ACK_RECEIPT : RakReliability.RELIABLE;
        }
        return needACK ? RakReliability.RELIABLE_ORDERED_WITH_ACK_RECEIPT : RakReliability.RELIABLE_ORDERED;
    }

    private ByteBuf createAdvertisement(String name) {
        QueryRegenerateEvent info = this.server.getQueryInformation();
        String advertisement = "MCPE;" + Utils.rtrim(name.replace(";", "\\;"), '\\') + ";" +
                ProtocolInfo.CURRENT_PROTOCOL + ";" +
                ProtocolInfo.MINECRAFT_VERSION_NETWORK + ";" +
                info.getPlayerCount() + ";" +
                info.getMaxPlayerCount();
        return Unpooled.wrappedBuffer(advertisement.getBytes(StandardCharsets.UTF_8));
    }

    private InetAddress parseAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            this.server.getLogger().warning("Failed to resolve address for transport-raknet: " + address, e);
            return null;
        }
    }

    private DataPacket getPacket(Player player, byte[] buffer) {
        if (buffer.length == 0) {
            return null;
        }

        if (buffer[0] == (byte) 0xfe) {
            if (buffer.length >= 2) {
                int packetId = buffer[1] & 0xff;

                if (Nukkit.DEBUG > 0) {
                    this.server.getLogger().info("[RakNet] 0xFE packet from " + player.getAddress()
                            + " packetId=0x" + Integer.toHexString(packetId)
                            + " protocol=" + player.protocol
                            + " useUncompressedBatch=" + player.useUncompressedBatch);
                }

                if (player.protocol != Integer.MAX_VALUE && ProtocolInfo.isBefore0140(player.protocol)) {
                    DataPacket data = this.network.getPacket(packetId, player.protocol);
                    if (data != null) {
                        data.protocol = player.protocol;
                        data.setBuffer(buffer, 2);
                        this.logLegacyPacketResolution(player, buffer, data.protocol, 2);
                        return data;
                    }
                }

                if (player.protocol == Integer.MAX_VALUE
                        && packetId == (ProtocolInfo.LOGIN_PACKET & 0xff)
                        && buffer.length >= 7) {
                    int possibleProtocol = Binary.readInt(Binary.subBytes(buffer, 2, 4));
                    if (ProtocolInfo.isSupportedProtocol(possibleProtocol) && !ProtocolInfo.isBefore0160(possibleProtocol)) {
                        player.useUncompressedBatch = true;
                        if (Nukkit.DEBUG > 1) {
                            this.server.getLogger().debug("[UncompressedBatch] Detected uncompressed LoginPacket from " + player.getAddress() + " protocol=" + possibleProtocol);
                        }
                        DataPacket data = this.network.getPacket(ProtocolInfo.LOGIN_PACKET);
                        if (data != null) {
                            data.protocol = Integer.MAX_VALUE;
                            data.setBuffer(buffer, 2);
                            return data;
                        }
                    }
                }

                if (player.useUncompressedBatch && player.protocol != Integer.MAX_VALUE) {
                    if (Nukkit.DEBUG > 1) {
                        this.server.getLogger().debug("[UncompressedBatch] Processing packet 0x" + Integer.toHexString(packetId) + " for " + player.getAddress() + " protocol=" + player.protocol);
                    }
                    DataPacket data = this.network.getPacket(packetId, player.protocol);
                    if (data == null) {
                        data = this.network.getPacket(packetId, ProtocolInfo.CURRENT_PROTOCOL);
                    }
                    if (data != null) {
                        data.protocol = player.protocol;
                        data.setBuffer(buffer, 2);
                        return data;
                    }
                    this.server.getLogger().warning("[UncompressedBatch] Unknown packet 0x" + Integer.toHexString(packetId) + " from " + player.getAddress() + " protocol=" + player.protocol);
                    return null;
                }

                if (player.useUncompressedBatch && player.protocol == Integer.MAX_VALUE) {
                    if (Nukkit.DEBUG > 1) {
                        this.server.getLogger().debug("[UncompressedBatch] Processing packet 0x" + Integer.toHexString(packetId) + " for " + player.getAddress() + " protocol=UNKNOWN");
                    }
                    DataPacket data = this.network.getPacket(packetId, ProtocolInfo.v1_0_5);
                    if (data != null) {
                        data.protocol = ProtocolInfo.v1_0_5;
                        data.setBuffer(buffer, 2);
                        return data;
                    }
                }

                if (player.protocol == Integer.MAX_VALUE && packetId >= 0x8f) {
                    @SupportedProtocol int protocol = ProtocolInfo.v0_13_2;
                    if (packetId == 0x8f && buffer.length >= 8) {
                        int usernameLength = Binary.readShort(Binary.subBytes(buffer, 2, 2)) & 0xffff;
                        int protocolOffset = 4 + usernameLength;
                        if (protocolOffset + 4 <= buffer.length) {
                            int clientProtocol = Binary.readInt(Binary.subBytes(buffer, protocolOffset, 4));
                            if (ProtocolInfo.isSupportedProtocol(clientProtocol) && clientProtocol < ProtocolInfo.v0_15_0) {
                                protocol = clientProtocol;
                            }
                        }
                    }

                    DataPacket data = this.network.getPacket(packetId, protocol);
                    if (data != null) {
                        data.protocol = packetId == 0x92 ? Integer.MAX_VALUE : protocol;
                        data.setBuffer(buffer, 2);
                        this.logLegacyPacketResolution(player, buffer, data.protocol, 2);
                        return data;
                    }
                }
            }

            DataPacket data = new BatchPacket();
            data.protocol = player.protocol;
            int start = 1;
            if (buffer.length >= 6 && buffer[1] == 0x06) {
                start = player.useUncompressedBatch ? 2 : 6;
            } else if (buffer.length >= 2 && Zlib.hasZlibHeader(Binary.subBytes(buffer, 1, 2))) {
                start = 1;
                if (Nukkit.DEBUG > 1) {
                    this.server.getLogger().debug("[BatchPacket] Detected zlib header at offset 1 for " + player.getAddress());
                }
            }
            if (Nukkit.DEBUG > 1) {
                this.server.getLogger().debug("[BatchPacket] Falling through to batch path for " + player.getAddress()
                        + " protocol=" + player.protocol + " useUncompressedBatch=" + player.useUncompressedBatch
                        + " packetId=0x" + Integer.toHexString(buffer[1] & 0xff) + " start=" + start);
            }
            data.setBuffer(buffer, start);
            this.logLegacyPacketResolution(player, buffer, data.protocol, start);
            return data;
        }

        if (buffer.length >= 2 && buffer[0] == (byte) 0x8e) {
            @SupportedProtocol int protocol = player.protocol == Integer.MAX_VALUE ? ProtocolInfo.v0_14_2 : player.protocol;
            DataPacket data = this.network.getPacket(buffer[1] & 0xff, protocol);
            if (data == null) {
                return null;
            }
            data.protocol = protocol;
            data.setBuffer(buffer, 2);
            this.logLegacyPacketResolution(player, buffer, data.protocol, 2);
            return data;
        }

        if (player.protocol != Integer.MAX_VALUE && ProtocolInfo.isBefore0140(player.protocol) && buffer.length >= 1) {
            @SupportedProtocol int protocol = player.protocol;
            DataPacket data = this.network.getPacket(buffer[0] & 0xff, protocol);
            if (data == null) {
                return null;
            }
            data.protocol = protocol;
            data.setBuffer(buffer, 1);
            this.logLegacyPacketResolution(player, buffer, data.protocol, 1);
            return data;
        }

        if (player.protocol == Integer.MAX_VALUE) {
            int packetId = buffer[0] & 0xff;
            if (packetId == 0x8f && buffer.length >= 7) {
                @SupportedProtocol int protocol = ProtocolInfo.v0_13_2;
                int usernameLength = Binary.readShort(Binary.subBytes(buffer, 1, 2)) & 0xffff;
                int protocolOffset = 3 + usernameLength;
                if (protocolOffset + 4 <= buffer.length) {
                    int clientProtocol = Binary.readInt(Binary.subBytes(buffer, protocolOffset, 4));
                    if (ProtocolInfo.isSupportedProtocol(clientProtocol) && clientProtocol < ProtocolInfo.v0_15_0) {
                        protocol = clientProtocol;
                    }
                }

                DataPacket data = this.network.getPacket(packetId, protocol);
                if (data != null) {
                    data.protocol = protocol;
                    data.setBuffer(buffer, 1);
                    this.logLegacyPacketResolution(player, buffer, data.protocol, 1);
                    return data;
                }
            }

            DataPacket data = this.network.getPacket(packetId, ProtocolInfo.v0_13_2);
            if (data != null) {
                data.protocol = packetId == 0x92 ? Integer.MAX_VALUE : ProtocolInfo.v0_13_2;
                data.setBuffer(buffer, 1);
                this.logLegacyPacketResolution(player, buffer, data.protocol, 1);
                return data;
            }
        }

        this.logUnresolvedLegacyPacket(player, buffer);
        return null;
    }

    private void logLegacyPacketResolution(Player player, byte[] buffer, int protocol, int startOffset) {
        if (this.server == null || Nukkit.DEBUG <= 1 || !this.looksLikeLegacyPacket(buffer)) {
            return;
        }

        String protocolText = protocol == Integer.MAX_VALUE ? "unknown" : String.valueOf(protocol);
        this.server.getLogger().debug("Resolved legacy packet from " + player.getAddress()
                + " head=0x" + Binary.bytesToHexString(Binary.subBytes(buffer, 0, Math.min(buffer.length, 12)))
                + ", start=" + startOffset
                + ", protocol=" + protocolText);
    }

    private void logUnresolvedLegacyPacket(Player player, byte[] buffer) {
        if (this.server == null || player == null || !this.looksLikeLegacyPacket(buffer)) {
            return;
        }

        String protocolText = player.protocol == Integer.MAX_VALUE ? "unknown" : String.valueOf(player.protocol);
        this.server.getLogger().notice("Unresolved legacy packet from " + player.getAddress()
                + " protocol=" + protocolText
                + " head=0x" + Binary.bytesToHexString(Binary.subBytes(buffer, 0, Math.min(buffer.length, 16)))
                + " length=" + buffer.length);
    }

    private boolean looksLikeLegacyPacket(byte[] buffer) {
        if (buffer.length == 0) {
            return false;
        }

        int first = buffer[0] & 0xff;
        if (first == 0x8e || first == 0x8f || first == 0x92) {
            return true;
        }

        return first == 0xfe && buffer.length >= 2 && ((buffer[1] & 0xff) >= 0x8f);
    }

    private void logThrowable(Throwable cause) {
        if (cause instanceof Exception) {
            this.server.getLogger().logException((Exception) cause);
        } else {
            this.server.getLogger().error(cause.getMessage(), cause);
        }
    }

    private final class RawDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            byte[] payload = new byte[packet.content().readableBytes()];
            packet.content().getBytes(packet.content().readerIndex(), payload);
            String address = packet.sender().getAddress().getHostAddress();
            int port = packet.sender().getPort();
            mainThreadQueue.offer(() -> server.handlePacket(address, port, payload));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logThrowable(cause);
        }
    }

    private final class ChildChannelHandler extends SimpleChannelInboundHandler<RakMessage> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            RakChildChannel channel = (RakChildChannel) ctx.channel();
            InetSocketAddress remoteAddress = channel.remoteAddress();
            String identifier = identifierOf(remoteAddress);
            channels.put(identifier, channel);
            mainThreadQueue.offer(() -> openSession(identifier, remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort(), channel.config().getGuid()));
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            RakChildChannel channel = (RakChildChannel) ctx.channel();
            String identifier = identifierOf(channel.remoteAddress());
            String reason = disconnectReasons.remove(identifier);
            if (reason == null) {
                reason = "disconnected";
            }
            final String finalReason = reason;
            mainThreadQueue.offer(() -> closeSession(identifier, finalReason));
            ctx.fireChannelInactive();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt != null) {
                disconnectReasons.put(identifierOf(((RakChildChannel) ctx.channel()).remoteAddress()), String.valueOf(evt));
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RakMessage message) {
            RakChildChannel channel = (RakChildChannel) ctx.channel();
            byte[] buffer = new byte[message.content().readableBytes()];
            message.content().getBytes(message.content().readerIndex(), buffer);
            String identifier = identifierOf(channel.remoteAddress());
            mainThreadQueue.offer(() -> handleRakMessage(identifier, buffer));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logThrowable(cause);
        }
    }
}
