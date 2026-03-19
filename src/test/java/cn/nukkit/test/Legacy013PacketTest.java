package cn.nukkit.test;

import cn.nukkit.block.Block;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Legacy 0.13 Packet")
class Legacy013PacketTest {

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

    @Test
    @DisplayName("0.13.x 协议应共享经典包池")
    void protocol013FamilyShouldUseClassicPacketIds() {
        Network network = new Network(null);

        assertAll(
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_13_0)),
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_13_1)),
                () -> assertTrue(ProtocolInfo.isSupportedProtocol(ProtocolInfo.v0_13_2)),
                () -> assertEquals(ProtocolInfo.v0_13_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_13_0)),
                () -> assertEquals(ProtocolInfo.v0_13_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_13_1)),
                () -> assertEquals(ProtocolInfo.v0_13_2, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_13_2)),
                () -> assertEquals(0x8f, network.getPacketPool(ProtocolInfo.v0_13_0).getPacketId(LoginPacket.class)),
                () -> assertEquals(0x8f, network.getPacketPool(ProtocolInfo.v0_13_1).getPacketId(LoginPacket.class)),
                () -> assertEquals(0xc2, network.getPacketPool(ProtocolInfo.v0_13_2).getPacketId(SetPlayerGameTypePacket.class)),
                () -> assertNull(network.getPacketPool(ProtocolInfo.v0_13_2).getPacket(0xc8)),
                () -> assertEquals("0.13.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_13_0)),
                () -> assertEquals("0.13.1", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_13_1)),
                () -> assertEquals("0.13.2", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_13_2))
        );
    }

    @Test
    @DisplayName("0.13.x 应使用独立旧创造物品表")
    void creativeItemsShouldUse013SpecificDataSet() {
        assertAll(
                () -> assertEquals(241, Item.getCreativeItems(ProtocolInfo.v0_13_0).size()),
                () -> assertEquals(241, Item.getCreativeItems(ProtocolInfo.v0_13_2).size()),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_13_0, Item.get(Item.WOOL, 0)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_13_1, Item.get(Item.WOOL, 0)) != -1),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.BEETROOT_SEEDS, 0)) != -1),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.RED_SANDSTONE, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.ITEM_FRAME, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.RAW_RABBIT, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.RAW_MUTTON, 0)))
        );
    }

    @Test
    @DisplayName("isSupportedOn 应区分 0.13 已有物品与 0.14 新物品")
    void itemSupportShouldRespect013Thresholds() {
        assertAll(
                () -> assertTrue(Item.get(Item.STONE, 0).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertTrue(Item.get(Item.BEETROOT_SEEDS, 0).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertTrue(Item.get(Item.STONE, 0).isSupportedOn(ProtocolInfo.v0_13_2)),
                () -> assertTrue(Item.get(Item.BEETROOT_SEEDS, 0).isSupportedOn(ProtocolInfo.v0_13_2)),
                () -> assertFalse(Item.get(Item.SPRUCE_DOOR).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertFalse(Item.get(Item.RED_SANDSTONE, 0).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertFalse(Item.get(Item.ITEM_FRAME, 0).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertFalse(Item.get(Item.RAW_RABBIT, 0).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertFalse(Item.get(Item.RAW_MUTTON, 0).isSupportedOn(ProtocolInfo.v0_13_0)),
                () -> assertTrue(Item.get(Item.SPRUCE_DOOR).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertTrue(Item.get(Item.RED_SANDSTONE, 0).isSupportedOn(ProtocolInfo.v0_14_2)),
                () -> assertTrue(Item.get(Item.ITEM_FRAME, 0).isSupportedOn(ProtocolInfo.v0_14_2))
        );
    }

    @Test
    @DisplayName("ContainerSetContent 应按 0.13.x 旧物品布局编码")
    void containerSetContentShouldUseClassic013SlotLayout() {
        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v0_13_2;
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
    @DisplayName("0.13.x 创造背包容器应发送旧创造物品集")
    void creativeContainerShouldUse013CreativeItems() {
        Item[] creativeSlots = Item.getCreativeItems(ProtocolInfo.v0_13_2).toArray(new Item[0]);

        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v0_13_2;
        packet.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
        packet.slots = creativeSlots;
        packet.encode();

        ContainerSetContentPacket decoded = new ContainerSetContentPacket();
        decoded.protocol = ProtocolInfo.v0_13_2;
        decoded.setBuffer(packet.getBuffer(), 1);
        decoded.decode();

        assertAll(
                () -> assertEquals(ContainerSetContentPacket.SPECIAL_CREATIVE, decoded.windowid),
                () -> assertEquals(241, decoded.slots.length),
                () -> assertEquals(0, decoded.hotbar.length),
                () -> assertEquals(packet.getBuffer().length, decoded.getOffset()),
                () -> assertEquals(Item.COBBLESTONE, decoded.slots[0].getId()),
                () -> assertEquals(0, decoded.slots[0].getDamage()),
                () -> assertItemEquals(creativeSlots[120], decoded.slots[120]),
                () -> assertItemEquals(creativeSlots[creativeSlots.length - 1], decoded.slots[decoded.slots.length - 1]),
                () -> assertTrue(Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.BEETROOT_SEEDS, 0)) != -1),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.RED_SANDSTONE, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.ITEM_FRAME, 0))),
                () -> assertEquals(-1, Item.getCreativeItemIndex(ProtocolInfo.v0_13_2, Item.get(Item.RAW_RABBIT, 0)))
        );
    }

    @Test
    @DisplayName("0.13.x 普通背包容器应保留 hotbar 映射")
    void inventoryContainerShouldRetain013HotbarMapping() {
        ContainerSetContentPacket packet = new ContainerSetContentPacket();
        packet.protocol = ProtocolInfo.v0_13_2;
        packet.windowid = ContainerSetContentPacket.SPECIAL_INVENTORY;
        packet.slots = new Item[]{
                Item.get(Item.STONE, 0, 1),
                Item.get(Item.WOODEN_PICKAXE, 0, 1)
        };
        packet.hotbar = new int[]{0, 1, 0, 1, 0, 1, 0, 1, 0};
        packet.encode();

        ContainerSetContentPacket decoded = new ContainerSetContentPacket();
        decoded.protocol = ProtocolInfo.v0_13_2;
        decoded.setBuffer(packet.getBuffer(), 1);
        decoded.decode();

        assertAll(
                () -> assertEquals(ContainerSetContentPacket.SPECIAL_INVENTORY, decoded.windowid),
                () -> assertEquals(packet.getBuffer().length, decoded.getOffset()),
                () -> assertEquals(2, decoded.slots.length),
                () -> assertItemEquals(packet.slots[0], decoded.slots[0]),
                () -> assertItemEquals(packet.slots[1], decoded.slots[1]),
                () -> assertArrayEquals(packet.hotbar, decoded.hotbar)
        );
    }

    @Test
    @DisplayName("Login 应兼容 0.13.0 旧 slim/alpha 皮肤布局")
    void loginShouldDecodeClassic0130SkinFlagsLayout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 6);

        BinaryStream input = new BinaryStream();
        putLegacyString(input, "Legacy0130");
        input.putInt(ProtocolInfo.v0_13_0);
        input.putInt(ProtocolInfo.v0_13_0);
        input.putLong(987654321L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "legacy-secret");
        input.putBoolean(true);
        input.putByte((byte) 127);
        input.putShort(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_13_0;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Legacy0130", packet.username),
                () -> assertEquals(ProtocolInfo.v0_13_0, packet.getProtocol()),
                () -> assertEquals(987654321L, packet.clientId),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertEquals("legacy-secret", packet.clientSecret),
                () -> assertEquals(Skin.MODEL_ALEX, packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("Login 应按 0.13.x 旧布局解码")
    void loginShouldDecodeClassic013Layout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 5);

        BinaryStream input = new BinaryStream();
        putLegacyString(input, "Legacy013");
        input.putInt(ProtocolInfo.v0_13_2);
        input.putInt(ProtocolInfo.v0_13_2);
        input.putLong(123456789L);
        input.putUUID(uuid);
        putLegacyString(input, "127.0.0.1:19132");
        putLegacyString(input, "secret");
        putLegacyString(input, "Standard_Custom");
        input.putShort(skinData.length);
        input.put(skinData);

        LoginPacket packet = new LoginPacket();
        packet.protocol = ProtocolInfo.v0_13_2;
        packet.setBuffer(input.getBuffer(), 0);
        packet.decode();

        assertAll(
                () -> assertEquals("Legacy013", packet.username),
                () -> assertEquals(ProtocolInfo.v0_13_2, packet.getProtocol()),
                () -> assertEquals(123456789L, packet.clientId),
                () -> assertEquals(uuid, packet.clientUUID),
                () -> assertEquals("secret", packet.clientSecret),
                () -> assertEquals("Standard_Custom", packet.getSkin().getModel()),
                () -> assertArrayEquals(skinData, packet.getSkin().getData())
        );
    }

    @Test
    @DisplayName("PlayerList 应兼容 0.13.0 旧 slim/alpha 皮肤布局")
    void playerListShouldUseClassic0130SkinFlagsLayout() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        byte[] skinData = new byte[Skin.SINGLE_SKIN_SIZE];
        Arrays.fill(skinData, (byte) 7);

        PlayerListPacket packet = new PlayerListPacket();
        packet.protocol = ProtocolInfo.v0_13_0;
        packet.type = PlayerListPacket.TYPE_ADD;
        packet.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(uuid, 321L, "Legacy0130", new Skin(skinData, Skin.MODEL_ALEX))
        };
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putByte(PlayerListPacket.TYPE_ADD);
        expected.putInt(1);
        expected.putUUID(uuid);
        expected.putLong(321L);
        putLegacyString(expected, "Legacy0130");
        expected.putBoolean(true);
        expected.putByte((byte) 0);
        expected.putShort(skinData.length);
        expected.put(skinData);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("BatchPacket 应按 0.13.x 旧长度字段编解码")
    void batchShouldDecodeClassic013LengthPrefix() {
        byte[] payload = new byte[]{10, 20, 30};

        BinaryStream input = new BinaryStream();
        input.putInt(payload.length);
        input.put(payload);

        BatchPacket decoded = new BatchPacket();
        decoded.protocol = ProtocolInfo.v0_13_2;
        decoded.setBuffer(input.getBuffer(), 0);
        decoded.decode();

        BatchPacket encoded = new BatchPacket();
        encoded.protocol = ProtocolInfo.v0_13_1;
        encoded.payload = payload;
        encoded.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(payload.length);
        expected.put(payload);

        assertAll(
                () -> assertArrayEquals(payload, decoded.payload),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(encoded.getBuffer(), 1, encoded.getBuffer().length))
        );
    }

    @Test
    @DisplayName("StartGame 应使用 0.13.x 更早期布局")
    void startGameShouldUseClassic013Layout() {
        StartGamePacket packet = new StartGamePacket();
        packet.protocol = ProtocolInfo.v0_13_2;
        packet.seed = 123456789;
        packet.dimension = 1;
        packet.generator = 1;
        packet.gamemode = 0;
        packet.entityUniqueId = 42L;
        packet.spawnX = 100;
        packet.spawnY = 65;
        packet.spawnZ = -12;
        packet.x = 1.5f;
        packet.y = 64.0f;
        packet.z = -3.25f;
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
        expected.putByte((byte) 0);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("UseItem 应兼容 0.13.x 不带 slot 的旧布局")
    void useItemShouldDecodeClassic013LayoutWithoutSlot() {
        UseItemPacket input = new UseItemPacket();
        input.protocol = ProtocolInfo.v0_13_2;
        input.reset();
        input.putInt(1);
        input.putInt(64);
        input.putInt(-2);
        input.putByte((byte) 3);
        input.putFloat(0.1f);
        input.putFloat(0.2f);
        input.putFloat(0.3f);
        input.putFloat(10.0f);
        input.putFloat(65.0f);
        input.putFloat(-1.5f);
        input.putSlot(Item.get(Item.STONE, 0, 1));

        UseItemPacket packet = new UseItemPacket();
        packet.protocol = ProtocolInfo.v0_13_2;
        packet.setBuffer(input.getBuffer(), 1);
        packet.decode();

        assertAll(
                () -> assertEquals(1, packet.x),
                () -> assertEquals(64, packet.y),
                () -> assertEquals(-2, packet.z),
                () -> assertEquals(3, packet.face),
                () -> assertEquals(-1, packet.slot),
                () -> assertEquals(Item.STONE, packet.item.getId()),
                () -> assertEquals(1, packet.item.getCount())
        );
    }

    @Test
    @DisplayName("BlockEntityData 应按 0.13.x 坐标布局编解码")
    void blockEntityDataShouldUseClassic013Coords() {
        byte[] namedTag = new byte[]{10, 1, 2, 3};

        BlockEntityDataPacket packet = new BlockEntityDataPacket();
        packet.protocol = ProtocolInfo.v0_13_2;
        packet.x = 12;
        packet.y = 64;
        packet.z = -6;
        packet.namedTag = namedTag;
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(packet.x);
        expected.putByte((byte) packet.y);
        expected.putInt(packet.z);
        expected.put(namedTag);

        BlockEntityDataPacket decoded = new BlockEntityDataPacket();
        decoded.protocol = ProtocolInfo.v0_13_2;
        decoded.setBuffer(packet.getBuffer(), 1);
        decoded.decode();

        assertAll(
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length)),
                () -> assertEquals(12, decoded.x),
                () -> assertEquals(64, decoded.y),
                () -> assertEquals(-6, decoded.z),
                () -> assertArrayEquals(namedTag, decoded.namedTag)
        );
    }
}
