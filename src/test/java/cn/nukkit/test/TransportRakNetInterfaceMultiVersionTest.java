package cn.nukkit.test;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.network.Network;
import cn.nukkit.network.TransportRakNetInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayStatusPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transport RakNet Interface Multi Version")
class TransportRakNetInterfaceMultiVersionTest {

    private static void invokeHandleRakMessage(TransportRakNetInterface interfaz, String identifier, byte[] buffer) throws Exception {
        Method method = TransportRakNetInterface.class.getDeclaredMethod("handleRakMessage", String.class, byte[].class);
        method.setAccessible(true);
        method.invoke(interfaz, identifier, buffer);
    }

    private static void invokeNotifyPlayerAck(TransportRakNetInterface interfaz, String identifier, int ackId) throws Exception {
        Method method = TransportRakNetInterface.class.getDeclaredMethod("notifyPlayerAck", String.class, int.class);
        method.setAccessible(true);
        method.invoke(interfaz, identifier, ackId);
    }

    private static DataPacket invokeGetPacket(TransportRakNetInterface interfaz, Player player, byte[] buffer) throws Exception {
        Method method = TransportRakNetInterface.class.getDeclaredMethod("getPacket", Player.class, byte[].class);
        method.setAccessible(true);
        return (DataPacket) method.invoke(interfaz, player, buffer);
    }

    private static int packetId(Network network, int protocol, Class<? extends DataPacket> packetClass) {
        return network.getPacketPool(protocol).getPacketId(packetClass);
    }

    private static Map<String, Player> mapOf(String identifier, Player player) {
        ConcurrentHashMap<String, Player> map = new ConcurrentHashMap<>();
        map.put(identifier, player);
        return map;
    }

    private static CapturingPlayer newCapturingPlayer(int protocol, boolean useUncompressedBatch) throws Exception {
        Unsafe unsafe = getUnsafe();
        CapturingPlayer player = (CapturingPlayer) unsafe.allocateInstance(CapturingPlayer.class);

        Field protocolField = Player.class.getDeclaredField("protocol");
        protocolField.setAccessible(true);
        protocolField.setInt(player, protocol);

        player.useUncompressedBatch = useUncompressedBatch;
        player.capturedAck = -1;
        return player;
    }

    private static TransportRakNetInterface newUnsafeTransportRakNetInterface(Network network, Map<String, Player> players) throws Exception {
        Unsafe unsafe = getUnsafe();
        TransportRakNetInterface interfaz = (TransportRakNetInterface) unsafe.allocateInstance(TransportRakNetInterface.class);

        setField(TransportRakNetInterface.class, interfaz, "network", network);
        setField(TransportRakNetInterface.class, interfaz, "players", players);
        return interfaz;
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe getUnsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    @Test
    @DisplayName("Transport 层应正确解析 0.12 的 0xFE 入站包")
    void handleRakMessageShouldParseLegacy012FePacket() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v0_12_1, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(ProtocolInfo.v0_12_1, false);
            TransportRakNetInterface interfaz = newUnsafeTransportRakNetInterface(network, mapOf("legacy012", player));

            invokeHandleRakMessage(interfaz, "legacy012", new byte[]{(byte) 0xfe, (byte) packetId});

            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v0_12_1, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("Transport 层应在未知协议时将 0.14 的 0x8E 包回退到 0.14 协议池")
    void handleRakMessageShouldParseLegacy0148ePacketWhenProtocolUnknown() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v0_14_2, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(Integer.MAX_VALUE, false);
            TransportRakNetInterface interfaz = newUnsafeTransportRakNetInterface(network, mapOf("legacy014", player));

            invokeHandleRakMessage(interfaz, "legacy014", new byte[]{(byte) 0x8e, (byte) packetId});

            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v0_14_2, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("Transport 层应按 1.0+ 未压缩格式解析 0xFE 入站包")
    void handleRakMessageShouldParseModernUncompressedFePacket() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v1_0_0_0, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(ProtocolInfo.v1_0_0_0, true);
            TransportRakNetInterface interfaz = newUnsafeTransportRakNetInterface(network, mapOf("modern100", player));

            invokeHandleRakMessage(interfaz, "modern100", new byte[]{(byte) 0xfe, (byte) packetId});

            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v1_0_0_0, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("Transport 层未知协议下的 0xFE+zlib 入站包应回落为 BatchPacket")
    void getPacketShouldTreatFeZlibPayloadAsBatchPacketWhenProtocolUnknown() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            CapturingPlayer player = newCapturingPlayer(Integer.MAX_VALUE, false);
            TransportRakNetInterface interfaz = newUnsafeTransportRakNetInterface(network, mapOf("zlib-unknown", player));

            byte[] compressedPayload = Zlib.deflate(new byte[]{0x01, 0x05, 0x06, 0x07, 0x08});
            byte[] incoming = Binary.appendBytes((byte) 0xfe, compressedPayload);

            DataPacket packet = invokeGetPacket(interfaz, player, incoming);
            assertNotNull(packet);
            assertTrue(packet instanceof BatchPacket);
            assertEquals(Integer.MAX_VALUE, packet.protocol);

            packet.decode();
            assertArrayEquals(compressedPayload, ((BatchPacket) packet).payload);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("Transport 层 notifyPlayerAck 应只通知对应连接玩家")
    void notifyPlayerAckShouldNotifyMatchedPlayerOnly() throws Exception {
        Network network = new Network(null);
        CapturingPlayer player = newCapturingPlayer(ProtocolInfo.v1_1_0, true);
        TransportRakNetInterface interfaz = newUnsafeTransportRakNetInterface(network, mapOf("peer-ack", player));

        invokeNotifyPlayerAck(interfaz, "peer-ack", 77);
        assertEquals(77, player.capturedAck);

        invokeNotifyPlayerAck(interfaz, "unknown-peer", 99);
        assertEquals(77, player.capturedAck);
    }

    private static final class CapturingPlayer extends Player {
        private DataPacket capturedPacket;
        private int capturedAck;

        private CapturingPlayer() {
            super(null, 0L, "", 0);
            throw new UnsupportedOperationException();
        }

        @Override
        public void handleDataPacket(DataPacket packet) {
            this.capturedPacket = packet;
        }

        @Override
        public void notifyACK(int identification) {
            this.capturedAck = identification;
        }
    }
}
