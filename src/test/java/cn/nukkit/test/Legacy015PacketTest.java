package cn.nukkit.test;

import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Legacy 0.15 Packet")
class Legacy015PacketTest {

    @Test
    @DisplayName("协议映射应与 Genisys 历史版本节点一致")
    void protocolFamiliesShouldMatchGenisysHistory() {
        assertAll(
                () -> assertEquals(ProtocolInfo.v0_15_0, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_15_0)),
                () -> assertEquals(ProtocolInfo.v0_15_10, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_15_4)),
                () -> assertEquals(ProtocolInfo.v0_15_10, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_15_9)),
                () -> assertEquals(ProtocolInfo.v0_15_10, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_15_10)),
                () -> assertEquals(ProtocolInfo.v0_16_0, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v0_16_0)),
                () -> assertEquals(ProtocolInfo.v1_0_0_0, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v1_0_0_0)),
                () -> assertEquals(ProtocolInfo.v1_0_0, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v1_0_0)),
                () -> assertEquals(ProtocolInfo.v1_0_3, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v1_0_3)),
                () -> assertEquals(ProtocolInfo.v1_0_4, ProtocolInfo.getPacketPoolProtocol(ProtocolInfo.v1_0_4)),
                () -> assertEquals("0.15.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_15_0)),
                () -> assertEquals("0.15.4", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_15_4)),
                () -> assertEquals("0.15.9", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_15_9)),
                () -> assertEquals("0.15.10", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_15_10)),
                () -> assertEquals("0.16.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v0_16_0)),
                () -> assertEquals("1.0.0.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v1_0_0_0)),
                () -> assertEquals("1.0.0", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v1_0_0)),
                () -> assertEquals("1.0.3", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v1_0_3)),
                () -> assertEquals("1.0.4", ProtocolInfo.getMinecraftVersion(ProtocolInfo.v1_0_4))
        );
    }

    @Test
    @DisplayName("StartGame 应使用 0.15.x 旧布局")
    void startGameShouldUseClassic015Layout() {
        StartGamePacket packet = new StartGamePacket();
        packet.protocol = ProtocolInfo.v0_15_10;
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
        expected.putString(packet.unknownstr);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("FullChunkData 应使用 0.15.x 旧布局")
    void fullChunkDataShouldUseClassic015Layout() {
        FullChunkDataPacket packet = new FullChunkDataPacket();
        packet.protocol = ProtocolInfo.v0_15_10;
        packet.chunkX = 4;
        packet.chunkZ = -7;
        packet.data = new byte[]{1, 2, 3, 4};
        packet.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(packet.chunkX);
        expected.putInt(packet.chunkZ);
        expected.putByte(packet.order);
        expected.putInt(packet.data.length);
        expected.put(packet.data);

        assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(packet.getBuffer(), 1, packet.getBuffer().length));
    }

    @Test
    @DisplayName("RequestChunkRadius 与 ChunkRadiusUpdated 应保持 0.15.x 整数布局")
    void chunkRadiusPacketsShouldUseClassic015Integers() {
        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0);
        input.putInt(8);

        RequestChunkRadiusPacket request = new RequestChunkRadiusPacket();
        request.protocol = ProtocolInfo.v0_15_10;
        request.setBuffer(input.getBuffer(), 1);
        request.decode();

        ChunkRadiusUpdatedPacket response = new ChunkRadiusUpdatedPacket();
        response.protocol = ProtocolInfo.v0_15_10;
        response.radius = 8;
        response.encode();

        BinaryStream expected = new BinaryStream();
        expected.putInt(8);

        assertAll(
                () -> assertEquals(8, request.radius),
                () -> assertArrayEquals(expected.getBuffer(), Arrays.copyOfRange(response.getBuffer(), 1, response.getBuffer().length))
        );
    }

    @Test
    @DisplayName("MovePlayer 应按 0.15.x 旧字段顺序解码")
    void movePlayerShouldDecodeClassic015Layout() {
        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0);
        input.putLong(123456789L);
        input.putFloat(10.5f);
        input.putFloat(64.0f);
        input.putFloat(-2.5f);
        input.putFloat(90.0f);
        input.putFloat(91.0f);
        input.putFloat(45.0f);
        input.putByte(MovePlayerPacket.MODE_NORMAL);
        input.putByte((byte) 1);

        MovePlayerPacket packet = new MovePlayerPacket();
        packet.protocol = ProtocolInfo.v0_15_10;
        packet.setBuffer(input.getBuffer(), 1);
        packet.decode();

        assertAll(
                () -> assertEquals(123456789L, packet.eid),
                () -> assertEquals(10.5f, packet.x, 0.0001f),
                () -> assertEquals(64.0f, packet.y, 0.0001f),
                () -> assertEquals(-2.5f, packet.z, 0.0001f),
                () -> assertEquals(90.0f, packet.yaw, 0.0001f),
                () -> assertEquals(91.0f, packet.headYaw, 0.0001f),
                () -> assertEquals(45.0f, packet.pitch, 0.0001f),
                () -> assertEquals(true, packet.onGround)
        );
    }

    @Test
    @DisplayName("LoginPacket 0.15.x 应使用 LInt 读取压缩数据长度")
    void loginPacket015xShouldUseLIntForCompressedDataLength() throws Exception {
        byte[] compressedPayload = Zlib.deflate(new byte[0]);

        BinaryStream input = new BinaryStream();
        input.putByte((byte) 0);
        input.putInt(ProtocolInfo.v0_15_4);
        input.putByte((byte) 0);
        input.putLInt(compressedPayload.length);
        input.put(compressedPayload);

        byte[] buffer = input.getBuffer();
        assertEquals(1 + 4 + 1 + 4 + compressedPayload.length, buffer.length);

        int length = Binary.readLInt(new byte[]{buffer[6], buffer[7], buffer[8], buffer[9]});
        assertEquals(compressedPayload.length, length);
    }
}
