package cn.nukkit.test;

import cn.nukkit.raknet.protocol.EncapsulatedPacket;
import cn.nukkit.raknet.server.Session;
import cn.nukkit.raknet.server.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RakNet Compatibility Guard")
class RakNetCompatibilityGuardTest {

    private static boolean invokeSessionBoolean(String methodName, int value) throws Exception {
        Method method = Session.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, value);
    }

    private static boolean invokeSessionManagerBoolean(String methodName, byte value) throws Exception {
        Method method = SessionManager.class.getDeclaredMethod(methodName, byte.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, value);
    }

    private static boolean invokeSplitValidation(EncapsulatedPacket packet) throws Exception {
        Method method = Session.class.getDeclaredMethod("isValidSplitPacket", EncapsulatedPacket.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, packet);
    }

    @Test
    @DisplayName("仅 0x80 到 0x8f 应被识别为 RakNet 数据报")
    void shouldOnlyTreatExpectedRangeAsDataPackets() throws Exception {
        assertFalse(invokeSessionBoolean("isDataPacketId", 0x7f));
        assertTrue(invokeSessionBoolean("isDataPacketId", 0x80));
        assertTrue(invokeSessionBoolean("isDataPacketId", 0x8f));
        assertFalse(invokeSessionBoolean("isDataPacketId", 0x90));
    }

    @Test
    @DisplayName("仅 0x01 到 0x7f 应被识别为离线握手包")
    void shouldOnlyTreatExpectedRangeAsOfflinePackets() throws Exception {
        assertFalse(invokeSessionBoolean("isOfflinePacketId", 0x00));
        assertTrue(invokeSessionBoolean("isOfflinePacketId", 0x01));
        assertTrue(invokeSessionBoolean("isOfflinePacketId", 0x7f));
        assertFalse(invokeSessionBoolean("isOfflinePacketId", 0x80));
    }

    @Test
    @DisplayName("应同时接受 0x01 和 0x02 作为未连接探测包")
    void shouldTreatBothDiscoveryPingIdsAsUnconnectedPing() throws Exception {
        assertTrue(invokeSessionManagerBoolean("isUnconnectedPing", (byte) 0x01));
        assertTrue(invokeSessionManagerBoolean("isUnconnectedPing", (byte) 0x02));
        assertFalse(invokeSessionManagerBoolean("isUnconnectedPing", (byte) 0x05));
    }

    @Test
    @DisplayName("应拒绝超出 splitCount 范围的分片索引")
    void shouldRejectOutOfRangeSplitFragments() throws Exception {
        EncapsulatedPacket packet = new EncapsulatedPacket();
        packet.splitCount = 2;
        packet.splitIndex = 2;
        assertFalse(invokeSplitValidation(packet));

        packet.splitIndex = 1;
        assertTrue(invokeSplitValidation(packet));

        packet.splitCount = 0;
        packet.splitIndex = 0;
        assertFalse(invokeSplitValidation(packet));

        packet.splitCount = Session.MAX_SPLIT_SIZE + 1;
        packet.splitIndex = 0;
        assertFalse(invokeSplitValidation(packet));
    }
}
