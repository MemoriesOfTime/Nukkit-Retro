package cn.nukkit.test;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.IntPositionEntityData;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemPotion;
import cn.nukkit.level.generator.biome.Biome;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.raknet.protocol.EncapsulatedPacket;
import cn.nukkit.raknet.server.ServerHandler;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Legacy 0.14 Packet")
class Legacy014PacketTest {

    @BeforeAll
    static void initItems() {
        if (Block.list == null) {
            Block.init();
        }
        if (Item.list == null) {
            Item.init();
        }
        Biome.init();
    }

    private static cn.nukkit.level.Level newLevelWithChunk(int chunkX, int chunkZ, cn.nukkit.level.format.FullChunk chunk) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        cn.nukkit.level.Level level = (cn.nukkit.level.Level) unsafe.allocateInstance(cn.nukkit.level.Level.class);

        java.util.HashMap<Long, cn.nukkit.level.format.generic.BaseFullChunk> chunks = new java.util.HashMap<>();
        chunks.put(cn.nukkit.level.Level.chunkHash(chunkX, chunkZ), (cn.nukkit.level.format.generic.BaseFullChunk) chunk);
        setLoadedChunks(level, chunks);

        return level;
    }

    private static void setLoadedChunks(cn.nukkit.level.Level level, java.util.Map<Long, cn.nukkit.level.format.generic.BaseFullChunk> chunks) throws Exception {
        Field chunksField = cn.nukkit.level.Level.class.getDeclaredField("chunks");
        chunksField.setAccessible(true);
        chunksField.set(level, chunks);
    }

    private static void setLevelProvider(cn.nukkit.level.Level level, Object provider) throws Exception {
        Field providerField = cn.nukkit.level.Level.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(level, provider);
    }

    private static TestPlayer newTestPlayer(cn.nukkit.level.Level level, int protocol) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        TestPlayer player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);

        Field connectedField = Player.class.getDeclaredField("connected");
        connectedField.setAccessible(true);
        connectedField.setBoolean(player, true);

        Field levelField = cn.nukkit.level.Position.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(player, level);

        Field usedChunksField = Player.class.getDeclaredField("usedChunks");
        usedChunksField.setAccessible(true);
        usedChunksField.set(player, new java.util.HashMap<Long, Boolean>());

        Field protocolField = Player.class.getDeclaredField("protocol");
        protocolField.setAccessible(true);
        protocolField.setInt(player, protocol);

        return player;
    }

    private static cn.nukkit.level.format.anvil.Anvil newUnsafeAnvilProvider() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (cn.nukkit.level.format.anvil.Anvil) unsafe.allocateInstance(cn.nukkit.level.format.anvil.Anvil.class);
    }

    private static CapturingServerHandler newUnsafeHandler() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (CapturingServerHandler) unsafe.allocateInstance(CapturingServerHandler.class);
    }

    private static RakNetInterface newUnsafeRakNetInterface(CapturingServerHandler handler, Player player, String identifier) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        RakNetInterface interfaz = (RakNetInterface) unsafe.allocateInstance(RakNetInterface.class);

        Field identifiersField = RakNetInterface.class.getDeclaredField("identifiers");
        identifiersField.setAccessible(true);
        java.util.Map<Integer, String> identifiers = new java.util.concurrent.ConcurrentHashMap<>();
        identifiers.put(player.rawHashCode(), identifier);
        identifiersField.set(interfaz, identifiers);

        Field identifiersAckField = RakNetInterface.class.getDeclaredField("identifiersACK");
        identifiersAckField.setAccessible(true);
        java.util.Map<String, Integer> identifiersAck = new java.util.concurrent.ConcurrentHashMap<>();
        identifiersAck.put(identifier, 0);
        identifiersAckField.set(interfaz, identifiersAck);

        Field handlerField = RakNetInterface.class.getDeclaredField("handler");
        handlerField.setAccessible(true);
        handlerField.set(interfaz, handler);

        return interfaz;
    }

    private static void putLegacyString(BinaryStream stream, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stream.putShort(bytes.length);
        stream.put(bytes);
    }

    private static void assertItemEquals(Item expected, Item actual) {
        assertAll(
                () -> assertEquals(expected.getId(), actual.getId()),
                () -> assertEquals(expected.getDamage(), actual.getDamage()),
                () -> assertEquals(expected.getCount(), actual.getCount())
        );
    }

    private static byte[] buildAnvilProviderPayload(cn.nukkit.level.format.anvil.Chunk chunk) {
        return cn.nukkit.level.format.anvil.Anvil.serializeNetworkChunkPayload(chunk, new byte[0]);
    }

    private static byte[] encodeBatchPackets(int protocol, boolean legacy012LengthPrefixed, DataPacket... packets) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Server server = (Server) unsafe.allocateInstance(Server.class);
        Method encodeMethod = Server.class.getDeclaredMethod("encodeBatchPackets", int.class, DataPacket[].class, boolean.class);
        encodeMethod.setAccessible(true);
        return (byte[]) encodeMethod.invoke(server, protocol, packets, legacy012LengthPrefixed);
    }

    private static void putLegacyMetadataString(BinaryStream stream, int id, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stream.putByte((byte) ((4 << 5) | id));
        stream.putLShort(bytes.length);
        stream.put(bytes);
    }

    @Test
    @DisplayName("0.14.x 协议应共享旧包 ID")
    void protocol014FamilyShouldUseClassicPacketIds() {
        Network network = new Network(null);

        assertAll(
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_14_0)),
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_14_1)),
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_14_2)),
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_14_3)),
                () -> assertEquals(ProtocolInfo.v0_14_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_14_0)),
                () -> assertEquals(ProtocolInfo.v0_14_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_14_1)),
                () -> assertEquals(ProtocolInfo.v0_14_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_14_2)),
                () -> assertEquals(ProtocolInfo.v0_14_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_14_3)),
                () -> assertEquals(0x8f, network.getPacketPool(ProtocolInfo.v0_14_0).getPacketId(LoginPacket.class)),
                () -> assertEquals(0x92, network.getPacketPool(ProtocolInfo.v0_14_3).getPacketId(BatchPacket.class)),
                () -> assertEquals("0.14.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_14_0)),
                () -> assertEquals("0.14.1", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_14_1)),
                () -> assertEquals("0.14.2", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_14_2)),
                () -> assertEquals("0.14.3", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_14_3))
        );
    }

    @Test
    @DisplayName("Login 应按 0.14.x 旧布局解码")
    void loginShouldDecodeClassic014Layout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 1);
        BinaryStream input = new BinaryStream();
        putLegacyString(input, "LegacyPlayer");
        input.putInt(ProtocolInfo.v0_14_2);
        input.putInt(ProtocolInfo.v0_14_2);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        putLegacyString(input, "Standard_Custom");
        input.putShort(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("LegacyPlayer", packet.username),
                () -> assertEquals(ProtocolInfo.v0_14_2, packet.getProtocol()),
                () -> assertEquals(123456789L, packet.clientId),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertEquals("secret", packet.clientSecret),
                () -> assertEquals("Standard_Custom", packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("Login 应兼容 0.14.3 的旧布局")
    void loginShouldDecodeClassic0143Layout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 3);
        BinaryStream input = new BinaryStream();
        putLegacyString(input, "LegacyPlayer");
        input.putInt(ProtocolInfo.v0_14_3);
        input.putInt(ProtocolInfo.v0_14_3);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        putLegacyString(input, "Standard_Custom");
        input.putShort(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_14_3;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("LegacyPlayer", packet.username),
                () -> assertEquals(ProtocolInfo.v0_14_3, packet.getProtocol()),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("Login 应兼容 0.14.x 皮肤整型长度布局")
    void loginShouldDecodeClassic014SkinWithIntLength() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 2);
        BinaryStream input = new BinaryStream();
        putLegacyString(input, "LegacyPlayer");
        input.putInt(ProtocolInfo.v0_14_2);
        input.putInt(ProtocolInfo.v0_14_2);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        putLegacyString(input, "Standard_Custom");
        input.putInt(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Standard_Custom", packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("Login 遇到非法 0.14.x 皮肤数据时应回退默认皮肤")
    void loginShouldFallbackToDefaultSkinWhenLegacySkinIsInvalid() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[]{1, 2, 3, 4};
        BinaryStream input = new BinaryStream();
        putLegacyString(input, "LegacyPlayer");
        input.putInt(ProtocolInfo.v0_14_2);
        input.putInt(ProtocolInfo.v0_14_2);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        putLegacyString(input, "Standard_Custom");
        input.putShort(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Standard_Custom", packet.getSkin().getModel()),
                () -> assertEquals(Skin.SINGLE_SKIN_SIZE, packet.getSkin().getData().length)
        );
    }

    @Test
    @DisplayName("BatchPacket 应按 0.14.x 旧长度字段解码")
    void batchShouldDecodeClassic014LengthPrefix() {
        byte[] payload = new byte[]{10, 20, 30};
        BinaryStream input = new BinaryStream();
        input.putInt(payload.length);
        input.put(payload);

        BatchPacket packet = new BatchPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertArrayEquals(payload, packet.payload);
    }

    @Test
    @DisplayName("BatchPacket 应兼容 0.14.3 的旧长度字段")
    void batchShouldDecodeClassic0143LengthPrefix() {
        byte[] payload = new byte[]{10, 20, 30};
        BinaryStream input = new BinaryStream();
        input.putInt(payload.length);
        input.put(payload);

        BatchPacket packet = new BatchPacket();
        packet.protocol = ProtocolInfo.v0_14_3;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertArrayEquals(payload, packet.payload);
    }

    @Test
    @DisplayName("0.14.x 出站批量包应保持整型长度前缀")
    void outgoingLegacy014BatchShouldKeepIntLengthPrefix() throws Exception {
        FullChunkDataPacket packet = new FullChunkDataPacket();
        packet.chunkX = 1;
        packet.chunkZ = 2;
        packet.order = FullChunkDataPacket.ORDER_LAYERED;
        packet.data = new byte[]{1, 2, 3, 4};

        byte[] encoded = encodeBatchPackets(ProtocolInfo.v0_14_2, false, packet);
        int packetLength = Binary.readInt(Arrays.copyOfRange(encoded, 0, 4));
        Network network = new Network(null);

        assertAll(
                () -> assertEquals(encoded.length - 4, packetLength),
                () -> assertEquals(0xbf, network.getPacketPool(ProtocolInfo.v0_14_2).getPacketId(FullChunkDataPacket.class))
        );
    }

    @Test
    @DisplayName("StartGame 应按 0.14.x 布局追加结尾字节")
    void startGameShouldUseClassic014Layout() {
        StartGamePacket packet = new StartGamePacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.seed = 123456789;
        packet.dimension = 1;
        packet.generator = 1;
        packet.gamemode = 0;
        packet.entityUniqueId = 0;
        packet.spawnX = 100;
        packet.spawnY = 65;
        packet.spawnZ = -12;
        packet.x = 1.5f;
        packet.y = 64.0f;
        packet.z = -3.25f;
        packet.b1 = true;
        packet.b2 = false;
        packet.b3 = true;
        packet.unknownstr = "";
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(packet.seed);
        expected.putByte(packet.dimension);
        expected.putInt(packet.generator);
        expected.putInt(packet.gamemode);
        expected.putLong(packet.entityUniqueId);
        expected.putInt(packet.spawnX);
        expected.putInt(packet.spawnY);
        expected.putInt(packet.spawnZ);
        expected.putFloat(packet.x);
        expected.putFloat(packet.y);
        expected.putFloat(packet.z);
        expected.putBoolean(packet.b1);
        expected.putBoolean(packet.b2);
        expected.putBoolean(packet.b3);
        putLegacyString(expected, packet.unknownstr);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("FullChunkData 应按 0.14.x 带 ordering 与整型长度编码")
    void fullChunkDataShouldUseClassic014Layout() {
        FullChunkDataPacket packet = new FullChunkDataPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.chunkX = 12;
        packet.chunkZ = -3;
        packet.order = FullChunkDataPacket.ORDER_LAYERED;
        packet.data = new byte[]{1, 2, 3, 4};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(12);
        expected.putInt(-3);
        expected.putByte(FullChunkDataPacket.ORDER_LAYERED);
        expected.putInt(4);
        expected.put(new byte[]{1, 2, 3, 4});

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("区块缓存批包应按 0.14.x 旧批量前缀编码")
    void chunkCacheBatchShouldUseClassic014BatchPrefix() throws Exception {
        BatchPacket batch = Player.getChunkCacheFromData(
                7,
                8,
                new byte[]{9, 10, 11},
                FullChunkDataPacket.ORDER_LAYERED,
                ProtocolInfo.v0_14_2
        );

        byte[] inflated = Zlib.inflate(batch.payload, 1024 * 1024);
        int chunkPacketLength = Binary.readInt(Arrays.copyOfRange(inflated, 0, 4));
        byte[] chunkPacket = Arrays.copyOfRange(inflated, 4, 4 + chunkPacketLength);

        FullChunkDataPacket expectedChunk = new FullChunkDataPacket();
        expectedChunk.protocol = ProtocolInfo.v0_14_2;
        expectedChunk.chunkX = 7;
        expectedChunk.chunkZ = 8;
        expectedChunk.order = FullChunkDataPacket.ORDER_LAYERED;
        expectedChunk.data = new byte[]{9, 10, 11};
        expectedChunk.encode();

        assertAll(
                () -> assertEquals(expectedChunk.getBuffer().length, chunkPacketLength),
                () -> assertArrayEquals(expectedChunk.getBuffer(), chunkPacket)
        );
    }

    @Test
    @DisplayName("0.14.x anvil 区块发送应保持 layered 顺序")
    void sendChunkShouldUseLayeredOrderingFor014AnvilChunk() throws Exception {
        cn.nukkit.level.format.anvil.Chunk chunk = cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(1, 2, 3, Block.STONE);

        cn.nukkit.level.Level level = newLevelWithChunk(0, 0, chunk);
        TestPlayer player = newTestPlayer(level, ProtocolInfo.v0_14_2);

        player.sendChunk(0, 0, new byte[]{7, 7, 7}, FullChunkDataPacket.ORDER_COLUMNS);

        assertAll(
                () -> assertEquals(FullChunkDataPacket.ORDER_LAYERED, player.capturedChunkPacket.order),
                () -> assertEquals(83204, player.capturedChunkPacket.data.length)
        );
    }

    @Test
    @DisplayName("0.14.x 非 anvil 区块发送应保留 provider 提供的列式数据")
    void sendChunkShouldKeepProviderPayloadFor014NonAnvilChunk() throws Exception {
        cn.nukkit.level.format.leveldb.Chunk chunk = cn.nukkit.level.format.leveldb.Chunk.getEmptyChunk(0, 0);

        cn.nukkit.level.Level level = newLevelWithChunk(0, 0, chunk);
        TestPlayer player = newTestPlayer(level, ProtocolInfo.v0_14_2);
        byte[] providerPayload = new byte[]{7, 8, 9, 10};

        player.sendChunk(0, 0, providerPayload, FullChunkDataPacket.ORDER_COLUMNS);

        assertAll(
                () -> assertEquals(FullChunkDataPacket.ORDER_COLUMNS, player.capturedChunkPacket.order),
                () -> assertArrayEquals(providerPayload, player.capturedChunkPacket.data)
        );
    }

    @Test
    @DisplayName("0.14.x 命中 anvil 缓存 payload 且 chunk 已卸载时仍应转换为 layered 顺序")
    void sendChunkShouldConvertCachedAnvilPayloadFor014WhenChunkIsNotLoaded() throws Exception {
        cn.nukkit.level.format.anvil.Chunk chunk = cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(1, 2, 3, Block.STONE);

        cn.nukkit.level.Level level = newLevelWithChunk(0, 0, chunk);
        setLevelProvider(level, newUnsafeAnvilProvider());
        setLoadedChunks(level, new java.util.HashMap<Long, cn.nukkit.level.format.generic.BaseFullChunk>());

        TestPlayer player = newTestPlayer(level, ProtocolInfo.v0_14_2);
        byte[] cachedPayload = buildAnvilProviderPayload(chunk);

        player.sendChunk(0, 0, cachedPayload, FullChunkDataPacket.ORDER_COLUMNS);

        int blockIndex = (2 << 8) | (3 << 4) | 1;
        assertNotNull(player.capturedChunkPacket);
        assertAll(
                () -> assertEquals(FullChunkDataPacket.ORDER_LAYERED, player.capturedChunkPacket.order),
                () -> assertEquals(83204, player.capturedChunkPacket.data.length),
                () -> assertEquals(Block.STONE, player.capturedChunkPacket.data[blockIndex] & 0xff)
        );
    }

    @Test
    @DisplayName("0.14.x 缓存区块经 RakNet 发出时应保持 0x8e 前缀与旧批处理布局")
    void cachedChunkShouldKeepLegacy014RakNetLayout() throws Exception {
        cn.nukkit.level.format.anvil.Chunk chunk = cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(1, 2, 3, Block.STONE);

        cn.nukkit.level.Level level = newLevelWithChunk(0, 0, chunk);
        setLevelProvider(level, newUnsafeAnvilProvider());
        setLoadedChunks(level, new java.util.HashMap<Long, cn.nukkit.level.format.generic.BaseFullChunk>());

        TestPlayer player = newTestPlayer(level, ProtocolInfo.v0_14_2);
        String identifier = "legacy014";
        CapturingServerHandler handler = newUnsafeHandler();
        RakNetInterface interfaz = newUnsafeRakNetInterface(handler, player, identifier);

        byte[] cachedPayload = buildAnvilProviderPayload(chunk);
        byte[] legacyPayload = level.convertCachedAnvilPayloadToLegacyPayload(cachedPayload);
        assertNotNull(legacyPayload);

        BatchPacket batch = Player.getChunkCacheFromData(0, 0, legacyPayload, FullChunkDataPacket.ORDER_LAYERED, ProtocolInfo.v0_14_2);
        interfaz.putPacket(player, batch, false, false);

        assertNotNull(handler.capturedPacket);
        assertAll(
                () -> assertEquals(identifier, handler.capturedIdentifier),
                () -> assertEquals(0x8e, handler.capturedPacket.buffer[0] & 0xff)
        );

        BatchPacket outgoing = new BatchPacket();
        outgoing.protocol = ProtocolInfo.v0_14_2;
        outgoing.setBuffer(handler.capturedPacket.buffer, 2);
        outgoing.decode();

        byte[] inflated = Zlib.inflate(outgoing.payload, 1024 * 1024);
        int packetLength = Binary.readInt(Arrays.copyOfRange(inflated, 0, 4));
        byte[] packetBuffer = Arrays.copyOfRange(inflated, 4, 4 + packetLength);
        FullChunkDataPacket decodedChunk = new FullChunkDataPacket();
        decodedChunk.protocol = ProtocolInfo.v0_14_2;
        decodedChunk.setBuffer(Arrays.copyOfRange(packetBuffer, 1, packetBuffer.length), 0);
        decodedChunk.decode();

        int blockIndex = (2 << 8) | (3 << 4) | 1;
        assertAll(
                () -> assertEquals(FullChunkDataPacket.ORDER_LAYERED, decodedChunk.order),
                () -> assertEquals(83204, decodedChunk.data.length),
                () -> assertEquals(Block.STONE, decodedChunk.data[blockIndex] & 0xff)
        );
    }

    @Test
    @DisplayName("PlayerList 应按 0.14.x 旧字符串与皮肤布局编码")
    void playerListShouldUseClassic014Layout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 7);

        PlayerListPacket packet = new PlayerListPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.type = PlayerListPacket.TYPE_ADD;
        packet.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(uuid, 321L, "LegacyPlayer", new Skin(skinData, "Standard_Custom"))
        };
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(PlayerListPacket.TYPE_ADD);
        expected.putInt(1);
        expected.putUUID(uuid);
        expected.putLong(321L);
        putLegacyString(expected, "LegacyPlayer");
        putLegacyString(expected, "Standard_Custom");
        expected.putShort(skinData.length);
        expected.put(skinData);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("ContainerSetContent 应按 0.14.x 旧物品布局编码")
    void containerSetContentShouldUseClassic014SlotLayout() {
        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
        packet.slots = new Item[]{Item.get(1, 2, 3)};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte((byte) ContainerSetContentPacket.SPECIAL_CREATIVE);
        expected.putShort(1);
        expected.putShort(1);
        expected.putByte((byte) 3);
        expected.putShort(2);
        expected.putLShort(0);
        expected.putShort(0);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("0.14.x 创造背包容器应完整消费尾部 hotbar 字段")
    void creativeContainerShouldConsumeClassic014HotbarTail() {
        Item[] creativeSlots = Item.getCreativeItems(ProtocolInfo.v0_14_2).toArray(new Item[0]);

        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
        packet.slots = creativeSlots;
        packet.encode();

        ContainerSetContentPacket decoded = new ContainerSetContentPacket();
        decoded.protocol = ProtocolInfo.v0_14_2;
        decoded.setBuffer(packet.getBuffer(), 1);
        decoded.decode();

        assertAll(
                () -> assertEquals(ContainerSetContentPacket.SPECIAL_CREATIVE, decoded.windowid),
                () -> assertEquals(packet.getBuffer().length, decoded.getOffset()),
                () -> assertEquals(creativeSlots.length, decoded.slots.length),
                () -> assertEquals(0, decoded.hotbar.length),
                () -> assertItemEquals(creativeSlots[0], decoded.slots[0]),
                () -> assertItemEquals(creativeSlots[200], decoded.slots[200]),
                () -> assertItemEquals(creativeSlots[creativeSlots.length - 1], decoded.slots[decoded.slots.length - 1])
        );
    }

    @Test
    @DisplayName("0.14.x 应使用独立旧创造物品表")
    void creativeItemsShouldUse014SpecificDataSet() {
        assertAll(
                () -> assertEquals(474, Item.getCreativeItems(ProtocolInfo.v0_14_2).size()),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_14_0, Item.get(Item.WOOL, 0)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.DYE, 0)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.SPAWN_EGG, 15)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.SPAWN_EGG, 17)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.SPAWN_EGG, 32)) != -1),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.RED_SANDSTONE, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.SHULKER_SHELL))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_14_2, Item.get(Item.LINGERING_POTION, ItemPotion.NO_EFFECTS))),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v1_1_0, Item.get(Item.RED_SANDSTONE, 0)) != -1)
        );
    }

    @Test
    @DisplayName("isSupportedOn 应区分 0.14 既有物品与后续版本物品")
    void itemSupportShouldRespectLegacyVersionThresholds() {
        assertAll(
                () -> assertTrue(Item.get(Item.SPRUCE_DOOR).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertTrue(Item.get(Item.BEETROOT).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertTrue(Item.get(Item.COOKED_SALMON).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertTrue(Item.get(Item.SPLASH_POTION, ItemPotion.NO_EFFECTS).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertFalse(Item.get(Item.ELYTRA).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertFalse(Item.get(Item.CHORUS_FRUIT).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertFalse(Item.get(Item.LINGERING_POTION, ItemPotion.NO_EFFECTS).isSupportedOn(ProtocolInfo.v0_16_0)),
                () -> assertFalse(Item.get(Item.SHULKER_BOX).isSupportedOn(ProtocolInfo.v1_0_7)),
                () -> assertFalse(Item.get(Item.CONCRETE, 0).isSupportedOn(ProtocolInfo.v1_0_7)),
                () -> assertTrue(Item.get(Item.ELYTRA).isSupportedOn(ProtocolInfo.v1_0_0)),
                () -> assertTrue(Item.get(Item.CHORUS_FRUIT).isSupportedOn(ProtocolInfo.v1_0_0)),
                () -> assertTrue(Item.get(Item.SHULKER_BOX).isSupportedOn(ProtocolInfo.v1_1_0)),
                () -> assertTrue(Item.get(Item.CONCRETE, 0).isSupportedOn(ProtocolInfo.v1_1_0))
        );
    }

    @Test
    @DisplayName("SetEntityData 应按 0.14.x 旧元数据映射编码")
    void setEntityDataShouldUseClassic014MetadataLayout() {
        EntityMetadata metadata = new EntityMetadata()
                .putLong(Entity.DATA_FLAGS, (1L << Entity.DATA_FLAG_SNEAKING) | (1L << Entity.DATA_FLAG_CAN_SHOW_NAMETAG) | (1L << Entity.DATA_FLAG_SILENT) | (1L << Entity.DATA_FLAG_NO_AI))
                .putShort(Entity.DATA_AIR, 250)
                .putString(Entity.DATA_NAMETAG, "Retro")
                .putByte(EntityHuman.DATA_PLAYER_FLAGS, EntityHuman.DATA_PLAYER_FLAG_SLEEP)
                .put(new IntPositionEntityData(EntityHuman.DATA_PLAYER_BED_POSITION, 1, 2, 3));

        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.eid = 99L;
        packet.metadata = metadata;
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putLong(99L);
        expected.putByte((byte) 0);
        expected.putByte((byte) (1 << Entity.DATA_FLAG_SNEAKING));
        expected.putByte((byte) ((1 << 5) | 1));
        expected.putLShort(250);
        putLegacyMetadataString(expected, 2, "Retro");
        expected.putByte((byte) 3);
        expected.putByte((byte) 1);
        expected.putByte((byte) 4);
        expected.putByte((byte) 1);
        expected.putByte((byte) 15);
        expected.putByte((byte) 1);
        expected.putByte((byte) 16);
        expected.putByte((byte) EntityHuman.DATA_PLAYER_FLAG_SLEEP);
        expected.putByte((byte) ((6 << 5) | 17));
        expected.putLInt(1);
        expected.putLInt(2);
        expected.putLInt(3);
        // key 23 = DATA_LEAD_HOLDER (long), default -1
        expected.putByte((byte) ((7 << 5) | 23));
        expected.putLLong(-1);
        // key 24 = DATA_LEAD (byte), default 0
        expected.putByte((byte) 24);
        expected.putByte((byte) 0);
        expected.putByte((byte) 0x7f);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("AddEntity 应按 0.14.x 布局发送旧角度与元数据")
    void addEntityShouldUseClassic014Layout() {
        EntityMetadata metadata = new EntityMetadata()
                .putLong(Entity.DATA_FLAGS, 1L << Entity.DATA_FLAG_SPRINTING)
                .putShort(Entity.DATA_AIR, 300)
                .putString(Entity.DATA_NAMETAG, "");

        AddEntityPacket packet = new AddEntityPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.entityRuntimeId = 777L;
        packet.type = 10;
        packet.x = 1.25f;
        packet.y = 65.5f;
        packet.z = -3.75f;
        packet.speedX = 0.1f;
        packet.speedY = 0.2f;
        packet.speedZ = 0.3f;
        packet.yaw = 90f;
        packet.pitch = 45f;
        packet.metadata = metadata;
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putLong(777L);
        expected.putInt(10);
        expected.putFloat(1.25f);
        expected.putFloat(65.5f);
        expected.putFloat(-3.75f);
        expected.putFloat(0.1f);
        expected.putFloat(0.2f);
        expected.putFloat(0.3f);
        expected.putFloat(90f);
        expected.putFloat(45f);
        expected.putByte((byte) 0);
        expected.putByte((byte) (1 << Entity.DATA_FLAG_SPRINTING));
        expected.putByte((byte) ((1 << 5) | 1));
        expected.putLShort(300);
        putLegacyMetadataString(expected, 2, "");
        expected.putByte((byte) 3);
        expected.putByte((byte) 0);
        expected.putByte((byte) 4);
        expected.putByte((byte) 0);
        expected.putByte((byte) 15);
        expected.putByte((byte) 0);
        // key 23 = DATA_LEAD_HOLDER (long), default -1
        expected.putByte((byte) ((7 << 5) | 23));
        expected.putLLong(-1);
        // key 24 = DATA_LEAD (byte), default 0
        expected.putByte((byte) 24);
        expected.putByte((byte) 0);
        expected.putByte((byte) 0x7f);
        expected.putShort(0);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("AdventureSettings 应按 0.14.x 标志位编码")
    void adventureSettingsShouldUseClassic014Flags() {
        AdventureSettingsPacket packet = new AdventureSettingsPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.worldImmutable = true;
        packet.autoJump = true;
        packet.allowFlight = true;
        packet.noClip = true;
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(0x01 | 0x40 | 0x80 | 0x100);
        expected.putInt(2);
        expected.putInt(2);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("tryEncode 应避免重复编码并允许 clean 后重编码")
    void tryEncodeShouldAvoidDuplicateEncoding() {
        CountingPacket packet = new CountingPacket();

        packet.tryEncode();
        byte[] firstEncode = packet.getBuffer().clone();

        packet.tryEncode();
        byte[] secondCall = packet.getBuffer().clone();

        packet.clean();
        packet.tryEncode();
        byte[] reEncoded = packet.getBuffer().clone();

        assertAll(
                () -> assertEquals(2, packet.encodeCount),
                () -> assertArrayEquals(firstEncode, secondCall),
                () -> assertEquals(5, firstEncode.length),
                () -> assertEquals(5, reEncoded.length),
                () -> assertEquals(1, firstEncode[4]),
                () -> assertEquals(2, reEncoded[4])
        );
    }

    @Test
    @DisplayName("Login 应正确处理皮肤数据开头等于皮肤大小的情况")
    void loginShouldHandleSkinDataStartingWithSkinSize() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        skinData[0] = 0x00;
        skinData[1] = 0x00;
        skinData[2] = 0x20;
        skinData[3] = 0x00;
        Arrays.fill(skinData, 4, skinData.length, (byte) 5);

        BinaryStream input = new BinaryStream();
        putLegacyString(input, "LegacyPlayer");
        input.putInt(ProtocolInfo.v0_14_2);
        input.putInt(ProtocolInfo.v0_14_2);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        putLegacyString(input, "Standard_Custom");
        input.putShort(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_14_2;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Standard_Custom", packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    private static class CountingPacket extends DataPacket {

        private int encodeCount;

        @Override
        public byte pid() {
            return 0x7f;
        }

        @Override
        public void decode() {

        }

        @Override
        public void encode() {
            this.encodeCount++;
            this.reset();
            this.putInt(this.encodeCount);
        }
    }

    private static final class TestPlayer extends Player {
        private FullChunkDataPacket capturedChunkPacket;

        private TestPlayer() {
            super(null, 0L, "", 0);
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean batchDataPacket(DataPacket packet) {
            this.capturedChunkPacket = (FullChunkDataPacket) packet;
            return true;
        }
    }

    private static final class CapturingServerHandler extends ServerHandler {
        private String capturedIdentifier;
        private EncapsulatedPacket capturedPacket;

        private CapturingServerHandler() {
            super(null, null);
        }

        @Override
        public void sendEncapsulated(String identifier, EncapsulatedPacket packet, int flags) {
            this.capturedIdentifier = identifier;
            this.capturedPacket = packet;
        }
    }
}
