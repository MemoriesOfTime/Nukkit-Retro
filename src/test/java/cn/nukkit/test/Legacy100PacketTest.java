package cn.nukkit.test;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.*;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.RuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Legacy 1.0.0 Packet")
class Legacy100PacketTest {

    private static TestPlayer newTestPlayer(int protocol) throws Exception {
        Unsafe unsafe = getUnsafe();
        TestPlayer player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        cn.nukkit.level.Level level = newTestLevel();

        setField(Player.class, player, "connected", true);
        setField(Player.class, player, "protocol", protocol);
        setField(cn.nukkit.level.Position.class, player, "level", level);
        setField(Player.class, player, "usedChunks", new java.util.HashMap<Long, Boolean>());
        setField(Entity.class, player, "server", newTestServer());

        return player;
    }

    private static Server newTestServer() throws Exception {
        Unsafe unsafe = getUnsafe();
        Server server = (Server) unsafe.allocateInstance(Server.class);
        MainLogger logger = MainLogger.getLogger();
        if (logger == null) {
            logger = new MainLogger("/tmp/nukkit-retro-legacy100-test.log");
        }
        setField(Server.class, null, "instance", server);
        setField(Server.class, server, "config", new Config(Config.YAML));
        setField(Server.class, server, "logger", logger);
        setField(Server.class, server, "pluginManager", new PluginManager(server, null));
        return server;
    }

    private static cn.nukkit.level.Level newTestLevel() throws Exception {
        Unsafe unsafe = getUnsafe();
        cn.nukkit.level.Level level = (cn.nukkit.level.Level) unsafe.allocateInstance(cn.nukkit.level.Level.class);
        setField(cn.nukkit.level.Level.class, level, "updateEntities", new java.util.HashMap<Long, Entity>());
        return level;
    }

