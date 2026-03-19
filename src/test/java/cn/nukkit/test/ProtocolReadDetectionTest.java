package cn.nukkit.test;

import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Protocol Read Detection")
class ProtocolReadDetectionTest {

    private static int detectProtocol(byte[] batchData) throws Exception {
        Network network = new Network(null);
        Method detectMethod = Network.class.getDeclaredMethod("detectProtocolFromBatchData", byte[].class, int.class);
        detectMethod.setAccessible(true);
        return (int) detectMethod.invoke(network, batchData, batchData.length);
    }

    private static int consumeOptionalPacketCount(byte[] batchData, int protocol) throws Exception {
        Network network = new Network(null);
        Method consumeMethod = Network.class.getDeclaredMethod("consumeOptionalPacketCount", BinaryStream.class, int.class, int.class);
        consumeMethod.setAccessible(true);
        BinaryStream stream = new BinaryStream(batchData);
        consumeMethod.invoke(network, stream, batchData.length, protocol);
        return stream.getOffset();
    }

    private static void putLegacyString(BinaryStream stream, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        stream.putShort(bytes.length);
        stream.put(bytes);
    }

    @Test
    @DisplayName("应从 0.13.x 经典批量包登录中识别协议")
    void shouldDetect013ProtocolFromClassicBatchLogin() throws Exception {
        BinaryStream login = new BinaryStream();
        login.putByte((byte) 0x8f);
        putLegacyString(login, "Detect013");
        login.putInt(ProtocolInfo.v0_13_2);

        BinaryStream batch = new BinaryStream();
        batch.putInt(login.getBuffer().length);
        batch.put(login.getBuffer());

        assertEquals(ProtocolInfo.v0_13_2, detectProtocol(batch.getBuffer()));
    }

    @Test
    @DisplayName("应从 0.14.x 经典批量包登录中识别协议")
    void shouldDetect014ProtocolFromClassicBatchLogin() throws Exception {
        BinaryStream login = new BinaryStream();
        login.putByte((byte) 0x8e);
        login.putByte((byte) 0x8f);
        putLegacyString(login, "Detect014");
        login.putInt(ProtocolInfo.v0_14_3);

        BinaryStream batch = new BinaryStream();
        batch.putInt(login.getBuffer().length);
        batch.put(login.getBuffer());

        assertEquals(ProtocolInfo.v0_14_3, detectProtocol(batch.getBuffer()));
    }

    @Test
    @DisplayName("应从 0.15.x 批量包登录中识别协议")
    void shouldDetect015ProtocolFromBatchLogin() throws Exception {
        byte[] login = new byte[]{
                ProtocolInfo.LOGIN_PACKET,
                0, 0, 0, (byte) ProtocolInfo.v0_15_10
        };

        BinaryStream batch = new BinaryStream();
        batch.putInt(login.length);
        batch.put(login);

        assertEquals(ProtocolInfo.v0_15_10, detectProtocol(batch.getBuffer()));
    }

    @Test
    @DisplayName("应从 1.0.0.0 批量包登录中识别协议")
    void shouldDetect1000ProtocolFromBatchLogin() throws Exception {
        byte[] login = new byte[]{
                ProtocolInfo.LOGIN_PACKET,
                0, 0, 0, (byte) ProtocolInfo.v1_0_0_0
        };

        byte[] batch = Binary.appendBytes(Binary.writeUnsignedVarInt(login.length), login);
        assertEquals(ProtocolInfo.v1_0_0_0, detectProtocol(batch));
    }

    @Test
    @DisplayName("应从 1.1.0 批量包登录中识别协议")
    void shouldDetect110ProtocolFromBatchLogin() throws Exception {
        byte[] login = new byte[]{
                ProtocolInfo.LOGIN_PACKET,
                0, 0, 0, (byte) ProtocolInfo.v1_1_0
        };

        byte[] batch = Binary.appendBytes(Binary.writeUnsignedVarInt(login.length), login);
        assertEquals(ProtocolInfo.v1_1_0, detectProtocol(batch));
    }

    @Test
    @DisplayName("应从 1.1.2 批量包登录中识别协议")
    void shouldDetect112ProtocolFromBatchLogin() throws Exception {
        byte[] login = new byte[]{
                ProtocolInfo.LOGIN_PACKET,
                0, 0, 0, (byte) ProtocolInfo.v1_1_2
        };

        byte[] batch = Binary.appendBytes(Binary.writeUnsignedVarInt(login.length), login);
        assertEquals(ProtocolInfo.v1_1_2, detectProtocol(batch));
    }

    @Test
    @DisplayName("应从 1.1 复合 VarInt 头登录包中识别协议")
    void shouldDetect110ProtocolFromCompositeHeaderLogin() throws Exception {
        byte[] header = Binary.writeUnsignedVarInt((1L << 10) | (ProtocolInfo.LOGIN_PACKET & 0xff));
        byte[] login = Binary.appendBytes(
                header,
                new byte[]{0, 0, 0, (byte) ProtocolInfo.v1_1_0}
        );

        byte[] batch = Binary.appendBytes(Binary.writeUnsignedVarInt(login.length), login);
        assertEquals(ProtocolInfo.v1_1_0, detectProtocol(batch));
    }

    @Test
    @DisplayName("1.1 无 packetCount 批量包不应误消费首个长度字段")
    void shouldNotConsumeLengthPrefixAsPacketCountFor110Batch() throws Exception {
        byte[] login = new byte[]{
                ProtocolInfo.LOGIN_PACKET,
                0, 0, 0, (byte) ProtocolInfo.v1_1_0
        };
        byte[] batch = Binary.appendBytes(Binary.writeUnsignedVarInt(login.length), login);

        assertEquals(0, consumeOptionalPacketCount(batch, ProtocolInfo.v1_1_1));
    }

    @Test
    @DisplayName("带 packetCount 的批量包应正确消费计数字段")
    void shouldConsumePacketCountPrefixWhenPresent() throws Exception {
        byte[] login = new byte[]{
                ProtocolInfo.LOGIN_PACKET,
                0, 0, 0, (byte) ProtocolInfo.v1_1_0
        };
        byte[] noCountBatch = Binary.appendBytes(Binary.writeUnsignedVarInt(login.length), login);
        byte[] countBatch = Binary.appendBytes(Binary.writeUnsignedVarInt(1), noCountBatch);

        assertEquals(1, consumeOptionalPacketCount(countBatch, ProtocolInfo.v1_1_1));
    }
}
