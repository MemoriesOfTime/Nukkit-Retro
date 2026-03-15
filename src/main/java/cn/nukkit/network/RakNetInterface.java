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
import cn.nukkit.raknet.protocol.EncapsulatedPacket;
import cn.nukkit.raknet.protocol.packet.PING_DataPacket;
import cn.nukkit.raknet.server.RakNetServer;
import cn.nukkit.raknet.server.ServerHandler;
import cn.nukkit.raknet.server.ServerInstance;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.Utils;
import cn.nukkit.utils.Zlib;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class RakNetInterface implements ServerInstance, AdvancedSourceInterface {

    private final Server server;

    private Network network;

    private final RakNetServer raknet;

    private final Map<String, Player> players = new ConcurrentHashMap<>();

    private final Map<String, Integer> networkLatency = new ConcurrentHashMap<>();

    private final Map<Integer, String> identifiers = new ConcurrentHashMap<>();

    private final Map<String, Integer> identifiersACK = new ConcurrentHashMap<>();

    private final ServerHandler handler;

    public RakNetInterface(Server server) {
        this.server = server;

        this.raknet = new RakNetServer(this.server.getLogger(), this.server.getPort(), this.server.getIp().equals("") ? "0.0.0.0" : this.server.getIp());
        this.handler = new ServerHandler(this.raknet, this);
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        boolean work = false;
        if (this.handler.handlePacket()) {
            work = true;
            while (this.handler.handlePacket()) {

            }
        }

        return work;
    }

    @Override
    public void closeSession(String identifier, String reason) {
        if (this.players.containsKey(identifier)) {
            Player player = this.players.get(identifier);
            this.identifiers.remove(player.rawHashCode());
            this.players.remove(identifier);
            this.networkLatency.remove(identifier);
            this.identifiersACK.remove(identifier);
            player.close(player.getLeaveMessage(), reason);
        }
    }

    @Override
    public int getNetworkLatency(Player player) {
        return this.networkLatency.get(this.identifiers.get(player.rawHashCode()));
    }

    @Override
    public void close(Player player) {
        this.close(player, "unknown reason");
    }

    @Override
    public void close(Player player, String reason) {
        if (this.identifiers.containsKey(player.rawHashCode())) {
            String id = this.identifiers.get(player.rawHashCode());
            this.players.remove(id);
            this.networkLatency.remove(id);
            this.identifiersACK.remove(id);
            this.closeSession(id, reason);
            this.identifiers.remove(player.rawHashCode());
        }
    }

    @Override
    public void shutdown() {
        this.handler.shutdown();
    }

    @Override
    public void emergencyShutdown() {
        this.handler.emergencyShutdown();
    }

    @Override
    public void openSession(String identifier, String address, int port, long clientID) {
        PlayerCreationEvent ev = new PlayerCreationEvent(this, Player.class, Player.class, null, address, port);
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

    @Override
    public void handleEncapsulated(String identifier, EncapsulatedPacket packet, int flags) {
        if (this.players.containsKey(identifier)) {
            Player player = this.players.get(identifier);
            DataPacket pk = null;
            try {
                if (packet.buffer.length > 0) {
                    if (packet.buffer[0] == PING_DataPacket.ID) {
                        PING_DataPacket pingPacket = new PING_DataPacket();
                        pingPacket.buffer = packet.buffer;
                        pingPacket.decode();

                        this.networkLatency.put(identifier, (int) pingPacket.pingID);
                        return;
                    }

                    pk = this.getPacket(player, packet.buffer);
                    if (pk != null) {
                        pk.decode();
                        player.handleDataPacket(pk);
                    }
                }
            } catch (Exception e) {
                this.server.getLogger().logException(e);
                if (Nukkit.DEBUG > 1 && pk != null) {
                    MainLogger logger = this.server.getLogger();
                    logger.debug("Packet " + pk.getClass().getName() + " 0x" + Binary.bytesToHexString(packet.buffer));
                }

                if (this.players.containsKey(identifier)) {
                    this.handler.blockAddress(this.players.get(identifier).getAddress(), 5);
                }
            }
        }
    }

    @Override
    public void blockAddress(String address) {
        this.blockAddress(address, 300);
    }

    @Override
    public void blockAddress(String address, int timeout) {
        this.handler.blockAddress(address, timeout);
    }

    @Override
    public void unblockAddress(String address) {
        this.handler.unblockAddress(address);
    }

    @Override
    public void handleRaw(String address, int port, byte[] payload) {
        this.server.handlePacket(address, port, payload);
    }

    @Override
    public void sendRawPacket(String address, int port, byte[] payload) {
        this.handler.sendRaw(address, port, payload);
    }

    @Override
    public void notifyACK(String identifier, int identifierACK) {
        // TODO: Better ACK notification implementation!
        for (Player p : server.getOnlinePlayers().values()) {
            p.notifyACK(identifierACK);
        }
    }

    @Override
    public void setName(String name) {
        QueryRegenerateEvent info = this.server.getQueryInformation();

        this.handler.sendOption("name",
                "MCPE;" + Utils.rtrim(name.replace(";", "\\;"), '\\') + ";" +
                        ProtocolInfo.CURRENT_PROTOCOL + ";" +
                        ProtocolInfo.MINECRAFT_VERSION_NETWORK + ";" +
                        info.getPlayerCount() + ";" +
                        info.getMaxPlayerCount());
    }

    public void setPortCheck(boolean value) {
        this.handler.sendOption("portChecking", String.valueOf(value));
    }

    @Override
    public void handleOption(String name, String value) {
        if ("bandwidth".equals(name)) {
            String[] v = value.split(";");
            this.network.addStatistics(Double.valueOf(v[0]), Double.valueOf(v[1]));
        }
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

        if (this.identifiers.containsKey(player.rawHashCode())) {
            byte[] buffer;
            if (player.useUncompressedBatch && packet.pid() != ProtocolInfo.BATCH_PACKET) {
                // 未压缩格式客户端：直接发送编码后的数据包，不经过批处理/压缩
                packet.tryEncode();
                buffer = packet.getBuffer();
            } else if (packet.pid() == ProtocolInfo.BATCH_PACKET) {
                byte[] payload = ((BatchPacket) packet).payload;
                if (ProtocolInfo.isBefore0160(player.protocol)) {
                    // 0.15.x: [packetID(0x06)][payload_length:int32BE][compressed_data]
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
            String identifier = this.identifiers.get(player.rawHashCode());
            EncapsulatedPacket pk = null;
            if (!needACK) {
                if (packet.encapsulatedPacket == null) {
                    packet.encapsulatedPacket = new CacheEncapsulatedPacket();
                    packet.encapsulatedPacket.identifierACK = null;
                    packet.encapsulatedPacket.buffer = Binary.appendBytes((byte) 0xfe, buffer);
                    if (packet.getChannel() != 0) {
                        packet.encapsulatedPacket.reliability = 3;
                        packet.encapsulatedPacket.orderChannel = packet.getChannel();
                        packet.encapsulatedPacket.orderIndex = 0;
                    } else {
                        packet.encapsulatedPacket.reliability = 2;
                    }
                }
                pk = packet.encapsulatedPacket;
            }

            if (pk == null) {
                pk = new EncapsulatedPacket();
                pk.buffer = Binary.appendBytes((byte) 0xfe, buffer);
                if (packet.getChannel() != 0) {
                    packet.reliability = 3;
                    packet.orderChannel = packet.getChannel();
                    packet.orderIndex = 0;
                } else {
                    packet.reliability = 2;
                }

                if (needACK) {
                    int iACK = this.identifiersACK.get(identifier);
                    iACK++;
                    pk.identifierACK = iACK;
                    this.identifiersACK.put(identifier, iACK);
                }
            }

            this.handler.sendEncapsulated(identifier, pk, (needACK ? RakNet.FLAG_NEED_ACK : 0) | (immediate ? RakNet.PRIORITY_IMMEDIATE : RakNet.PRIORITY_NORMAL));

            return pk.identifierACK;
        }

        return null;
    }

    @SinceProtocol(ProtocolInfo.v0_12_0)
    @UnsupportedSince(ProtocolInfo.v0_15_0)
    private Integer putLegacyPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        if (!this.identifiers.containsKey(player.rawHashCode())) {
            return null;
        }

        packet.tryEncode();

        byte[] buffer = packet.getBuffer();
        String identifier = this.identifiers.get(player.rawHashCode());
        EncapsulatedPacket pk = null;

        if (!immediate && !needACK && packet.pid() != ProtocolInfo.BATCH_PACKET && buffer != null && buffer.length >= 512) {
            this.server.batchPackets(new Player[]{player}, new DataPacket[]{packet}, true);
            return null;
        }

        boolean use0x8ePrefix = !ProtocolInfo.isBefore0140(player.protocol);

        if (!needACK) {
            if (packet.encapsulatedPacket == null) {
                packet.encapsulatedPacket = new CacheEncapsulatedPacket();
                packet.encapsulatedPacket.identifierACK = null;
                packet.encapsulatedPacket.buffer = use0x8ePrefix ? Binary.appendBytes((byte) 0x8e, buffer) : buffer;
                if (packet.getChannel() != 0) {
                    packet.encapsulatedPacket.reliability = 3;
                    packet.encapsulatedPacket.orderChannel = packet.getChannel();
                    packet.encapsulatedPacket.orderIndex = 0;
                } else {
                    packet.encapsulatedPacket.reliability = 2;
                }
            }
            pk = packet.encapsulatedPacket;
        }

        if (pk == null) {
            pk = new EncapsulatedPacket();
            pk.buffer = use0x8ePrefix ? Binary.appendBytes((byte) 0x8e, buffer) : buffer;
            if (packet.getChannel() != 0) {
                packet.reliability = 3;
                packet.orderChannel = packet.getChannel();
                packet.orderIndex = 0;
            } else {
                packet.reliability = 2;
            }

            if (needACK) {
                int iACK = this.identifiersACK.get(identifier);
                iACK++;
                pk.identifierACK = iACK;
                this.identifiersACK.put(identifier, iACK);
            }
        }

        this.handler.sendEncapsulated(identifier, pk, (needACK ? RakNet.FLAG_NEED_ACK : 0) | (immediate ? RakNet.PRIORITY_IMMEDIATE : RakNet.PRIORITY_NORMAL));
        return pk.identifierACK;
    }

    private DataPacket getPacket(Player player, byte[] buffer) {
        if (buffer.length == 0) {
            return null;
        }

        if (buffer[0] == (byte) 0xfe) {
            if (buffer.length >= 2) {
                int packetId = buffer[1] & 0xff;

                // Debug: log state for every 0xFE packet
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

                // 检测未压缩的 LoginPacket：某些 1.0.x 客户端在 0xfe 后直接发送原始 LoginPacket
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

                // 未压缩格式客户端的后续数据包：[0xfe][packetId][raw_data]
                if (player.useUncompressedBatch && player.protocol != Integer.MAX_VALUE) {
                    if (Nukkit.DEBUG > 1) {
                        this.server.getLogger().debug("[UncompressedBatch] Processing packet 0x" + Integer.toHexString(packetId) + " for " + player.getAddress() + " protocol=" + player.protocol);
                    }
                    DataPacket data = this.network.getPacket(packetId, player.protocol);
                    if (data == null) {
                        // 某些客户端使用标准 1.1.0 ID 而非协议特定的 ID，尝试回退
                        data = this.network.getPacket(packetId, ProtocolInfo.CURRENT_PROTOCOL);
                    }
                    if (data != null) {
                        data.protocol = player.protocol;
                        data.setBuffer(buffer, 2);
                        return data;
                    }
                    // 如果仍然找不到，记录警告但不回退到 BatchPacket
                    this.server.getLogger().warning("[UncompressedBatch] Unknown packet 0x" + Integer.toHexString(packetId) + " from " + player.getAddress() + " protocol=" + player.protocol);
                    return null;
                }

                // 未压缩格式客户端但协议未知时，也尝试按未压缩格式解析
                if (player.useUncompressedBatch && player.protocol == Integer.MAX_VALUE) {
                    if (Nukkit.DEBUG > 1) {
                        this.server.getLogger().debug("[UncompressedBatch] Processing packet 0x" + Integer.toHexString(packetId) + " for " + player.getAddress() + " protocol=UNKNOWN");
                    }
                    // 尝试从常用协议中查找
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

            DataPacket data = this.network.getPacket(ProtocolInfo.BATCH_PACKET);
            if (data == null) {
                return null;
            }
            data.protocol = player.protocol;
            int start = 1;
            if (buffer.length >= 6 && buffer[1] == 0x06) {
                // 对于 useUncompressedBatch 客户端，批处理包格式是 [0xFE][0x06][payload]，没有长度前缀
                // 对于标准客户端，批处理包格式是 [0xFE][0x06][int32 size][payload]
                start = player.useUncompressedBatch ? 2 : 6;
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

        // 协议未知且无 0xfe/0x8e 前缀时，尝试 0.12.x / 0.13.x 裸包格式
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
}