    private static Unsafe getUnsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("1.0.0.0 应使用 protocol 92 的初始包 ID")
    void protocol92ShouldUseInitial100PacketIds() {
        Network network = new Network(null);

        assertAll(
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v1_0_0_0)),
                () -> assertEquals(0x01, network.getPacketPool(ProtocolInfo.v1_0_0_0).getPacketId(LoginPacket.class)),
                () -> assertEquals(0x06, network.getPacketPool(ProtocolInfo.v1_0_0_0).getPacketId(BatchPacket.class)),
                () -> assertEquals(0x0c, network.getPacketPool(ProtocolInfo.v1_0_0_0).getPacketId(StartGamePacket.class)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> network.getPacketPool(ProtocolInfo.v1_0_0_0).getPacketId(EntityFallPacket.class))
        );
    }

    @Test
    @DisplayName("UseItem 应兼容 1.0.0.0 未包含 blockId 的布局")
    void useItemShouldDecodeInitial100LayoutWithoutBlockId() {
        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0);
        input.putBlockCoords(1, 64, -2);
        input.putVarInt(3);
        input.putVector3f(0.1f, 0.2f, 0.3f);
        input.putVector3f(10.0f, 65.0f, -1.5f);
        input.putByte((byte) 5);
        input.putSlot(Item.get(Item.STONE, 0, 1));

        UseItemPacket packet = new UseItemPacket();
        packet.protocol = ProtocolInfo.v1_0_0_0;
        packet.setBuffer(input.getBuffer(), 1);
        packet.decode();

        assertAll(
                () -> assertEquals(1, packet.x),
                () -> assertEquals(64, packet.y),
                () -> assertEquals(-2, packet.z),
                () -> assertEquals(0, packet.interactBlockId),
                () -> assertEquals(3, packet.face),
                () -> assertEquals(5, packet.slot),
                () -> assertEquals(Item.STONE, packet.item.getId()),
                () -> assertEquals(1, packet.item.getCount())
        );
    }

    @Test
    @DisplayName("PlayerList 应在 1.1.0 使用结构化皮肤布局")
    void playerListShouldUseStructuredSkinLayoutOn110() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 9);

        PlayerListPacket packet = new PlayerListPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.type = PlayerListPacket.TYPE_ADD;
        packet.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(uuid, 321L, "Modern110", new Skin(skinData, Skin.MODEL_ALEX))
        };
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(PlayerListPacket.TYPE_ADD);
        expected.putUnsignedVarInt(1);
        expected.putUUID(uuid);
        expected.putVarLong(321L);
        expected.putString("Modern110");
        expected.putString(Skin.MODEL_ALEX);
        expected.putByteArray(skinData);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("1.1.0 不应注册旧版 RemovePlayerPacket")
    void removePlayerPacketShouldNotBePresentOn110() {
        Network network = new Network(null);

        assertThrows(IllegalArgumentException.class,
                () -> network.getPacketPool(ProtocolInfo.v1_1_0).getPacketId(RemovePlayerPacket.class));
    }

    @Test
    @DisplayName("1.1.0 与 1.1.3 应共享同一协议族包池")
    void protocol110ShouldSharePacketPoolWith113Family() {
        Network network = new Network(null);

        assertAll(
                () -> assertEquals(ProtocolInfo.v1_1_3, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v1_1_0)),
                () -> assertEquals(
                        network.getPacketPool(ProtocolInfo.v1_1_3).getPacketId(LoginPacket.class),
                        network.getPacketPool(ProtocolInfo.v1_1_0).getPacketId(LoginPacket.class)
                ),
                () -> assertEquals(
                        network.getPacketPool(ProtocolInfo.v1_1_3).getPacketId(StartGamePacket.class),
                        network.getPacketPool(ProtocolInfo.v1_1_0).getPacketId(StartGamePacket.class)
                ),
                () -> assertEquals(
                        network.getPacketPool(ProtocolInfo.v1_1_3).getPacketId(BatchPacket.class),
                        network.getPacketPool(ProtocolInfo.v1_1_0).getPacketId(BatchPacket.class)
                ),
                () -> assertEquals(
                        network.getPacketPool(ProtocolInfo.v1_1_3).getPacketId(LoginPacket.class),
                        network.getPacketPool(ProtocolInfo.v1_1_1).getPacketId(LoginPacket.class)
                ),
                () -> assertEquals(
                        network.getPacketPool(ProtocolInfo.v1_1_3).getPacketId(LoginPacket.class),
                        network.getPacketPool(ProtocolInfo.v1_1_2).getPacketId(LoginPacket.class)
                )
        );
    }

    @Test
    @DisplayName("向 1.1.0 注册数据包时应直接复用 1.1.3 协议池")
    void registerPacketOn110ShouldReuse113Pool() {
        Network network = new Network(null);

        network.registerPacket(ProtocolInfo.v1_1_0, 0x7a, SharedPoolProbePacket.class);

        assertAll(
                () -> assertSame(network.getPacketPool(ProtocolInfo.v1_1_0), network.getPacketPool(ProtocolInfo.v1_1_3)),
                () -> assertSame(network.getPacketPool(ProtocolInfo.v1_1_1), network.getPacketPool(ProtocolInfo.v1_1_3)),
                () -> assertSame(network.getPacketPool(ProtocolInfo.v1_1_2), network.getPacketPool(ProtocolInfo.v1_1_3)),
                () -> assertEquals(0x7a, network.getPacketPool(ProtocolInfo.v1_1_0).getPacketId(SharedPoolProbePacket.class)),
                () -> assertEquals(0x7a, network.getPacketPool(ProtocolInfo.v1_1_1).getPacketId(SharedPoolProbePacket.class)),
                () -> assertEquals(0x7a, network.getPacketPool(ProtocolInfo.v1_1_2).getPacketId(SharedPoolProbePacket.class)),
                () -> assertEquals(0x7a, network.getPacketPool(ProtocolInfo.v1_1_3).getPacketId(SharedPoolProbePacket.class))
        );
    }

    @Test
    @DisplayName("LoginPacket 应按 1.1.0 原始字符串布局解码")
    void loginPacketShouldDecode110RawChainPayload() {
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 7);

        BinaryStream payload = new BinaryStream();
        byte[] chainBytes = "{\"chain\":[]}".getBytes(StandardCharsets.UTF_8);
        payload.putLInt(chainBytes.length);
        payload.put(chainBytes);

        String clientDataJson = "{\"ClientRandomId\":123,\"ServerAddress\":\"127.0.0.1:19132\",\"SkinId\":\""
                + Skin.MODEL_ALEX + "\",\"SkinData\":\""
                + Base64.getEncoder().encodeToString(skinData) + "\"}";
        String token = "header." + Base64.getEncoder().encodeToString(clientDataJson.getBytes(StandardCharsets.UTF_8));
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        payload.putLInt(tokenBytes.length);
        payload.put(tokenBytes);

        BinaryStream input = new BinaryStream();
        input.putInt(ProtocolInfo.v1_1_0);
        input.putByte((byte) 0);
        input.putByteArray(payload.getBuffer());

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals(ProtocolInfo.v1_1_0, packet.getProtocol()),
                () -> assertEquals(123L, packet.clientId),
                () -> assertNotNull(packet.getSkin()),
                () -> assertEquals(Skin.MODEL_ALEX, packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("ResourcePacksInfo 应匹配 GenisysPro 的 1.1 布局")
    void resourcePacksInfoShouldMatchGenisysPro110Layout() {
        TestResourcePack pack = new TestResourcePack("test-pack", "1.0.0", 321, new byte[]{1, 2, 3});

        ResourcePacksInfoPacket packet = new ResourcePacksInfoPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.mustAccept = true;
        packet.resourcePackEntries = new ResourcePack[]{pack};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putBoolean(true);
        expected.putLShort(0);
        expected.putLShort(1);
        expected.putString("test-pack");
        expected.putString("1.0.0");
        expected.putLLong(321);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("ResourcePackStack 应在 1.1.0 使用 VarInt 计数")
    void resourcePackStackShouldUseVarIntCountsOn110() {
        TestResourcePack pack = new TestResourcePack("stack-pack", "2.0.0", 123, new byte[]{9});

        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.mustAccept = true;
        packet.behaviourPackStack = new ResourcePack[]{pack};
        packet.resourcePackStack = new ResourcePack[]{pack};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putBoolean(true);
        expected.putUnsignedVarInt(1);
        expected.putString("stack-pack");
        expected.putString("2.0.0");
        expected.putUnsignedVarInt(1);
        expected.putString("stack-pack");
        expected.putString("2.0.0");

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("StartGame 应匹配 GenisysPro 的 1.1 布局")
    void startGameShouldMatchGenisysPro110Layout() {
        StartGamePacket packet = new StartGamePacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.entityUniqueId = 1;
        packet.entityRuntimeId = 2;
        packet.playerGamemode = 3;
        packet.x = 1.25f;
        packet.y = 64.5f;
        packet.z = -3.75f;
        packet.pitch = 10.5f;
        packet.yaw = 20.25f;
        packet.seed = 42;
        packet.dimension = 1;
        packet.generator = 2;
        packet.gamemode = 4;
        packet.difficulty = 5;
        packet.spawnX = 10;
        packet.spawnY = 64;
        packet.spawnZ = -8;
        packet.hasAchievementsDisabled = true;
        packet.dayCycleStopTime = -1;
        packet.eduMode = false;
        packet.rainLevel = 0.25f;
        packet.lightningLevel = 0.5f;
        packet.commandsEnabled = true;
        packet.isTexturePacksRequired = false;
        packet.ruleDatas = new RuleData[0];
        packet.levelId = "world-id";
        packet.worldName = "Retro";
        packet.premiumWorldTemplateId = "";
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVarLong(1);
        expected.putVarLong(2);
        expected.putVarInt(3);
        expected.putVector3f(1.25f, 64.5f, -3.75f);
        expected.putLFloat(10.5f);
        expected.putLFloat(20.25f);
        expected.putVarInt(42);
        expected.putVarInt(1);
        expected.putVarInt(2);
        expected.putVarInt(4);
        expected.putVarInt(5);
        expected.putBlockCoords(10, 64, -8);
        expected.putBoolean(true);
        expected.putVarInt(-1);
        expected.putBoolean(false);
        expected.putLFloat(0.25f);
        expected.putLFloat(0.5f);
        expected.putBoolean(true);
        expected.putBoolean(false);
        expected.putUnsignedVarInt(0);
        expected.putString("world-id");
        expected.putString("Retro");
        expected.putString("");

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("ChunkRadius 包应在 1.1.0 使用 VarInt")
    void chunkRadiusPacketsShouldUseVarIntOn110() {
        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0);
        input.putVarInt(8);

        RequestChunkRadiusPacket request = new RequestChunkRadiusPacket();
        request.protocol = ProtocolInfo.v1_1_0;
        request.setBuffer(input.getBuffer(), 1);
        request.decode();

        ChunkRadiusUpdatedPacket response = new ChunkRadiusUpdatedPacket();
        response.protocol = ProtocolInfo.v1_1_0;
        response.radius = 8;
        response.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVarInt(8);

        assertAll(
                () -> assertEquals(8, request.radius),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(response.getBuffer(), 1, response.getBuffer().length))
        );
    }

    @Test
    @DisplayName("ContainerSetContent 应在 1.1.0 写入窗口 VarInt 与目标实体 ID")
    void containerSetContentShouldUse110EntityLayout() {
        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.windowid = ContainerSetContentPacket.SPECIAL_INVENTORY;
        packet.eid = 321L;
        packet.slots = new Item[]{Item.get(Item.STONE, 0, 2)};
        packet.hotbar = new int[]{3};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putUnsignedVarInt(ContainerSetContentPacket.SPECIAL_INVENTORY);
        expected.putVarLong(321L);
        expected.putUnsignedVarInt(1);
        expected.putSlot(Item.get(Item.STONE, 0, 2));
        expected.putUnsignedVarInt(1);
        expected.putVarInt(3);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("ContainerSetContent 应保留 1.0.0 的字节窗口布局")
    void containerSetContentShouldKeep100ByteWindowLayout() {
        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v1_0_0_0;
        packet.windowid = ContainerSetContentPacket.SPECIAL_INVENTORY;
        packet.eid = 321L;
        packet.slots = new Item[]{Item.get(Item.STONE, 0, 2)};
        packet.hotbar = new int[]{3};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(ContainerSetContentPacket.SPECIAL_INVENTORY);
        expected.putUnsignedVarInt(1);
        expected.putSlot(Item.get(Item.STONE, 0, 2));
        expected.putUnsignedVarInt(1);
        expected.putVarInt(3);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("ContainerOpen 应在 1.1.0 省略 slots 字段")
    void containerOpenShouldOmitSlotsOn110() {
        ContainerOpenPacket packet = new ContainerOpenPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.windowid = 2;
        packet.type = 4;
        packet.slots = 27;
        packet.x = 10;
        packet.y = 65;
        packet.z = -8;
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte((byte) 2);
        expected.putByte((byte) 4);
        expected.putBlockCoords(10, 65, -8);
        expected.putVarLong(-1);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("ContainerOpen 应保留 1.0.0 的 slots 字段")
    void containerOpenShouldKeepSlotsOn100() {
        ContainerOpenPacket packet = new ContainerOpenPacket();
        packet.protocol = ProtocolInfo.v1_0_0_0;
        packet.windowid = 2;
        packet.type = 4;
        packet.slots = 27;
        packet.x = 10;
        packet.y = 65;
        packet.z = -8;
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte((byte) 2);
        expected.putByte((byte) 4);
        expected.putVarInt(27);
        expected.putBlockCoords(10, 65, -8);
        expected.putVarLong(-1);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("MobEquipment 应在 0.16.0、1.0.0 与 1.1.0 都包含 windowId")
    void mobEquipmentShouldIncludeWindowIdOn0160AndAbove() {
        MobEquipmentPacket packet016 = new MobEquipmentPacket();
        packet016.protocol = ProtocolInfo.v0_16_0;
        packet016.eid = 7L;
        packet016.item = Item.get(Item.STONE_SWORD, 0, 1);
        packet016.slot = 1;
        packet016.selectedSlot = 2;
        packet016.windowId = 119;
        packet016.encode();

        MobEquipmentPacket packet100 = new MobEquipmentPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.eid = 7L;
        packet100.item = Item.get(Item.STONE_SWORD, 0, 1);
        packet100.slot = 1;
        packet100.selectedSlot = 2;
        packet100.windowId = 119;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putVarLong(7L);
        expected100.putSlot(Item.get(Item.STONE_SWORD, 0, 1));
        expected100.putByte((byte) 1);
        expected100.putByte((byte) 2);
        expected100.putByte((byte) 119);

        MobEquipmentPacket packet110 = new MobEquipmentPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.eid = 7L;
        packet110.item = Item.get(Item.STONE_SWORD, 0, 1);
        packet110.slot = 1;
        packet110.selectedSlot = 2;
        packet110.windowId = 119;
        packet110.encode();

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet016.getBuffer(), 1, packet016.getBuffer().length)),
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("MovePlayer 应在 1.1.0 使用 GenisysPro 的尾部 runtime id 布局")
    void movePlayerShouldUseGenisysPro110Layout() {
        MovePlayerPacket packet100 = new MovePlayerPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.eid = 11L;
        packet100.x = 1.25f;
        packet100.y = 64.0f;
        packet100.z = -2.5f;
        packet100.pitch = 12.5f;
        packet100.headYaw = 34.5f;
        packet100.yaw = 56.5f;
        packet100.mode = MovePlayerPacket.MODE_NORMAL;
        packet100.onGround = true;
        packet100.ridingEid = 99L;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putVarLong(11L);
        expected100.putVector3f(1.25f, 64.0f, -2.5f);
        expected100.putLFloat(12.5f);
        expected100.putLFloat(56.5f);
        expected100.putLFloat(34.5f);
        expected100.putByte(MovePlayerPacket.MODE_NORMAL);
        expected100.putBoolean(true);

        MovePlayerPacket packet110 = new MovePlayerPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.eid = 11L;
        packet110.x = 1.25f;
        packet110.y = 64.0f;
        packet110.z = -2.5f;
        packet110.pitch = 12.5f;
        packet110.headYaw = 34.5f;
        packet110.yaw = 56.5f;
        packet110.mode = MovePlayerPacket.MODE_NORMAL;
        packet110.onGround = true;
        packet110.ridingEid = 99L;
        packet110.encode();

        BinaryStream expected110 = new BinaryStream();
        expected110.putVarLong(11L);
        expected110.putVector3f(1.25f, 64.0f, -2.5f);
        expected110.putLFloat(12.5f);
        expected110.putLFloat(56.5f);
        expected110.putLFloat(34.5f);
        expected110.putByte(MovePlayerPacket.MODE_NORMAL);
        expected110.putBoolean(true);
        expected110.putVarLong(99L);

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected110.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("MovePlayer 应按 1.1.0 的 rotation 布局解码而不读取额外 teleport 整数")
    void movePlayerShouldDecodeRotationLayoutOn110() {
        BinaryStream input = new BinaryStream();
        input.putVarLong(21L);
        input.putVector3f(1.25f, 64.0f, -2.5f);
        input.putLFloat(12.5f);
        input.putLFloat(56.5f);
        input.putLFloat(34.5f);
        input.putByte(MovePlayerPacket.MODE_ROTATION);
        input.putBoolean(true);
        input.putVarLong(77L);

        MovePlayerPacket packet = new MovePlayerPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals(21L, packet.eid),
                () -> assertEquals(1.25f, packet.x, 0.0001f),
                () -> assertEquals(64.0f, packet.y, 0.0001f),
                () -> assertEquals(-2.5f, packet.z, 0.0001f),
                () -> assertEquals(12.5f, packet.pitch, 0.0001f),
                () -> assertEquals(56.5f, packet.yaw, 0.0001f),
                () -> assertEquals(34.5f, packet.headYaw, 0.0001f),
                () -> assertEquals(MovePlayerPacket.MODE_ROTATION, packet.mode),
                () -> assertTrue(packet.onGround),
                () -> assertEquals(77L, packet.ridingEid)
        );
    }

    @Test
    @DisplayName("MoveEntity 应在 1.1.0 使用 GenisysPro 的尾部 flags 布局")
    void moveEntityShouldUseGenisysPro110Layout() {
        MoveEntityPacket packet100 = new MoveEntityPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.eid = 11L;
        packet100.x = 1.25f;
        packet100.y = 64.0f;
        packet100.z = -2.5f;
        packet100.pitch = 12.5f;
        packet100.yaw = 56.5f;
        packet100.headYaw = 34.5f;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putVarLong(11L);
        expected100.putVector3f(1.25f, 64.0f, -2.5f);
        expected100.putByte((byte) (12.5f / (360f / 256f)));
        expected100.putByte((byte) (56.5f / (360f / 256f)));
        expected100.putByte((byte) (34.5f / (360f / 256f)));

        MoveEntityPacket packet110 = new MoveEntityPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.eid = 11L;
        packet110.x = 1.25f;
        packet110.y = 64.0f;
        packet110.z = -2.5f;
        packet110.pitch = 12.5f;
        packet110.yaw = 56.5f;
        packet110.headYaw = 34.5f;
        packet110.flags = 0;
        packet110.encode();

        BinaryStream expected110 = new BinaryStream();
        expected110.putVarLong(11L);
        expected110.putVector3f(1.25f, 64.0f, -2.5f);
        expected110.putByte((byte) (12.5f / (360f / 256f)));
        expected110.putByte((byte) (56.5f / (360f / 256f)));
        expected110.putByte((byte) (34.5f / (360f / 256f)));
        expected110.putByte((byte) 0);

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected110.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("1.0.9 anvil 区块 payload 应匹配 GenisysPro 布局")
    void anvilChunkPayloadShouldMatchGenisysPro109Layout() {
        Chunk chunk = Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(1, 2, 3, Item.STONE);
        chunk.setHeightMap(1, 3, 7);

        byte[] payload = Anvil.serializeNetworkChunkPayload(chunk, new byte[0]);

        BinaryStream expected = new BinaryStream();
        expected.putByte((byte) 1);
        expected.putByte((byte) 0);
        expected.put(chunk.getSections()[0].getBytes());
        for (int height : chunk.getHeightMapArray()) {
            expected.putLShort(height);
        }
        expected.put(chunk.getBiomeIdArray());
        expected.putByte((byte) 0);
        expected.putVarInt(0);

        assertArrayEquals(expected.getBuffer(), payload);
    }

    @Test
    @DisplayName("1.1.0 正常移动不应因为 forceMovement 残留被直接回弹")
    void movePlayerShouldNotReset110WhenForceMovementIsStale() throws Exception {
        TestPlayer player = newTestPlayer(ProtocolInfo.v1_1_0);
        player.x = 0;
        player.y = 64;
        player.z = 0;
        player.yaw = 0;
        player.pitch = 0;
        player.spawned = true;
        player.setHealthDirect(20);
        player.setForceMovement(new Vector3(0, 64, 0));

        MovePlayerPacket packet = new MovePlayerPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.x = 0.4f;
        packet.y = 64f + player.getEyeHeight();
        packet.z = 0;
        packet.yaw = 15;
        packet.pitch = 5;
        packet.headYaw = 15;
        packet.mode = MovePlayerPacket.MODE_NORMAL;
        packet.onGround = true;

        player.handleDataPacket(packet);

        assertAll(
                () -> assertEquals(0, player.resetCount),
                () -> assertEquals(15f, player.yaw),
                () -> assertEquals(5f, player.pitch),
                () -> assertEquals(0.4, player.getNewPosition().x, 0.000001),
                () -> assertEquals(64, player.getNewPosition().y, 0.000001),
                () -> assertEquals(0, player.getNewPosition().z, 0.000001),
                () -> assertEquals(null, player.getForceMovement())
        );
    }

    @Test
    @DisplayName("Player.updateMovement 不应再为 1.1.0 玩家补发 MovePlayer 广播")
    void playerUpdateMovementShouldNotBroadcastMovePlayer() throws Exception {
        TestPlayer player = newTestPlayer(ProtocolInfo.v1_1_0);
        player.x = 10;
        player.y = 64;
        player.z = -4;
        player.yaw = 30;
        player.pitch = 10;
        player.lastX = 0;
        player.lastY = 64;
        player.lastZ = -4;
        player.lastYaw = 0;
        player.lastPitch = 0;

        player.invokeUpdateMovement();

        assertEquals(0, player.addMovementCount);
    }

    @Test
    @DisplayName("AdventureSettings 应从 1.1.0 开始编码 muted 标记")
    void adventureSettingsShouldOnlyEncodeMutedFlagOn110() {
        AdventureSettingsPacket packet100 = new AdventureSettingsPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.worldImmutable = true;
        packet100.autoJump = true;
        packet100.muted = true;
        packet100.userPermission = 1;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putUnsignedVarInt((1 << 0) | (1 << 5));
        expected100.putUnsignedVarInt(1);

        AdventureSettingsPacket packet110 = new AdventureSettingsPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.worldImmutable = true;
        packet110.autoJump = true;
        packet110.muted = true;
        packet110.userPermission = 1;
        packet110.encode();

        BinaryStream expected110 = new BinaryStream();
        expected110.putUnsignedVarInt((1 << 0) | (1 << 5) | (1 << 10));
        expected110.putUnsignedVarInt(1);

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected110.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("TextPacket 应在 1.0.0 与 1.1.0 的 whisper 中包含 source")
    void textPacketWhisperShouldIncludeSourceOn100And110() {
        TextPacket packet100 = new TextPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.type = TextPacket.TYPE_WHISPER;
        packet100.source = "Alice";
        packet100.message = "Hello";
        packet100.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(TextPacket.TYPE_WHISPER);
        expected.putString("Alice");
        expected.putString("Hello");

        TextPacket packet110 = new TextPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.type = TextPacket.TYPE_WHISPER;
        packet110.source = "Alice";
        packet110.message = "Hello";
        packet110.encode();

        assertAll(
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("0.16.0 的 whisper 不应被误编码为 1.0.0 布局")
    void textPacketWhisperShouldNotUse100LayoutOn0160() {
        TextPacket packet = new TextPacket();
        packet.protocol = ProtocolInfo.v0_16_0;
        packet.type = TextPacket.TYPE_WHISPER;
        packet.source = "Alice";
        packet.message = "Hello";
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(TextPacket.TYPE_WHISPER);
        expected.putString("Hello");

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("AddEntity 应在 1.0.0 与 1.1.0 使用旧属性列表布局")
    void addEntityShouldUseLegacyAttributeLayoutOn100And110() {
        Attribute.init();
        Attribute health = Attribute.getAttribute(Attribute.MAX_HEALTH).setValue(18f);
        EntityMetadata metadata = new EntityMetadata().putLong(Entity.DATA_FLAGS, 1L << Entity.DATA_FLAG_SPRINTING);

        AddEntityPacket packet100 = new AddEntityPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.entityUniqueId = 55L;
        packet100.entityRuntimeId = 66L;
        packet100.type = 12;
        packet100.x = 1.5f;
        packet100.y = 64.0f;
        packet100.z = -2.25f;
        packet100.speedX = 0.1f;
        packet100.speedY = 0.2f;
        packet100.speedZ = 0.3f;
        packet100.pitch = 45f;
        packet100.yaw = 90f;
        packet100.attributes = new Attribute[]{health};
        packet100.metadata = metadata;
        packet100.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVarLong(55L);
        expected.putVarLong(66L);
        expected.putUnsignedVarInt(12);
        expected.putVector3f(1.5f, 64.0f, -2.25f);
        expected.putVector3f(0.1f, 0.2f, 0.3f);
        expected.putLFloat(45f * (256f / 360f));
        expected.putLFloat(90f * (256f / 360f));
        expected.putUnsignedVarInt(1);
        expected.putString(health.getName());
        expected.putLFloat(health.getMinValue());
        expected.putLFloat(health.getValue());
        expected.putLFloat(health.getMaxValue());
        expected.put(cn.nukkit.utils.Binary.writeMetadata(ProtocolInfo.v1_0_0_0, metadata));
        expected.putUnsignedVarInt(0);

        AddEntityPacket packet110 = new AddEntityPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.entityUniqueId = 55L;
        packet110.entityRuntimeId = 66L;
        packet110.type = 12;
        packet110.x = 1.5f;
        packet110.y = 64.0f;
        packet110.z = -2.25f;
        packet110.speedX = 0.1f;
        packet110.speedY = 0.2f;
        packet110.speedZ = 0.3f;
        packet110.pitch = 45f;
        packet110.yaw = 90f;
        packet110.attributes = new Attribute[]{health};
        packet110.metadata = metadata;
        packet110.encode();

        BinaryStream expected110 = new BinaryStream();
        expected110.putVarLong(55L);
        expected110.putVarLong(66L);
        expected110.putUnsignedVarInt(12);
        expected110.putVector3f(1.5f, 64.0f, -2.25f);
        expected110.putVector3f(0.1f, 0.2f, 0.3f);
        expected110.putLFloat(45f * (256f / 360f));
        expected110.putLFloat(90f * (256f / 360f));
        expected110.putUnsignedVarInt(1);
        expected110.putString(health.getName());
        expected110.putLFloat(health.getMinValue());
        expected110.putLFloat(health.getValue());
        expected110.putLFloat(health.getMaxValue());
        expected110.put(cn.nukkit.utils.Binary.writeMetadata(ProtocolInfo.v1_1_0, metadata));
        expected110.putUnsignedVarInt(0);

        assertAll(
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected110.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("AddItemEntity 不应在 1.1.0 追加元数据")
    void addItemEntityShouldNotAppendMetadataOn110() {
        AddItemEntityPacket packet = new AddItemEntityPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.entityUniqueId = 77L;
        packet.entityRuntimeId = 88L;
        packet.item = Item.get(Item.STONE, 0, 1);
        packet.x = 1.25f;
        packet.y = 64.5f;
        packet.z = -3.75f;
        packet.speedX = 0.1f;
        packet.speedY = 0.2f;
        packet.speedZ = 0.3f;
        packet.metadata = new EntityMetadata().putLong(Entity.DATA_FLAGS, 1L << Entity.DATA_FLAG_SPRINTING);
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVarLong(77L);
        expected.putVarLong(88L);
        expected.putSlot(Item.get(Item.STONE, 0, 1));
        expected.putVector3f(1.25f, 64.5f, -3.75f);
        expected.putVector3f(0.1f, 0.2f, 0.3f);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("SetSpawnPosition 应在 1.0.0 与 1.1.0 使用 VarInt")
    void setSpawnPositionShouldUseSignedVarIntOn100And110() {
        SetSpawnPositionPacket packet100 = new SetSpawnPositionPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.spawnType = -1;
        packet100.x = 10;
        packet100.y = 64;
        packet100.z = -8;
        packet100.spawnForced = true;
        packet100.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVarInt(-1);
        expected.putBlockCoords(10, 64, -8);
        expected.putBoolean(true);

        SetSpawnPositionPacket packet110 = new SetSpawnPositionPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.spawnType = -1;
        packet110.x = 10;
        packet110.y = 64;
        packet110.z = -8;
        packet110.spawnForced = true;
        packet110.encode();

        assertAll(
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("InventoryAction 应从 1.0.0 开始附带附魔参数")
    void inventoryActionShouldIncludeEnchantFieldsFrom100() {
        InventoryActionPacket packet016 = new InventoryActionPacket();
        packet016.protocol = ProtocolInfo.v0_16_0;
        packet016.actionId = 2;
        packet016.item = Item.get(Item.BOOK, 0, 1);
        packet016.enchantmentId = 5;
        packet016.enchantmentLevel = 3;
        packet016.encode();

        BinaryStream expected016 = new BinaryStream();
        expected016.putUnsignedVarInt(2);
        expected016.putSlot(Item.get(Item.BOOK, 0, 1));

        InventoryActionPacket packet100 = new InventoryActionPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.actionId = 2;
        packet100.item = Item.get(Item.BOOK, 0, 1);
        packet100.enchantmentId = 5;
        packet100.enchantmentLevel = 3;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putUnsignedVarInt(2);
        expected100.putSlot(Item.get(Item.BOOK, 0, 1));
        expected100.putVarInt(5);
        expected100.putVarInt(3);

        assertAll(
                () -> assertArrayEquals(expected016.getBuffer(), Arrays.copyOfRange(packet016.getBuffer(), 1, packet016.getBuffer().length)),
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length))
        );
    }

    @Test
    @DisplayName("AddPlayer 应在 1.1.0 使用独立 headYaw")
    void addPlayerShouldUseDistinctHeadYawOn110() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");

        AddPlayerPacket packet = new AddPlayerPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.uuid = uuid;
        packet.username = "Retro";
        packet.entityUniqueId = 10L;
        packet.entityRuntimeId = 11L;
        packet.x = 1.25f;
        packet.y = 64.5f;
        packet.z = -3.75f;
        packet.speedX = 0.1f;
        packet.speedY = 0.2f;
        packet.speedZ = 0.3f;
        packet.pitch = 15f;
        packet.headYaw = 25f;
        packet.yaw = 35f;
        packet.item = Item.get(Item.STONE_SWORD, 0, 1);
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putUUID(uuid);
        expected.putString("Retro");
        expected.putVarLong(10L);
        expected.putVarLong(11L);
        expected.putVector3f(1.25f, 64.5f, -3.75f);
        expected.putVector3f(0.1f, 0.2f, 0.3f);
        expected.putLFloat(15f);
        expected.putLFloat(25f);
        expected.putLFloat(35f);
        expected.putSlot(Item.get(Item.STONE_SWORD, 0, 1));
        expected.put(cn.nukkit.utils.Binary.writeMetadata(ProtocolInfo.v1_1_0, new EntityMetadata()));

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("Animate 应从 1.1.0 开始附带尾部 float")
    void animateShouldIncludeFloatFrom110() {
        AnimatePacket packet100 = new AnimatePacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.action = 0x80;
        packet100.eid = 7L;
        packet100.unknown = 1.25f;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putUnsignedVarInt(0x80);
        expected100.putVarLong(7L);

        AnimatePacket packet110 = new AnimatePacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.action = 0x80;
        packet110.eid = 7L;
        packet110.unknown = 1.25f;
        packet110.encode();

        BinaryStream expected110 = new BinaryStream();
        expected110.putUnsignedVarInt(0x80);
        expected110.putVarLong(7L);
        expected110.putLFloat(1.25f);

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected110.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("Disconnect 应在 1.1.0 的隐藏界面模式下省略消息")
    void disconnectShouldOmitMessageWhenHiddenOn110() {
        DisconnectPacket packet100 = new DisconnectPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.hideDisconnectionScreen = true;
        packet100.message = "Hidden";
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putBoolean(true);
        expected100.putString("Hidden");

        DisconnectPacket packet110 = new DisconnectPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.hideDisconnectionScreen = true;
        packet110.message = "Hidden";
        packet110.encode();

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(new byte[]{1}, Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("LevelSoundEvent 应从 1.1.0 开始使用新版声音编号")
    void levelSoundEventShouldUseModernIdsFrom110() {
        LevelSoundEventPacket packet100 = new LevelSoundEventPacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.sound = LevelSoundEventPacket.SOUND_SHULKERBOX_OPEN;
        packet100.x = 1.5f;
        packet100.y = 64f;
        packet100.z = -2.5f;
        packet100.extraData = 7;
        packet100.pitch = 2;
        packet100.unknownBool = true;
        packet100.disableRelativeVolume = false;
        packet100.encode();

        BinaryStream expected100 = new BinaryStream();
        expected100.putByte((byte) 95);
        expected100.putVector3f(1.5f, 64f, -2.5f);
        expected100.putVarInt(7);
        expected100.putVarInt(2);
        expected100.putBoolean(true);
        expected100.putBoolean(false);

        LevelSoundEventPacket packet110 = new LevelSoundEventPacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.sound = LevelSoundEventPacket.SOUND_SHULKERBOX_OPEN;
        packet110.x = 1.5f;
        packet110.y = 64f;
        packet110.z = -2.5f;
        packet110.extraData = 7;
        packet110.pitch = 2;
        packet110.unknownBool = true;
        packet110.disableRelativeVolume = false;
        packet110.encode();

        BinaryStream expected110 = new BinaryStream();
        expected110.putByte(LevelSoundEventPacket.SOUND_SHULKERBOX_OPEN);
        expected110.putVector3f(1.5f, 64f, -2.5f);
        expected110.putVarInt(7);
        expected110.putVarInt(2);
        expected110.putBoolean(true);
        expected110.putBoolean(false);

        assertAll(
                () -> assertArrayEquals(expected100.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected110.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("CommandStep 应按无符号 VarLong 读取 clientId")
    void commandStepShouldDecodeUnsignedClientId() {
        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0);
        input.putString("version");
        input.putString("default");
        input.putUnsignedVarInt(1);
        input.putUnsignedVarInt(2);
        input.putBoolean(true);
        input.putUnsignedVarLong(123456789L);
        input.putString("{}");
        input.putString("{\"ok\":true}");

        CommandStepPacket packet = new CommandStepPacket();
        packet.protocol = ProtocolInfo.v1_1_3;
        packet.setBuffer(input.getBuffer(), 1);
        packet.decode();

        assertAll(
                () -> assertEquals("version", packet.command),
                () -> assertEquals("default", packet.overload),
                () -> assertTrue(packet.done),
                () -> assertEquals(123456789L, packet.clientId)
        );
    }

    @Test
    @DisplayName("SetTime 应在 0.16.0、1.0.0 与 1.1.0 都附带 started 标志")
    void setTimeShouldIncludeStartedFlagOn0160AndAbove() {
        SetTimePacket packet016 = new SetTimePacket();
        packet016.protocol = ProtocolInfo.v0_16_0;
        packet016.time = 6000;
        packet016.started = false;
        packet016.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVarInt(6000);
        expected.putBoolean(false);

        SetTimePacket packet100 = new SetTimePacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.time = 6000;
        packet100.started = false;
        packet100.encode();

        SetTimePacket packet110 = new SetTimePacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.time = 6000;
        packet110.started = false;
        packet110.encode();

        assertAll(
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet016.getBuffer(), 1, packet016.getBuffer().length)),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    @Test
    @DisplayName("Explode 应在 1.0.0 与 1.1.0 继续使用 LFloat 半径")
    void explodeShouldKeepFloatRadiusOn100And110() {
        ExplodePacket packet100 = new ExplodePacket();
        packet100.protocol = ProtocolInfo.v1_0_0_0;
        packet100.x = 1.5f;
        packet100.y = 64f;
        packet100.z = -2.5f;
        packet100.radius = 4.5f;
        packet100.encode();

        BinaryStream expected = new BinaryStream();
        expected.putVector3f(1.5f, 64f, -2.5f);
        expected.putLFloat(4.5f);
        expected.putUnsignedVarInt(0);

        ExplodePacket packet110 = new ExplodePacket();
        packet110.protocol = ProtocolInfo.v1_1_0;
        packet110.x = 1.5f;
        packet110.y = 64f;
        packet110.z = -2.5f;
        packet110.radius = 4.5f;
        packet110.encode();

        assertAll(
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet100.getBuffer(), 1, packet100.getBuffer().length)),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet110.getBuffer(), 1, packet110.getBuffer().length))
        );
    }

    private static final class SharedPoolProbePacket extends DataPacket {

        @Override
        public byte pid() {
            return 0x7a;
        }

        @Override
        public void decode() {
        }

        @Override
        public void encode() {
        }
    }

    private static final class TestResourcePack implements ResourcePack {
        private final String packId;
        private final String version;
        private final int size;
        private final byte[] sha256;

        private TestResourcePack(String packId, String version, int size, byte[] sha256) {
            this.packId = packId;
            this.version = version;
            this.size = size;
            this.sha256 = sha256;
        }

        @Override
        public String getPackName() {
            return this.packId;
        }

        @Override
        public String getPackId() {
            return this.packId;
        }

        @Override
        public String getPackVersion() {
            return this.version;
        }

        @Override
        public int getPackSize() {
            return this.size;
        }

        @Override
        public byte[] getSha256() {
            return this.sha256;
        }

        @Override
        public byte[] getPackChunk(int off, int len) {
            return new byte[0];
        }
    }

    private static final class TestPlayer extends Player {
        private int resetCount;
        private int addMovementCount;

        private TestPlayer() {
            super(null, null, null, 0);
        }

        @Override
        public void sendPosition(Vector3 pos, double yaw, double pitch, byte mode, Player[] targets) {
            if (mode == MovePlayerPacket.MODE_RESET) {
                this.resetCount++;
            }
        }

        @Override
        public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
            this.addMovementCount++;
        }

        private void setHealthDirect(float health) throws Exception {
            setField(Entity.class, this, "health", health);
        }

        private Vector3 getForceMovement() throws Exception {
            Field field = Player.class.getDeclaredField("forceMovement");
            field.setAccessible(true);
            return (Vector3) field.get(this);
        }

        private void setForceMovement(Vector3 vector) throws Exception {
            setField(Player.class, this, "forceMovement", vector);
        }

        private Vector3 getNewPosition() throws Exception {
            Field field = Player.class.getDeclaredField("newPosition");
            field.setAccessible(true);
            return (Vector3) field.get(this);
        }

        private void invokeUpdateMovement() {
            super.updateMovement();
        }
    }
}
