package cn.nukkit.test;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Legacy 0.12 Packet")
class Legacy012PacketTest {

    @BeforeAll
    static void initItems() {
        if (Block.list == null) {
            Block.init();
        }
        if (Item.list == null) {
            Item.init();
        }
    }

    private static void putLegacyString(BinaryStream stream, String value) {
        putLegacyBytes(stream, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void putLegacyBytes(BinaryStream stream, byte[] value) {
        stream.putShort(value.length);
        stream.put(value);
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

    private static Level newLevelWithChunk(int chunkX, int chunkZ, cn.nukkit.level.format.FullChunk chunk) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Level level = (Level) unsafe.allocateInstance(Level.class);

        Field chunksField = Level.class.getDeclaredField("chunks");
        chunksField.setAccessible(true);
        HashMap<Long, cn.nukkit.level.format.generic.BaseFullChunk> chunks = new HashMap<>();
        chunks.put(Level.chunkHash(chunkX, chunkZ), (cn.nukkit.level.format.generic.BaseFullChunk) chunk);
        chunksField.set(level, chunks);

        return level;
    }

    private static TestPlayer newTestPlayer(Level level, int protocol) throws Exception {
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
        usedChunksField.set(player, new HashMap<Long, Boolean>());

        Field protocolField = Player.class.getDeclaredField("protocol");
        protocolField.setAccessible(true);
        protocolField.setInt(player, protocol);

        return player;
    }

    @Test
    @DisplayName("0.12.x 协议应共享最早期经典包池")
    void protocol012FamilyShouldUseClassicPacketIds() {
        Network network = new Network(null);

        assertAll(
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_12_0)),
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_12_1)),
                () -> assertEquals(ProtocolInfo.v0_12_1, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_12_0)),
                () -> assertEquals(ProtocolInfo.v0_12_1, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_12_1)),
                () -> assertEquals(0x8f, network.getPacketPool(ProtocolInfo.v0_12_0).getPacketId(LoginPacket.class)),
                () -> assertEquals(0x92, network.getPacketPool(ProtocolInfo.v0_12_1).getPacketId(BatchPacket.class)),
                () -> assertEquals(0xc3, network.getPacketPool(ProtocolInfo.v0_12_1).getPacketId(PlayerListPacket.class)),
                () -> assertNull(network.getPacketPool(ProtocolInfo.v0_12_1).getPacket(0xbe)),
                () -> assertNull(network.getPacketPool(ProtocolInfo.v0_12_1).getPacket(0xc1)),
                () -> assertNull(network.getPacketPool(ProtocolInfo.v0_12_1).getPacket(0xc2)),
                () -> assertNull(network.getPacketPool(ProtocolInfo.v0_12_1).getPacket(0xc5)),
                () -> assertEquals("0.12.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_12_0)),
                () -> assertEquals("0.12.3", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_12_1))
        );
    }

    @Test
    @DisplayName("0.12.x 创造物品应复用旧经典数据集")
    void creativeItemsShouldReuseClassicDataSet() {
        assertAll(
                () -> assertEquals(241, Item.getCreativeItems(ProtocolInfo.v0_12_0).size()),
                () -> assertEquals(241, Item.getCreativeItems(ProtocolInfo.v0_12_1).size()),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_12_1, Item.get(Item.WOOL, 0)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_12_1, Item.get(Item.BEETROOT_SEEDS, 0)) != -1),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_12_1, Item.get(Item.RED_SANDSTONE, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_12_1, Item.get(Item.ITEM_FRAME, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_12_1, Item.get(Item.RAW_RABBIT, 0)))
        );
    }

    @Test
    @DisplayName("isSupportedOn 应区分 0.12 已有物品与 0.14 新物品")
    void itemSupportShouldRespect012Thresholds() {
        assertAll(
                () -> assertTrue(Item.get(Item.STONE, 0).isSupportedOn(ProtocolInfo.v0_12_0)),
                () -> assertTrue(Item.get(Item.BEETROOT_SEEDS, 0).isSupportedOn(ProtocolInfo.v0_12_1)),
                () -> assertFalse(Item.get(Item.RED_SANDSTONE, 0).isSupportedOn(ProtocolInfo.v0_12_1)),
                () -> assertFalse(Item.get(Item.ITEM_FRAME, 0).isSupportedOn(ProtocolInfo.v0_12_1)),
                () -> assertFalse(Item.get(Item.RAW_RABBIT, 0).isSupportedOn(ProtocolInfo.v0_12_1)),
                () -> assertTrue(Item.get(Item.RED_SANDSTONE, 0).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertTrue(Item.get(Item.ITEM_FRAME, 0).isSupportedOn(ProtocolInfo.v0_14_2))
        );
    }

    @Test
    @DisplayName("Login 应按 0.12.x slim + skin string 布局解码 Base64 皮肤")
    void loginShouldDecodeClassic012Base64SkinStringLayout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 4);
        byte[] encodedSkin = Base64.getEncoder().encode(skinData);

        BinaryStream input = new BinaryStream();
        putLegacyString(input, "Legacy012");
        input.putInt(ProtocolInfo.v0_12_1);
        input.putInt(ProtocolInfo.v0_12_1);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        input.putByte((byte) 1);
        putLegacyBytes(input, encodedSkin);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_12_1;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Legacy012", packet.username),
                () -> assertEquals(ProtocolInfo.v0_12_1, packet.getProtocol()),
                () -> assertEquals(123456789L, packet.clientId),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertEquals("secret", packet.clientSecret),
                () -> assertEquals(Skin.MODEL_ALEX, packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("Login 应兼容 0.12.x 直接二进制皮肤字符串")
    void loginShouldDecodeClassic012RawSkinStringLayout() {
        UUID uuid = UUID.fromString("87654321-4321-8765-4321-876543218765");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 2);

        BinaryStream input = new BinaryStream();
        putLegacyString(input, "Legacy012Raw");
        input.putInt(ProtocolInfo.v0_12_0);
        input.putInt(ProtocolInfo.v0_12_0);
        input.putLong(987654321L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "raw-secret");
        input.putByte((byte) 0);
        putLegacyBytes(input, skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_12_0;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Legacy012Raw", packet.username),
                () -> assertEquals(ProtocolInfo.v0_12_0, packet.getProtocol()),
                () -> assertEquals(987654321L, packet.clientId),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertEquals("raw-secret", packet.clientSecret),
                () -> assertEquals(Skin.MODEL_STEVE, packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("Login 应兼容 0.12.x skinName + skin 字符串布局")
    void loginShouldDecodeClassic012NamedSkinStringLayout() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 6);

        BinaryStream input = new BinaryStream();
        putLegacyString(input, "Legacy012Named");
        input.putInt(ProtocolInfo.v0_12_1);
        input.putInt(ProtocolInfo.v0_12_1);
        input.putLong(19260817L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "named-secret");
        putLegacyString(input, Skin.MODEL_ALEX);
        putLegacyString(input, Base64.getEncoder().encodeToString(skinData));

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_12_1;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Legacy012Named", packet.username),
                () -> assertEquals(ProtocolInfo.v0_12_1, packet.getProtocol()),
                () -> assertEquals(19260817L, packet.clientId),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertEquals("named-secret", packet.clientSecret),
                () -> assertEquals(Skin.MODEL_ALEX, packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("PlayerList 应按 0.12.x slim + skin string 布局编码")
    void playerListShouldUseClassic012SkinStringLayout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 7);

        PlayerListPacket packet = new PlayerListPacket();
        packet.protocol = ProtocolInfo.v0_12_1;
        packet.type = PlayerListPacket.TYPE_ADD;
        packet.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(uuid, 321L, "Legacy012", new Skin(skinData, Skin.MODEL_ALEX))
        };
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(PlayerListPacket.TYPE_ADD);
        expected.putInt(1);
        expected.putUUID(uuid);
        expected.putLong(321L);
        putLegacyString(expected, "Legacy012");
        expected.putByte((byte) 1);
        putLegacyString(expected, Base64.getEncoder().encodeToString(skinData));

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("BatchPacket 应按 0.12.x 外层整型长度字段解码")
    void batchShouldDecodeClassic012LengthPrefix() {
        byte[] payload = new byte[]{10, 20, 30};

        BinaryStream input = new BinaryStream();
        input.putInt(payload.length);
        input.put(payload);

        BatchPacket decoded = new BatchPacket();
        decoded.protocol = ProtocolInfo.v0_12_1;
        decoded.setBuffer(input.getBuffer(), 0);
        decoded.decode();

        BatchPacket detected = new BatchPacket();
        detected.protocol = Integer.MAX_VALUE;
        detected.setBuffer(input.getBuffer(), 0);
        detected.decode();

        assertAll(
                () -> assertArrayEquals(payload, decoded.payload),
                () -> assertArrayEquals(payload, detected.payload)
        );
    }

    @Test
    @DisplayName("批量包预检测应识别 0.12.x 原始登录数据")
    void batchDataDetectionShouldIdentifyClassic012Login() throws Exception {
        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0x8f);
        putLegacyString(input, "Detect012");
        input.putInt(ProtocolInfo.v0_12_1);
        input.putInt(ProtocolInfo.v0_12_1);
        input.putLong(1L);
        input.putUUID(UUID.fromString("12345678-1234-5678-1234-567812345678"));
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        input.putByte((byte) 0);
        putLegacyBytes(input, new byte[Skin.SINGLE_SKIN_SIZE]);

        Network network = new Network(null);
        Method detectMethod = Network.class.getDeclaredMethod("detectProtocolFromBatchData", byte[].class, int.class);
        detectMethod.setAccessible(true);
        int detectedProtocol = (int) detectMethod.invoke(network, input.getBuffer(), input.getBuffer().length);

        assertEquals(ProtocolInfo.v0_12_1, detectedProtocol);
    }

    @Test
    @DisplayName("批量包预检测应识别带内部长度前缀的 0.12.x 登录数据")
    void batchDataDetectionShouldIdentifyLengthPrefixedClassic012Login() throws Exception {
        BinaryStream login = new BinaryStream();
        login.putByte((byte) 0x8f);
        putLegacyString(login, "Detect012Len");
        login.putInt(ProtocolInfo.v0_12_1);
        login.putInt(ProtocolInfo.v0_12_1);
        login.putLong(1L);
        login.putUUID(UUID.fromString("12345678-1234-5678-1234-567812345678"));
        putLegacyString(login, "127.0.0.1:19132");
        putLegacyString(login, "secret");
        login.putByte((byte) 0);
        putLegacyBytes(login, new byte[Skin.SINGLE_SKIN_SIZE]);

        BinaryStream input = new BinaryStream();
        input.putInt(login.getBuffer().length);
        input.put(login.getBuffer());

        Network network = new Network(null);
        Method detectMethod = Network.class.getDeclaredMethod("detectProtocolFromBatchData", byte[].class, int.class);
        detectMethod.setAccessible(true);
        int detectedProtocol = (int) detectMethod.invoke(network, input.getBuffer(), input.getBuffer().length);

        assertEquals(ProtocolInfo.v0_12_1, detectedProtocol);
    }

    @Test
    @DisplayName("0.12.x 批量包应识别内部长度前缀形态")
    void legacy012BatchEncodingDetectionShouldRecognizeLengthPrefixedLayout() throws Exception {
        BinaryStream login = new BinaryStream();
        login.putByte((byte) 0x8f);
        putLegacyString(login, "Detect012LenMode");
        login.putInt(ProtocolInfo.v0_12_1);

        BinaryStream framed = new BinaryStream();
        framed.putInt(login.getBuffer().length);
        framed.put(login.getBuffer());

        Network network = new Network(null);
        Method detectMethod = Network.class.getDeclaredMethod("usesLengthPrefixedLegacy012Batch", byte[].class, int.class, int.class);
        detectMethod.setAccessible(true);

        boolean rawDetected = (boolean) detectMethod.invoke(network, login.getBuffer(), login.getBuffer().length, ProtocolInfo.v0_12_1);
        boolean framedDetected = (boolean) detectMethod.invoke(network, framed.getBuffer(), framed.getBuffer().length, ProtocolInfo.v0_12_1);

        assertAll(
                () -> assertFalse(rawDetected),
                () -> assertTrue(framedDetected)
        );
    }

    @Test
    @DisplayName("0.12.x 出站批量包应支持长度前缀布局")
    void outgoingLegacy012BatchShouldSupportLengthPrefixedLayout() throws Exception {
        FullChunkDataPacket packet = new FullChunkDataPacket();
        packet.chunkX = 1;
        packet.chunkZ = 2;
        packet.order = FullChunkDataPacket.ORDER_LAYERED;
        packet.data = new byte[]{1, 2, 3, 4};

        byte[] encoded = encodeBatchPackets(ProtocolInfo.v0_12_1, true, packet);
        int packetLength = Binary.readInt(Arrays.copyOfRange(encoded, 0, 4));
        Network network = new Network(null);

        assertAll(
                () -> assertEquals(encoded.length - 4, packetLength),
                () -> assertEquals(0xbf, network.getPacketPool(ProtocolInfo.v0_12_1).getPacketId(FullChunkDataPacket.class))
        );
    }

    @Test
    @DisplayName("旧区块序列化应兼容 LevelDB 世界的 0.12 layered 布局")
    void legacyChunkPayloadShouldConvertLevelDbChunkToLayeredLayout() throws Exception {
        cn.nukkit.level.format.leveldb.Chunk chunk = cn.nukkit.level.format.leveldb.Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(0, 3, 3, Block.STONE);

        Level level = newLevelWithChunk(0, 0, chunk);
        byte[] payload = level.buildLegacyChunkPayload(0, 0);

        int blockIndex = (3 << 8) | (3 << 4);

        assertNotNull(payload);
        assertAll(
                () -> assertEquals(83204, payload.length),
                () -> assertEquals(Block.STONE, payload[blockIndex] & 0xff)
        );
    }

    @Test
    @DisplayName("0.12.x 列式旧区块序列化应兼容 anvil 世界")
    void legacyColumnChunkPayloadShouldConvertAnvilChunkToClassic012Layout() throws Exception {
        cn.nukkit.level.format.anvil.Chunk chunk = cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(1, 2, 3, Block.STONE);

        Level level = newLevelWithChunk(0, 0, chunk);
        byte[] payload = level.buildLegacyColumnChunkPayload(0, 0);

        int blockIndex = ((((1 << 4) | 3) << 7) | 2);

        assertNotNull(payload);
        assertAll(
                () -> assertEquals(83204, payload.length),
                () -> assertEquals(Block.STONE, payload[blockIndex] & 0xff)
        );
    }

    @Test
    @DisplayName("0.12.x 发送区块时应改用列式旧区块顺序")
    void sendChunkShouldUseColumnOrderingFor012() throws Exception {
        cn.nukkit.level.format.anvil.Chunk chunk = cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(0, 0);
        chunk.setBlockId(1, 2, 3, Block.STONE);

        Level level = newLevelWithChunk(0, 0, chunk);
        TestPlayer player = newTestPlayer(level, ProtocolInfo.v0_12_1);

        player.sendChunk(0, 0, new byte[]{9, 9, 9}, FullChunkDataPacket.ORDER_LAYERED);

        assertNotNull(player.capturedChunkPacket);
        int blockIndex = ((((1 << 4) | 3) << 7) | 2);
        assertAll(
                () -> assertEquals(FullChunkDataPacket.ORDER_COLUMNS, player.capturedChunkPacket.order),
                () -> assertEquals(83204, player.capturedChunkPacket.data.length),
                () -> assertEquals(Block.STONE, player.capturedChunkPacket.data[blockIndex] & 0xff)
        );
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
}
