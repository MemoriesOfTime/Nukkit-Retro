package cn.nukkit.network;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.ProtocolInfo.SinceProtocol;
import cn.nukkit.network.protocol.ProtocolInfo.SupportedProtocol;
import cn.nukkit.network.protocol.ProtocolInfo.UnsupportedSince;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class Network {

    public static final byte CHANNEL_NONE = 0;
    public static final byte CHANNEL_PRIORITY = 1; //Priority channel, only to be used when it matters
    public static final byte CHANNEL_WORLD_CHUNKS = 2; //Chunk sending
    public static final byte CHANNEL_MOVEMENT = 3; //Movement sending
    public static final byte CHANNEL_BLOCKS = 4; //Block updates or explosions
    public static final byte CHANNEL_WORLD_EVENTS = 5; //Entity, level or blockentity entity events
    public static final byte CHANNEL_ENTITY_SPAWNING = 6; //Entity spawn/despawn channel
    public static final byte CHANNEL_TEXT = 7; //Chat and other text stuff
    public static final byte CHANNEL_END = 31;

    private PacketPool packetPool60;
    private PacketPool packetPool39;
    private PacketPool packetPool81;
    private PacketPool packetPool84;
    private PacketPool packetPool91;
    private PacketPool packetPool92;
    private PacketPool packetPool100;
    private PacketPool packetPool101;
    private PacketPool packetPool102;
    private PacketPool packetPool105;
    private PacketPool packetPool113;

    private final Server server;

    private final Set<SourceInterface> interfaces = new HashSet<>();

    private final Set<AdvancedSourceInterface> advancedInterfaces = new HashSet<>();

    private double upload = 0;
    private double download = 0;

    private String name;

    public Network(Server server) {
        this.server = server;
        this.registerPackets();
    }

    public void addStatistics(double upload, double download) {
        this.upload += upload;
        this.download += download;
    }

    public double getUpload() {
        return upload;
    }

    public double getDownload() {
        return download;
    }

    public void resetStatistics() {
        this.upload = 0;
        this.download = 0;
    }

    public Set<SourceInterface> getInterfaces() {
        return interfaces;
    }

    public void processInterfaces() {
        for (SourceInterface interfaz : this.interfaces) {
            try {
                interfaz.process();
            } catch (Exception e) {
                if (Nukkit.DEBUG > 1) {
                    this.server.getLogger().logException(e);
                }

                interfaz.emergencyShutdown();
                this.unregisterInterface(interfaz);
                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.server.networkError", new String[]{interfaz.getClass().getName(), e.getMessage()}));
            }
        }
    }

    public void registerInterface(SourceInterface interfaz) {
        this.interfaces.add(interfaz);
        if (interfaz instanceof AdvancedSourceInterface) {
            this.advancedInterfaces.add((AdvancedSourceInterface) interfaz);
            ((AdvancedSourceInterface) interfaz).setNetwork(this);
        }
        interfaz.setName(this.name);
    }

    public void unregisterInterface(SourceInterface interfaz) {
        this.interfaces.remove(interfaz);
        this.advancedInterfaces.remove(interfaz);
    }

    public void setName(String name) {
        this.name = name;
        this.updateName();
    }

    public String getName() {
        return name;
    }

    public void updateName() {
        for (SourceInterface interfaz : this.interfaces) {
            interfaz.setName(this.name);
        }
    }

    public void registerPacket(byte id, Class<? extends DataPacket> clazz) {
        this.registerPacket(ProtocolInfo.CURRENT_PROTOCOL, id & 0xff, clazz);
    }

    public void registerPacket(@SupportedProtocol int protocol, int id, Class<? extends DataPacket> clazz) {
        PacketPool pool = this.getPacketPool(protocol);
        if (pool == null) {
            return;
        }

        this.setPacketPool(protocol, pool.toBuilder()
                .registerPacket(id, clazz)
                .build());
    }

    public Server getServer() {
        return server;
    }

    public void processBatch(BatchPacket packet, Player player) {
        byte[] data;
        try {
            data = Zlib.inflate(packet.payload, 64 * 1024 * 1024);
        } catch (Exception e) {
            if (Nukkit.DEBUG > 1) {
                this.server.getLogger().debug("Failed to inflate BatchPacket" + (player != null ? " from " + player.getName() : ""));
                this.server.getLogger().logException(e);
            }
            return;
        }

        int len = data.length;
        @SupportedProtocol int protocol = packet.protocol != Integer.MAX_VALUE ? packet.protocol : (player == null ? ProtocolInfo.CURRENT_PROTOCOL : player.protocol);

        // 当协议版本未知时，从批量包数据中预检测协议版本
        if (protocol == Integer.MAX_VALUE && len >= 5) {
            protocol = detectProtocolFromBatchData(data, len);
            if (player != null && player.protocol == Integer.MAX_VALUE) {
                player.protocol = protocol;
            }
            if (Nukkit.DEBUG > 1) {
                this.server.getLogger().debug("Pre-detected protocol " + protocol + " from batch data" + (player != null ? " for " + player.getAddress() : ""));
            }
        }

        if (ProtocolInfo.isBefore0160(protocol)) {
            // 0.13.x ~ 0.15.x: 内部包使用4字节大端 int 长度前缀
            int offset = 0;
            boolean isClassicLegacy = protocol < ProtocolInfo.v0_15_0;
            try {
                List<DataPacket> packets = new ArrayList<>();
                while (offset < len) {
                    int pkLen = Binary.readInt(Binary.subBytes(data, offset, 4));
                    offset += 4;
                    byte[] buf = Binary.subBytes(data, offset, pkLen);
                    offset += pkLen;

                    if (isClassicLegacy) {
                        if (buf.length < 1) {
                            continue;
                        }

                        boolean is014orLater = (buf[0] & 0xff) == 0x8e && buf.length >= 2;
                        int dataOffset;
                        int packetId;

                        if (is014orLater) {
                            dataOffset = 2;
                            packetId = buf[1] & 0xff;
                        } else {
                            dataOffset = 1;
                            packetId = buf[0] & 0xff;
                        }

                        @SupportedProtocol int pktProtocol = protocol;

                        if (packetId == 0x8f && buf.length >= 8) {
                            int usernameOffset = is014orLater ? 2 : 1;
                            if (usernameOffset + 2 <= buf.length) {
                                int usernameLength = Binary.readShort(Binary.subBytes(buf, usernameOffset, 2)) & 0xffff;
                                int protocolOffset = usernameOffset + 2 + usernameLength;
                                if (protocolOffset + 4 <= buf.length) {
                                    int clientProtocol = Binary.readInt(Binary.subBytes(buf, protocolOffset, 4));
                                    if (ProtocolInfo.isSupportedProtocol(clientProtocol) && clientProtocol < ProtocolInfo.v0_15_0) {
                                        pktProtocol = clientProtocol;
                                        if (player != null && player.protocol == Integer.MAX_VALUE) {
                                            player.protocol = clientProtocol;
                                        }
                                        protocol = clientProtocol;
                                        if (Nukkit.DEBUG > 1) {
                                            this.server.getLogger().debug("Classic LoginPacket detected: clientProtocol=" + clientProtocol + ", playerProtocol=" + protocol + (player != null ? ", player=" + player.getAddress() : ""));
                                        }
                                    }
                                }
                            }
                        }

                        DataPacket pk = this.getPacket(packetId, pktProtocol);
                        if (pk != null) {
                            pk.protocol = pktProtocol;
                            pk.setBuffer(buf, dataOffset);
                            pk.decode();
                            packets.add(pk);
                        }
                    } else {
                        // 0.15.x: [packetID][data...]
                        if (buf.length < 1) {
                            continue;
                        }

                        // 对于 LoginPacket，检测协议版本
                        @SupportedProtocol int pktProtocol = protocol;
                        if ((buf[0] & 0xff) == (ProtocolInfo.LOGIN_PACKET & 0xff) && buf.length >= 5) {
                            int clientProtocol = Binary.readInt(Binary.subBytes(buf, 1, 4));
                            if (ProtocolInfo.isSupportedProtocol(clientProtocol)) {
                                pktProtocol = ProtocolInfo.getPacketPoolProtocol(clientProtocol);
                                if (player != null && player.protocol == Integer.MAX_VALUE) {
                                    player.protocol = clientProtocol;
                                }
                                protocol = pktProtocol;
                                if (Nukkit.DEBUG > 1) {
                                    this.server.getLogger().debug("LoginPacket detected: clientProtocol=" + clientProtocol + ", poolProtocol=" + pktProtocol + (player != null ? ", player=" + player.getAddress() : ""));
                                }
                            } else if (Nukkit.DEBUG > 1) {
                                this.server.getLogger().debug("LoginPacket with unsupported protocol " + clientProtocol + (player != null ? " from " + player.getAddress() : ""));
                            }
                        }

                        DataPacket pk = this.getPacket(buf[0] & 0xff, pktProtocol);
                        if (pk != null) {
                            pk.protocol = pktProtocol;
                            pk.setBuffer(buf, 1);
                            pk.decode();
                            packets.add(pk);
                        }
                    }
                }

                processPackets(player, packets);
            } catch (Exception e) {
                if (Nukkit.DEBUG > 0) {
                    this.server.getLogger().debug("BatchPacket 0x" + Binary.bytesToHexString(packet.payload));
                    this.server.getLogger().logException(e);
                }
            }
            return;
        }

        BinaryStream stream = new BinaryStream(data);
        try {
            List<DataPacket> packets = new ArrayList<>();
            while (stream.offset < len) {
                byte[] buf = stream.getByteArray();

                // 对于 LoginPacket，先检测协议版本
                @SupportedProtocol int pktProtocol = protocol;
                if ((buf[0] & 0xff) == (ProtocolInfo.LOGIN_PACKET & 0xff) && buf.length >= 5) {
                    // 读取 clientProtocol 字段（偏移量 1 开始的 int）
                    int clientProtocol = Binary.readInt(Binary.subBytes(buf, 1, 4));
                    if (ProtocolInfo.isSupportedProtocol(clientProtocol)) {
                        pktProtocol = ProtocolInfo.getPacketPoolProtocol(clientProtocol);
                        // 第一时间记录玩家协议版本
                        if (player != null && player.protocol == Integer.MAX_VALUE) {
                            player.protocol = clientProtocol;
                        }
                        protocol = pktProtocol;
                        if (Nukkit.DEBUG > 1) {
                            this.server.getLogger().debug("LoginPacket detected: clientProtocol=" + clientProtocol + ", poolProtocol=" + pktProtocol + (player != null ? ", player=" + player.getAddress() : ""));
                        }
                    } else if (Nukkit.DEBUG > 1) {
                        this.server.getLogger().debug("LoginPacket with unsupported protocol " + clientProtocol + (player != null ? " from " + player.getAddress() : ""));
                    }
                }

                DataPacket pk;
                if ((pk = this.getPacket(buf[0] & 0xff, pktProtocol)) != null) {
                    pk.protocol = pktProtocol;
                    pk.setBuffer(buf, 1);
                    pk.decode();
                    packets.add(pk);
                }
            }

            processPackets(player, packets);

        } catch (Exception e) {
            if (Nukkit.DEBUG > 0) {
                this.server.getLogger().debug("BatchPacket 0x" + Binary.bytesToHexString(packet.payload));
                this.server.getLogger().logException(e);
            }
        }
    }

    /**
     * Process packets obtained from batch packets
     * Required to perform additional analyses and filter unnecessary packets
     *
     * @param packets
     */
    public void processPackets(Player player, List<DataPacket> packets) {
        if (packets.isEmpty()) return;
        List<Byte> filter = new ArrayList<>();
        for (DataPacket packet : packets) {
            switch (packet.pid()) {
                case ProtocolInfo.USE_ITEM_PACKET:
                    // Prevent double fire of PlayerInteractEvent
                    if (!filter.contains(ProtocolInfo.USE_ITEM_PACKET)) {
                        player.handleDataPacket(packet);
                        filter.add(ProtocolInfo.USE_ITEM_PACKET);
                    }
                    break;
                default:
                    player.handleDataPacket(packet);
            }
        }
    }


    public DataPacket getPacket(byte id) {
        return this.getPacket(id & 0xff, ProtocolInfo.CURRENT_PROTOCOL);
    }

    /**
     * 当协议版本未知时，从批量包解压数据中预检测协议版本。
     * 先尝试 0.15.x+ 格式（VarInt 长度前缀），再尝试 0.14.x 格式（4字节 int 长度前缀）。
     */
    private @SupportedProtocol int detectProtocolFromBatchData(byte[] data, int len) {
        // 先尝试 0.16.0+ 格式：VarInt 长度 + [packetID, clientProtocol, ...]
        try {
            BinaryStream stream = new BinaryStream(data);
            byte[] buf = stream.getByteArray();
            if (buf.length >= 5 && (buf[0] & 0xff) == (ProtocolInfo.LOGIN_PACKET & 0xff)) {
                int clientProtocol = Binary.readInt(Binary.subBytes(buf, 1, 4));
                if (ProtocolInfo.isSupportedProtocol(clientProtocol)) {
                    return clientProtocol;
                }
            }
        } catch (Exception ignored) {
        }

        // 再尝试 0.15.x 格式：4字节 int 长度 + [packetID(0x01), clientProtocol, ...]
        if (len >= 9) {
            try {
                int pkLen = Binary.readInt(Binary.subBytes(data, 0, 4));
                if (pkLen > 0 && pkLen <= len - 4) {
                    byte[] buf = Binary.subBytes(data, 4, pkLen);
                    if (buf.length >= 5 && (buf[0] & 0xff) == (ProtocolInfo.LOGIN_PACKET & 0xff)) {
                        int clientProtocol = Binary.readInt(Binary.subBytes(buf, 1, 4));
                        if (ProtocolInfo.isSupportedProtocol(clientProtocol) && clientProtocol < ProtocolInfo.v0_16_0) {
                            return clientProtocol;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 最后尝试 0.13.x ~ 0.14.x 格式：4字节 int 长度 + [packetData...]
        // 0.13.x / 0.14.x LoginPacket 的翻译 ID 都是 0x8f
        if (len >= 8) {
            try {
                int pkLen = Binary.readInt(Binary.subBytes(data, 0, 4));
                if (pkLen > 0 && pkLen <= len - 4) {
                    byte[] buf = Binary.subBytes(data, 4, pkLen);
                    // 0.14.x: [0x8e][0x8f][username_short][username...][protocol_int]
                    if (buf.length >= 2 && (buf[0] & 0xff) == 0x8e && (buf[1] & 0xff) == 0x8f) {
                        if (buf.length >= 8) {
                            int usernameLength = Binary.readShort(Binary.subBytes(buf, 2, 2)) & 0xffff;
                            int protocolOffset = 4 + usernameLength;
                            if (protocolOffset + 4 <= buf.length) {
                                int clientProtocol = Binary.readInt(Binary.subBytes(buf, protocolOffset, 4));
                                if (ProtocolInfo.isSupportedProtocol(clientProtocol) && clientProtocol < ProtocolInfo.v0_15_0) {
                                    return clientProtocol;
                                }
                            }
                        }
                        return ProtocolInfo.v0_14_2;
                    }
                    // 0.13.x: [0x8f][username_short][username...][protocol_int]（无 0x8e 前缀）
                    if (buf.length >= 1 && (buf[0] & 0xff) == 0x8f) {
                        if (buf.length >= 7) {
                            int usernameLength = Binary.readShort(Binary.subBytes(buf, 1, 2)) & 0xffff;
                            int protocolOffset = 3 + usernameLength;
                            if (protocolOffset + 4 <= buf.length) {
                                int clientProtocol = Binary.readInt(Binary.subBytes(buf, protocolOffset, 4));
                                if (ProtocolInfo.isSupportedProtocol(clientProtocol) && clientProtocol < ProtocolInfo.v0_14_0) {
                                    return clientProtocol;
                                }
                            }
                        }
                        return ProtocolInfo.v0_13_2;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return ProtocolInfo.CURRENT_PROTOCOL;
    }

    public DataPacket getPacket(int id, @SupportedProtocol int protocol) {
        PacketPool pool = this.getPacketPool(protocol);
        return pool == null ? null : pool.getPacket(id);
    }

    public PacketPool getPacketPool(@SupportedProtocol int protocol) {
        switch (ProtocolInfo.getPacketPoolProtocol(protocol)) {
            case ProtocolInfo.v0_13_2:
                return this.packetPool39;
            case ProtocolInfo.v0_14_2:
                return this.packetPool60;
            case ProtocolInfo.v0_15_0:
                return this.packetPool81;
            case ProtocolInfo.v0_15_10:
                return this.packetPool84;
            case ProtocolInfo.v0_16_0:
                return this.packetPool91;
            case ProtocolInfo.v1_0_0_0:
                return this.packetPool92;
            case ProtocolInfo.v1_0_0:
                return this.packetPool100;
            case ProtocolInfo.v1_0_3:
                return this.packetPool101;
            case ProtocolInfo.v1_0_4:
                return this.packetPool102;
            case ProtocolInfo.v1_0_5:
                return this.packetPool105;
            case ProtocolInfo.v1_1_0:
            default:
                return this.packetPool113;
        }
    }

    public void setPacketPool(@SupportedProtocol int protocol, PacketPool packetPool) {
        switch (ProtocolInfo.getPacketPoolProtocol(protocol)) {
            case ProtocolInfo.v0_13_2:
                this.packetPool39 = packetPool;
                break;
            case ProtocolInfo.v0_14_2:
                this.packetPool60 = packetPool;
                break;
            case ProtocolInfo.v0_15_0:
                this.packetPool81 = packetPool;
                break;
            case ProtocolInfo.v0_15_10:
                this.packetPool84 = packetPool;
                break;
            case ProtocolInfo.v0_16_0:
                this.packetPool91 = packetPool;
                break;
            case ProtocolInfo.v1_0_0_0:
                this.packetPool92 = packetPool;
                break;
            case ProtocolInfo.v1_0_0:
                this.packetPool100 = packetPool;
                break;
            case ProtocolInfo.v1_0_3:
                this.packetPool101 = packetPool;
                break;
            case ProtocolInfo.v1_0_4:
                this.packetPool102 = packetPool;
                break;
            case ProtocolInfo.v1_0_5:
                this.packetPool105 = packetPool;
                break;
            case ProtocolInfo.v1_1_0:
            default:
                this.packetPool113 = packetPool;
                break;
        }
    }

    public void sendPacket(String address, int port, byte[] payload) {
        for (AdvancedSourceInterface interfaz : this.advancedInterfaces) {
            interfaz.sendRawPacket(address, port, payload);
        }
    }

    public void blockAddress(String address) {
        this.blockAddress(address, 300);
    }

    public void blockAddress(String address, int timeout) {
        for (AdvancedSourceInterface interfaz : this.advancedInterfaces) {
            interfaz.blockAddress(address, timeout);
        }
    }

    public void unblockAddress(String address) {
        for (AdvancedSourceInterface interfaz : this.advancedInterfaces) {
            interfaz.unblockAddress(address);
        }
    }

    private void registerPackets() {
        PacketPool.Builder pool60 = this.newBuilder(ProtocolInfo.v0_14_2);
        PacketPool.Builder pool81 = this.newBuilder(ProtocolInfo.v0_15_0);
        PacketPool.Builder pool84 = this.newBuilder(ProtocolInfo.v0_15_10);
        PacketPool.Builder pool91 = this.newBuilder(ProtocolInfo.v0_16_0);
        PacketPool.Builder pool92 = this.newBuilder(ProtocolInfo.v1_0_0_0);
        PacketPool.Builder pool100 = this.newBuilder(ProtocolInfo.v1_0_0);
        PacketPool.Builder pool101 = this.newBuilder(ProtocolInfo.v1_0_3);
        PacketPool.Builder pool102 = this.newBuilder(ProtocolInfo.v1_0_4);
        PacketPool.Builder pool105 = this.newBuilder(ProtocolInfo.v1_0_5);
        PacketPool.Builder pool113 = this.newBuilder(ProtocolInfo.v1_1_0);

        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_ENTITY_PACKET, AddEntityPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_HANGING_ENTITY_PACKET, AddHangingEntityPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_ITEM_ENTITY_PACKET, AddItemEntityPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_ITEM_PACKET, AddItemPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_PAINTING_PACKET, AddPaintingPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_PLAYER_PACKET, AddPlayerPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADVENTURE_SETTINGS_PACKET, AdventureSettingsPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ANIMATE_PACKET, AnimatePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.AVAILABLE_COMMANDS_PACKET, AvailableCommandsPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BATCH_PACKET, BatchPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BLOCK_ENTITY_DATA_PACKET, BlockEntityDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BLOCK_EVENT_PACKET, BlockEventPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BLOCK_PICK_REQUEST_PACKET, BlockPickRequestPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BOSS_EVENT_PACKET, BossEventPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CHANGE_DIMENSION_PACKET, ChangeDimensionPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CHUNK_RADIUS_UPDATED_PACKET, ChunkRadiusUpdatedPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CLIENTBOUND_MAP_ITEM_DATA_PACKET, ClientboundMapItemDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.COMMAND_STEP_PACKET, CommandStepPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_CLOSE_PACKET, ContainerClosePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_OPEN_PACKET, ContainerOpenPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_SET_CONTENT_PACKET, ContainerSetContentPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_SET_DATA_PACKET, ContainerSetDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_SET_SLOT_PACKET, ContainerSetSlotPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CRAFTING_DATA_PACKET, CraftingDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CRAFTING_EVENT_PACKET, CraftingEventPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.DISCONNECT_PACKET, DisconnectPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.DROP_ITEM_PACKET, DropItemPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ENTITY_EVENT_PACKET, EntityEventPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ENTITY_FALL_PACKET, EntityFallPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.EXPLODE_PACKET, ExplodePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.FULL_CHUNK_DATA_PACKET, FullChunkDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.GAME_RULES_CHANGED_PACKET, GameRulesChangedPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.HURT_ARMOR_PACKET, HurtArmorPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.INTERACT_PACKET, InteractPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.INVENTORY_ACTION_PACKET, InventoryActionPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET, ItemFrameDropItemPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.LEVEL_EVENT_PACKET, LevelEventPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.LEVEL_SOUND_EVENT_PACKET, LevelSoundEventPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.LOGIN_PACKET, LoginPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MAP_INFO_REQUEST_PACKET, MapInfoRequestPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET, MobArmorEquipmentPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOB_EQUIPMENT_PACKET, MobEquipmentPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOVE_ENTITY_PACKET, MoveEntityPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOVE_PLAYER_PACKET, MovePlayerPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAYER_ACTION_PACKET, PlayerActionPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAYER_INPUT_PACKET, PlayerInputPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAYER_LIST_PACKET, PlayerListPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAY_SOUND_PACKET, PlaySoundPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAY_STATUS_PACKET, PlayStatusPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REMOVE_BLOCK_PACKET, RemoveBlockPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REMOVE_ENTITY_PACKET, RemoveEntityPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REPLACE_ITEM_IN_SLOT_PACKET, ReplaceItemInSlotPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET, RequestChunkRadiusPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACKS_INFO_PACKET, ResourcePacksInfoPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_STACK_PACKET, ResourcePackStackPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ResourcePackClientResponsePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_DATA_INFO_PACKET, ResourcePackDataInfoPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_CHUNK_DATA_PACKET, ResourcePackChunkDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ResourcePackChunkRequestPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESPAWN_PACKET, RespawnPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RIDER_JUMP_PACKET, RiderJumpPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_COMMANDS_ENABLED_PACKET, SetCommandsEnabledPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_DIFFICULTY_PACKET, SetDifficultyPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_ENTITY_DATA_PACKET, SetEntityDataPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_ENTITY_LINK_PACKET, SetEntityLinkPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_ENTITY_MOTION_PACKET, SetEntityMotionPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_HEALTH_PACKET, SetHealthPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET, SetPlayerGameTypePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_SPAWN_POSITION_PACKET, SetSpawnPositionPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_TITLE_PACKET, SetTitlePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_TIME_PACKET, SetTimePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SHOW_CREDITS_PACKET, ShowCreditsPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SPAWN_EXPERIENCE_ORB_PACKET, SpawnExperienceOrbPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.START_GAME_PACKET, StartGamePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.TAKE_ITEM_ENTITY_PACKET, TakeItemEntityPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.TEXT_PACKET, TextPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.UPDATE_BLOCK_PACKET, UpdateBlockPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.USE_ITEM_PACKET, UseItemPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.UPDATE_TRADE_PACKET, UpdateTradePacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOB_EFFECT_PACKET, MobEffectPacket.class);
        this.registerPacketAcrossPools(pool60, pool81, pool84, pool91, pool92, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.UPDATE_ATTRIBUTES_PACKET, UpdateAttributesPacket.class);
        pool60.registerPacket(0x97, RemovePlayerPacket.class);

        this.packetPool60 = pool60.build();
        this.packetPool39 = this.packetPool60.toBuilder()
                .protocolVersion(ProtocolInfo.v0_13_2)
                .minecraftVersion(ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_13_2))
                .deregisterPacket(0xc6)
                .deregisterPacket(0xc7)
                .deregisterPacket(0xc8)
                .deregisterPacket(0xc9)
                .deregisterPacket(0xca)
                .deregisterPacket(0xcb)
                .build();
        this.packetPool81 = pool81.build();
        this.packetPool84 = pool84.build();
        this.packetPool91 = pool91.build();
        this.packetPool92 = pool92.build();
        this.packetPool100 = pool100.build();
        this.packetPool101 = pool101.build();
        this.packetPool102 = pool102.build();
        this.packetPool105 = pool105.build();
        this.packetPool113 = pool113.build();
    }

    private PacketPool.Builder newBuilder(@SupportedProtocol int protocol) {
        return PacketPool.builder()
                .protocolVersion(protocol)
                .minecraftVersion(ProtocolInfo.getMinecraftVersion(protocol));
    }

    private <T extends DataPacket> void registerPacketAcrossPools(PacketPool.Builder pool60,
                                                                  PacketPool.Builder pool81,
                                                                  PacketPool.Builder pool84,
                                                                  PacketPool.Builder pool91,
                                                                  PacketPool.Builder pool92,
                                                                  PacketPool.Builder pool100,
                                                                  PacketPool.Builder pool101,
                                                                  PacketPool.Builder pool102,
                                                                  PacketPool.Builder pool105,
                                                                  PacketPool.Builder pool113,
                                                                  byte currentId,
                                                                  Class<T> clazz) {
        this.registerTranslatedPacket(pool60, ProtocolInfo.v0_14_2, currentId, clazz);
        this.registerTranslatedPacket(pool81, ProtocolInfo.v0_15_0, currentId, clazz);
        this.registerTranslatedPacket(pool84, ProtocolInfo.v0_15_10, currentId, clazz);
        this.registerTranslatedPacket(pool91, ProtocolInfo.v0_16_0, currentId, clazz);
        this.registerTranslatedPacket(pool92, ProtocolInfo.v1_0_0_0, currentId, clazz);
        this.registerTranslatedPacket(pool100, ProtocolInfo.v1_0_0, currentId, clazz);
        this.registerTranslatedPacket(pool101, ProtocolInfo.v1_0_3, currentId, clazz);
        this.registerTranslatedPacket(pool102, ProtocolInfo.v1_0_4, currentId, clazz);
        this.registerTranslatedPacket(pool105, ProtocolInfo.v1_0_5, currentId, clazz);
        this.registerTranslatedPacket(pool113, ProtocolInfo.v1_1_0, currentId, clazz);
    }

    private <T extends DataPacket> void registerTranslatedPacket(PacketPool.Builder builder, @SupportedProtocol int protocol, byte currentId, Class<T> clazz) {
        int translatedId = this.translatePacketId(protocol, currentId & 0xff);
        if (translatedId >= 0) {
            builder.registerPacket(translatedId, clazz);
        }
    }

    private int translatePacketId(@SupportedProtocol int protocol, int currentId) {
        if (protocol == ProtocolInfo.v1_1_0) {
            return currentId;
        }
        if (ProtocolInfo.getPacketPoolProtocol(protocol) == ProtocolInfo.v0_14_2) {
            return this.translatePacketIdFrom014(currentId);
        }
        if (currentId < 0x06) {
            return currentId;
        }
        if (currentId == (ProtocolInfo.BATCH_PACKET & 0xff)) {
            return 0x06;
        }

        switch (protocol) {
            case ProtocolInfo.v0_15_0:
            case ProtocolInfo.v0_15_10:
                return this.translatePacketIdFrom016(this.translatePacketId(ProtocolInfo.v0_16_0, currentId));
            case ProtocolInfo.v1_0_5:
                if (currentId >= 0x06 && currentId <= 0x3f) {
                    return currentId + 1;
                }
                if (currentId == 0x40 || currentId == 0x52) {
                    return -1;
                }
                if (currentId >= 0x41 && currentId <= 0x51) {
                    return currentId;
                }
                if (currentId >= 0x53 && currentId <= 0x59) {
                    return currentId - 1;
                }
                return -1;
            case ProtocolInfo.v1_0_4:
                if (currentId >= 0x06 && currentId <= 0x21) {
                    return currentId + 1;
                }
                if (currentId == 0x22 || currentId == 0x40 || currentId == 0x4d || currentId == 0x50 || currentId == 0x52) {
                    return -1;
                }
                if (currentId >= 0x23 && currentId <= 0x3f) {
                    return currentId;
                }
                if (currentId >= 0x41 && currentId <= 0x4c) {
                    return currentId - 1;
                }
                if (currentId >= 0x4e && currentId <= 0x4f) {
                    return currentId - 1;
                }
                if (currentId == 0x51) {
                    return currentId - 2;
                }
                if (currentId >= 0x53 && currentId <= 0x56) {
                    return currentId - 3;
                }
                return -1;
            case ProtocolInfo.v1_0_3:
                if (currentId >= 0x06 && currentId <= 0x21) {
                    return currentId + 1;
                }
                if (currentId == 0x22 || currentId == 0x40 || currentId == 0x4d || currentId == 0x50 || currentId == 0x51 || currentId == 0x52) {
                    return -1;
                }
                if (currentId >= 0x23 && currentId <= 0x3f) {
                    return currentId;
                }
                if (currentId >= 0x41 && currentId <= 0x4c) {
                    return currentId - 1;
                }
                if (currentId >= 0x4e && currentId <= 0x4f) {
                    return currentId - 1;
                }
                if (currentId >= 0x53 && currentId <= 0x56) {
                    return currentId - 4;
                }
                return -1;
            case ProtocolInfo.v1_0_0:
                if (currentId >= 0x06 && currentId <= 0x21) {
                    return currentId + 1;
                }
                if (currentId == 0x22 || currentId == 0x40 || currentId == 0x4d || currentId == 0x50
                        || currentId == 0x51 || currentId == 0x52 || currentId == 0x56) {
                    return -1;
                }
                if (currentId >= 0x23 && currentId <= 0x3f) {
                    return currentId;
                }
                if (currentId >= 0x41 && currentId <= 0x4c) {
                    return currentId - 1;
                }
                if (currentId >= 0x4e && currentId <= 0x4f) {
                    return currentId - 1;
                }
                if (currentId >= 0x53 && currentId <= 0x55) {
                    return currentId - 4;
                }
                return -1;
            case ProtocolInfo.v0_16_0:
                if (currentId >= 0x06 && currentId <= 0x21) {
                    return currentId + 1;
                }
                if (currentId == 0x22 || currentId == 0x25 || currentId == 0x40 || currentId == 0x4d
                        || currentId == 0x50 || currentId == 0x51 || currentId == 0x52) {
                    return -1;
                }
                if (currentId >= 0x23 && currentId <= 0x24) {
                    return currentId;
                }
                if (currentId >= 0x26 && currentId <= 0x3f) {
                    return currentId - 1;
                }
                if (currentId >= 0x41 && currentId <= 0x4c) {
                    return currentId - 2;
                }
                if (currentId >= 0x4e && currentId <= 0x4f) {
                    return currentId - 3;
                }
                if (currentId >= 0x53 && currentId <= 0x55) {
                    return currentId - 6;
                }
                return -1;
            case ProtocolInfo.v1_0_0_0:
                return this.translatePacketIdFrom1000(this.translatePacketId(ProtocolInfo.v1_0_0, currentId));
            default:
                return currentId;
        }
    }

    @ApiStatus.AvailableSince("1.0.0.0")
    @SinceProtocol(ProtocolInfo.v1_0_0_0)
    @UnsupportedSince(ProtocolInfo.v1_0_0)
    private int translatePacketIdFrom1000(int currentId) {
        if (currentId < 0) {
            return -1;
        }
        if (currentId <= 0x24) {
            return currentId;
        }
        if (currentId == 0x25) {
            return -1;
        }
        if (currentId >= 0x26 && currentId <= 0x51) {
            return currentId - 1;
        }
        return -1;
    }

    private int translatePacketIdFrom016(int currentId) {
        if (currentId < 0) {
            return -1;
        }
        if (currentId <= 0x06) {
            return currentId;
        }
        if (currentId >= 0x07 && currentId <= 0x09) {
            return -1;
        }
        if (currentId >= 0x0a && currentId <= 0x10) {
            return currentId - 3;
        }
        if (currentId == 0x11) {
            return -1;
        }
        if (currentId >= 0x12 && currentId <= 0x19) {
            return currentId - 4;
        }
        if (currentId == 0x1a) {
            return -1;
        }
        if (currentId >= 0x1b && currentId <= 0x21) {
            return currentId - 5;
        }
        if (currentId >= 0x22 && currentId <= 0x2d) {
            return currentId - 4;
        }
        if (currentId == 0x2e) {
            return -1;
        }
        if (currentId >= 0x2f && currentId <= 0x39) {
            return currentId - 5;
        }
        if (currentId >= 0x3b && currentId <= 0x3e) {
            return currentId - 6;
        }
        if (currentId >= 0x40 && currentId <= 0x46) {
            return currentId - 6;
        }
        if (currentId == 0x49) {
            return currentId - 8;
        }
        return -1;
    }

    @ApiStatus.AvailableSince("0.14.0")
    @SinceProtocol(ProtocolInfo.v0_14_0)
    @UnsupportedSince(ProtocolInfo.v0_15_0)
    private int translatePacketIdFrom014(int currentId) {
        switch (currentId) {
            case ProtocolInfo.LOGIN_PACKET & 0xff:
                return 0x8f;
            case ProtocolInfo.PLAY_STATUS_PACKET & 0xff:
                return 0x90;
            case ProtocolInfo.DISCONNECT_PACKET & 0xff:
                return 0x91;
            case ProtocolInfo.BATCH_PACKET & 0xff:
                return 0x92;
            case ProtocolInfo.TEXT_PACKET & 0xff:
                return 0x93;
            case ProtocolInfo.SET_TIME_PACKET & 0xff:
                return 0x94;
            case ProtocolInfo.START_GAME_PACKET & 0xff:
                return 0x95;
            case ProtocolInfo.ADD_PLAYER_PACKET & 0xff:
                return 0x96;
            case ProtocolInfo.ADD_ENTITY_PACKET & 0xff:
                return 0x98;
            case ProtocolInfo.REMOVE_ENTITY_PACKET & 0xff:
                return 0x99;
            case ProtocolInfo.ADD_ITEM_ENTITY_PACKET & 0xff:
                return 0x9a;
            case ProtocolInfo.TAKE_ITEM_ENTITY_PACKET & 0xff:
                return 0x9b;
            case ProtocolInfo.MOVE_ENTITY_PACKET & 0xff:
                return 0x9c;
            case ProtocolInfo.MOVE_PLAYER_PACKET & 0xff:
                return 0x9d;
            case ProtocolInfo.REMOVE_BLOCK_PACKET & 0xff:
                return 0x9e;
            case ProtocolInfo.UPDATE_BLOCK_PACKET & 0xff:
                return 0x9f;
            case ProtocolInfo.ADD_PAINTING_PACKET & 0xff:
                return 0xa0;
            case ProtocolInfo.EXPLODE_PACKET & 0xff:
                return 0xa1;
            case ProtocolInfo.LEVEL_EVENT_PACKET & 0xff:
                return 0xa2;
            case ProtocolInfo.BLOCK_EVENT_PACKET & 0xff:
                return 0xa3;
            case ProtocolInfo.ENTITY_EVENT_PACKET & 0xff:
                return 0xa4;
            case ProtocolInfo.MOB_EFFECT_PACKET & 0xff:
                return 0xa5;
            case ProtocolInfo.UPDATE_ATTRIBUTES_PACKET & 0xff:
                return 0xa6;
            case ProtocolInfo.MOB_EQUIPMENT_PACKET & 0xff:
                return 0xa7;
            case ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET & 0xff:
                return 0xa8;
            case ProtocolInfo.INTERACT_PACKET & 0xff:
                return 0xa9;
            case ProtocolInfo.USE_ITEM_PACKET & 0xff:
                return 0xaa;
            case ProtocolInfo.PLAYER_ACTION_PACKET & 0xff:
                return 0xab;
            case ProtocolInfo.HURT_ARMOR_PACKET & 0xff:
                return 0xac;
            case ProtocolInfo.SET_ENTITY_DATA_PACKET & 0xff:
                return 0xad;
            case ProtocolInfo.SET_ENTITY_MOTION_PACKET & 0xff:
                return 0xae;
            case ProtocolInfo.SET_ENTITY_LINK_PACKET & 0xff:
                return 0xaf;
            case ProtocolInfo.SET_HEALTH_PACKET & 0xff:
                return 0xb0;
            case ProtocolInfo.SET_SPAWN_POSITION_PACKET & 0xff:
                return 0xb1;
            case ProtocolInfo.ANIMATE_PACKET & 0xff:
                return 0xb2;
            case ProtocolInfo.RESPAWN_PACKET & 0xff:
                return 0xb3;
            case ProtocolInfo.DROP_ITEM_PACKET & 0xff:
                return 0xb4;
            case ProtocolInfo.CONTAINER_OPEN_PACKET & 0xff:
                return 0xb5;
            case ProtocolInfo.CONTAINER_CLOSE_PACKET & 0xff:
                return 0xb6;
            case ProtocolInfo.CONTAINER_SET_SLOT_PACKET & 0xff:
                return 0xb7;
            case ProtocolInfo.CONTAINER_SET_DATA_PACKET & 0xff:
                return 0xb8;
            case ProtocolInfo.CONTAINER_SET_CONTENT_PACKET & 0xff:
                return 0xb9;
            case ProtocolInfo.CRAFTING_DATA_PACKET & 0xff:
                return 0xba;
            case ProtocolInfo.CRAFTING_EVENT_PACKET & 0xff:
                return 0xbb;
            case ProtocolInfo.ADVENTURE_SETTINGS_PACKET & 0xff:
                return 0xbc;
            case ProtocolInfo.BLOCK_ENTITY_DATA_PACKET & 0xff:
                return 0xbd;
            case ProtocolInfo.PLAYER_INPUT_PACKET & 0xff:
                return 0xbe;
            case ProtocolInfo.FULL_CHUNK_DATA_PACKET & 0xff:
                return 0xbf;
            case ProtocolInfo.SET_DIFFICULTY_PACKET & 0xff:
                return 0xc0;
            case ProtocolInfo.CHANGE_DIMENSION_PACKET & 0xff:
                return 0xc1;
            case ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET & 0xff:
                return 0xc2;
            case ProtocolInfo.PLAYER_LIST_PACKET & 0xff:
                return 0xc3;
            case ProtocolInfo.SPAWN_EXPERIENCE_ORB_PACKET & 0xff:
                return 0xc5;
            case ProtocolInfo.CLIENTBOUND_MAP_ITEM_DATA_PACKET & 0xff:
                return 0xc6;
            case ProtocolInfo.MAP_INFO_REQUEST_PACKET & 0xff:
                return 0xc7;
            case ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET & 0xff:
                return 0xc8;
            case ProtocolInfo.CHUNK_RADIUS_UPDATED_PACKET & 0xff:
                return 0xc9;
            case ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET & 0xff:
                return 0xca;
            case ProtocolInfo.REPLACE_ITEM_IN_SLOT_PACKET & 0xff:
                return 0xcb;
            default:
                return -1;
        }
    }
}
