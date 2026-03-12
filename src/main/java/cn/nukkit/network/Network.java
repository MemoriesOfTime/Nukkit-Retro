package cn.nukkit.network;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;

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

    private PacketPool packetPool81;
    private PacketPool packetPool84;
    private PacketPool packetPool91;
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

    public void registerPacket(int protocol, int id, Class<? extends DataPacket> clazz) {
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
            return;
        }

        int len = data.length;
        BinaryStream stream = new BinaryStream(data);
        try {
            List<DataPacket> packets = new ArrayList<>();
            while (stream.offset < len) {
                byte[] buf = stream.getByteArray();

                DataPacket pk;
                int protocol = player == null ? ProtocolInfo.CURRENT_PROTOCOL : player.protocol;

                if ((pk = this.getPacket(buf[0] & 0xff, protocol)) != null) {
                    pk.protocol = protocol;
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

    public DataPacket getPacket(int id, int protocol) {
        PacketPool pool = this.getPacketPool(protocol);
        return pool == null ? null : pool.getPacket(id);
    }

    public PacketPool getPacketPool(int protocol) {
        switch (ProtocolInfo.getPacketPoolProtocol(protocol)) {
            case ProtocolInfo.v0_15_0:
                return this.packetPool81;
            case ProtocolInfo.v0_15_10:
                return this.packetPool84;
            case ProtocolInfo.v0_16_0:
                return this.packetPool91;
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

    public void setPacketPool(int protocol, PacketPool packetPool) {
        switch (ProtocolInfo.getPacketPoolProtocol(protocol)) {
            case ProtocolInfo.v0_15_0:
                this.packetPool81 = packetPool;
                break;
            case ProtocolInfo.v0_15_10:
                this.packetPool84 = packetPool;
                break;
            case ProtocolInfo.v0_16_0:
                this.packetPool91 = packetPool;
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
        PacketPool.Builder pool81 = this.newBuilder(ProtocolInfo.v0_15_0);
        PacketPool.Builder pool84 = this.newBuilder(ProtocolInfo.v0_15_10);
        PacketPool.Builder pool91 = this.newBuilder(ProtocolInfo.v0_16_0);
        PacketPool.Builder pool100 = this.newBuilder(ProtocolInfo.v1_0_0);
        PacketPool.Builder pool101 = this.newBuilder(ProtocolInfo.v1_0_3);
        PacketPool.Builder pool102 = this.newBuilder(ProtocolInfo.v1_0_4);
        PacketPool.Builder pool105 = this.newBuilder(ProtocolInfo.v1_0_5);
        PacketPool.Builder pool113 = this.newBuilder(ProtocolInfo.v1_1_0);

        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_ENTITY_PACKET, AddEntityPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_HANGING_ENTITY_PACKET, AddHangingEntityPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_ITEM_ENTITY_PACKET, AddItemEntityPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_ITEM_PACKET, AddItemPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_PAINTING_PACKET, AddPaintingPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADD_PLAYER_PACKET, AddPlayerPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ADVENTURE_SETTINGS_PACKET, AdventureSettingsPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ANIMATE_PACKET, AnimatePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.AVAILABLE_COMMANDS_PACKET, AvailableCommandsPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BATCH_PACKET, BatchPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BLOCK_ENTITY_DATA_PACKET, BlockEntityDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BLOCK_EVENT_PACKET, BlockEventPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BLOCK_PICK_REQUEST_PACKET, BlockPickRequestPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.BOSS_EVENT_PACKET, BossEventPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CHANGE_DIMENSION_PACKET, ChangeDimensionPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CHUNK_RADIUS_UPDATED_PACKET, ChunkRadiusUpdatedPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CLIENTBOUND_MAP_ITEM_DATA_PACKET, ClientboundMapItemDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.COMMAND_STEP_PACKET, CommandStepPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_CLOSE_PACKET, ContainerClosePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_OPEN_PACKET, ContainerOpenPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_SET_CONTENT_PACKET, ContainerSetContentPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_SET_DATA_PACKET, ContainerSetDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CONTAINER_SET_SLOT_PACKET, ContainerSetSlotPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CRAFTING_DATA_PACKET, CraftingDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.CRAFTING_EVENT_PACKET, CraftingEventPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.DISCONNECT_PACKET, DisconnectPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.DROP_ITEM_PACKET, DropItemPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ENTITY_EVENT_PACKET, EntityEventPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ENTITY_FALL_PACKET, EntityFallPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.EXPLODE_PACKET, ExplodePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.FULL_CHUNK_DATA_PACKET, FullChunkDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.GAME_RULES_CHANGED_PACKET, GameRulesChangedPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.HURT_ARMOR_PACKET, HurtArmorPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.INTERACT_PACKET, InteractPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.INVENTORY_ACTION_PACKET, InventoryActionPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET, ItemFrameDropItemPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.LEVEL_EVENT_PACKET, LevelEventPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.LEVEL_SOUND_EVENT_PACKET, LevelSoundEventPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.LOGIN_PACKET, LoginPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MAP_INFO_REQUEST_PACKET, MapInfoRequestPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET, MobArmorEquipmentPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOB_EQUIPMENT_PACKET, MobEquipmentPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOVE_ENTITY_PACKET, MoveEntityPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.MOVE_PLAYER_PACKET, MovePlayerPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAYER_ACTION_PACKET, PlayerActionPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAYER_INPUT_PACKET, PlayerInputPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAYER_LIST_PACKET, PlayerListPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAY_SOUND_PACKET, PlaySoundPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.PLAY_STATUS_PACKET, PlayStatusPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REMOVE_BLOCK_PACKET, RemoveBlockPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REMOVE_ENTITY_PACKET, RemoveEntityPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REPLACE_ITEM_IN_SLOT_PACKET, ReplaceItemInSlotPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET, RequestChunkRadiusPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACKS_INFO_PACKET, ResourcePacksInfoPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_STACK_PACKET, ResourcePackStackPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ResourcePackClientResponsePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_DATA_INFO_PACKET, ResourcePackDataInfoPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_CHUNK_DATA_PACKET, ResourcePackChunkDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ResourcePackChunkRequestPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RESPAWN_PACKET, RespawnPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.RIDER_JUMP_PACKET, RiderJumpPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_COMMANDS_ENABLED_PACKET, SetCommandsEnabledPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_DIFFICULTY_PACKET, SetDifficultyPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_ENTITY_DATA_PACKET, SetEntityDataPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_ENTITY_LINK_PACKET, SetEntityLinkPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_ENTITY_MOTION_PACKET, SetEntityMotionPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_HEALTH_PACKET, SetHealthPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET, SetPlayerGameTypePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_SPAWN_POSITION_PACKET, SetSpawnPositionPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_TITLE_PACKET, SetTitlePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SET_TIME_PACKET, SetTimePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SHOW_CREDITS_PACKET, ShowCreditsPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.SPAWN_EXPERIENCE_ORB_PACKET, SpawnExperienceOrbPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.START_GAME_PACKET, StartGamePacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.TAKE_ITEM_ENTITY_PACKET, TakeItemEntityPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.TEXT_PACKET, TextPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.UPDATE_BLOCK_PACKET, UpdateBlockPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.USE_ITEM_PACKET, UseItemPacket.class);
        this.registerPacketAcrossPools(pool81, pool84, pool91, pool100, pool101, pool102, pool105, pool113, ProtocolInfo.UPDATE_TRADE_PACKET, UpdateTradePacket.class);

        this.packetPool81 = pool81.build();
        this.packetPool84 = pool84.build();
        this.packetPool91 = pool91.build();
        this.packetPool100 = pool100.build();
        this.packetPool101 = pool101.build();
        this.packetPool102 = pool102.build();
        this.packetPool105 = pool105.build();
        this.packetPool113 = pool113.build();
    }

    private PacketPool.Builder newBuilder(int protocol) {
        return PacketPool.builder()
                .protocolVersion(protocol)
                .minecraftVersion(ProtocolInfo.getMinecraftVersion(protocol));
    }

    private <T extends DataPacket> void registerPacketAcrossPools(PacketPool.Builder pool81,
                                                                  PacketPool.Builder pool84,
                                                                  PacketPool.Builder pool91,
                                                                  PacketPool.Builder pool100,
                                                                  PacketPool.Builder pool101,
                                                                  PacketPool.Builder pool102,
                                                                  PacketPool.Builder pool105,
                                                                  PacketPool.Builder pool113,
                                                                  byte currentId,
                                                                  Class<T> clazz) {
        this.registerTranslatedPacket(pool81, ProtocolInfo.v0_15_0, currentId, clazz);
        this.registerTranslatedPacket(pool84, ProtocolInfo.v0_15_10, currentId, clazz);
        this.registerTranslatedPacket(pool91, ProtocolInfo.v0_16_0, currentId, clazz);
        this.registerTranslatedPacket(pool100, ProtocolInfo.v1_0_0, currentId, clazz);
        this.registerTranslatedPacket(pool101, ProtocolInfo.v1_0_3, currentId, clazz);
        this.registerTranslatedPacket(pool102, ProtocolInfo.v1_0_4, currentId, clazz);
        this.registerTranslatedPacket(pool105, ProtocolInfo.v1_0_5, currentId, clazz);
        this.registerTranslatedPacket(pool113, ProtocolInfo.v1_1_0, currentId, clazz);
    }

    private <T extends DataPacket> void registerTranslatedPacket(PacketPool.Builder builder, int protocol, byte currentId, Class<T> clazz) {
        int translatedId = this.translatePacketId(protocol, currentId & 0xff);
        if (translatedId >= 0) {
            builder.registerPacket(translatedId, clazz);
        }
    }

    private int translatePacketId(int protocol, int currentId) {
        if (protocol == ProtocolInfo.v1_1_0) {
            return currentId;
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
            default:
                return currentId;
        }
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
}
