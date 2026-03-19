package cn.nukkit.test;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayStatusPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.raknet.RakNet;
import cn.nukkit.raknet.protocol.EncapsulatedPacket;
import cn.nukkit.raknet.server.RakNetServer;
import cn.nukkit.raknet.server.ServerHandler;
import cn.nukkit.raknet.server.ServerInstance;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RakNet Interface Multi Version")
class RakNetInterfaceMultiVersionTest {

    private static byte[] buildEncapsulatedFrame(String identifier, int flags, byte[] payload) {
        EncapsulatedPacket encapsulatedPacket = new EncapsulatedPacket();
        encapsulatedPacket.reliability = 2;
        encapsulatedPacket.buffer = payload;
        return Binary.appendBytes(
                RakNet.PACKET_ENCAPSULATED,
                new byte[]{(byte) (identifier.length() & 0xff)},
                identifier.getBytes(StandardCharsets.UTF_8),
                new byte[]{(byte) (flags & 0xff)},
                encapsulatedPacket.toBinary(true)
        );
    }

    private static DataPacket invokeGetPacket(RakNetInterface interfaz, Player player, byte[] buffer) throws Exception {
        Method method = RakNetInterface.class.getDeclaredMethod("getPacket", Player.class, byte[].class);
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
        return player;
    }

    private static RakNetInterface newUnsafeRakNetInterface(Network network, Map<String, Player> players) throws Exception {
        Unsafe unsafe = getUnsafe();
        RakNetInterface interfaz = (RakNetInterface) unsafe.allocateInstance(RakNetInterface.class);

        setField(RakNetInterface.class, interfaz, "network", network);
        setField(RakNetInterface.class, interfaz, "players", players);
        return interfaz;
    }

    private static RakNetServer newUnsafeRakNetServer() throws Exception {
        Unsafe unsafe = getUnsafe();
        RakNetServer server = (RakNetServer) unsafe.allocateInstance(RakNetServer.class);
        setField(RakNetServer.class, server, "externalQueue", new ConcurrentLinkedQueue<byte[]>());
        setField(RakNetServer.class, server, "internalQueue", new ConcurrentLinkedQueue<byte[]>());
        return server;
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
    @DisplayName("0.12 入站 0xFE 前缀应由 RakNetInterface 解析为旧协议数据包")
    void handleEncapsulatedShouldParseLegacy012FePacket() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v0_12_1, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(ProtocolInfo.v0_12_1, false);
            RakNetInterface interfaz = newUnsafeRakNetInterface(network, mapOf("legacy012", player));

            EncapsulatedPacket packet = new EncapsulatedPacket();
            packet.buffer = new byte[]{(byte) 0xfe, (byte) packetId};

            interfaz.handleEncapsulated("legacy012", packet, 0);

            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v0_12_1, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("0.14 入站 0x8E 前缀应在未知协议时回退到 0.14 协议池")
    void handleEncapsulatedShouldParseLegacy0148ePacketWhenProtocolUnknown() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v0_14_2, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(Integer.MAX_VALUE, false);
            RakNetInterface interfaz = newUnsafeRakNetInterface(network, mapOf("legacy014", player));

            EncapsulatedPacket packet = new EncapsulatedPacket();
            packet.buffer = new byte[]{(byte) 0x8e, (byte) packetId};

            interfaz.handleEncapsulated("legacy014", packet, 0);

            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v0_14_2, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("1.0+ 入站未压缩 0xFE 包应按玩家协议解析")
    void handleEncapsulatedShouldParseModernUncompressedFePacket() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v1_0_0_0, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(ProtocolInfo.v1_0_0_0, true);
            RakNetInterface interfaz = newUnsafeRakNetInterface(network, mapOf("modern100", player));

            EncapsulatedPacket packet = new EncapsulatedPacket();
            packet.buffer = new byte[]{(byte) 0xfe, (byte) packetId};

            interfaz.handleEncapsulated("modern100", packet, 0);

            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v1_0_0_0, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("未知协议下的 0xFE+zlib 入站包应按 Genisys 风格回落为 BatchPacket")
    void getPacketShouldTreatFeZlibPayloadAsBatchPacketWhenProtocolUnknown() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            CapturingPlayer player = newCapturingPlayer(Integer.MAX_VALUE, false);
            RakNetInterface interfaz = newUnsafeRakNetInterface(network, mapOf("zlib-unknown", player));

            byte[] compressedPayload = Zlib.deflate(new byte[]{0x01, 0x01, 0x02, 0x03, 0x04});
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
    @DisplayName("ServerHandler 应将 ENCAPSULATED 帧正确分发给 ServerInstance")
    void serverHandlerShouldDispatchEncapsulatedFrame() throws Exception {
        RakNetServer server = newUnsafeRakNetServer();
        CapturingServerInstance instance = new CapturingServerInstance();
        ServerHandler handler = new ServerHandler(server, instance);

        byte[] payload = new byte[]{(byte) 0xfe, 0x55, 0x66};
        byte[] frame = buildEncapsulatedFrame("sid-1", RakNet.PRIORITY_IMMEDIATE, payload);
        server.pushThreadToMainPacket(frame);

        assertTrue(handler.handlePacket());
        assertEquals("sid-1", instance.identifier);
        assertEquals(RakNet.PRIORITY_IMMEDIATE, instance.flags);
        assertNotNull(instance.packet);
        assertArrayEquals(payload, instance.packet.buffer);
    }

    @Test
    @DisplayName("ServerHandler 到 RakNetInterface 链路应可处理 0.14 前缀包")
    void serverHandlerToRakNetInterfaceShouldHandleLegacy014Packet() throws Exception {
        int oldDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 0;
        try {
            Network network = new Network(null);
            int packetId = packetId(network, ProtocolInfo.v0_14_2, PlayStatusPacket.class);

            CapturingPlayer player = newCapturingPlayer(Integer.MAX_VALUE, false);
            RakNetInterface interfaz = newUnsafeRakNetInterface(network, mapOf("sid-2", player));

            RakNetServer server = newUnsafeRakNetServer();
            ServerHandler handler = new ServerHandler(server, interfaz);
            byte[] frame = buildEncapsulatedFrame("sid-2", RakNet.PRIORITY_NORMAL, new byte[]{(byte) 0x8e, (byte) packetId});
            server.pushThreadToMainPacket(frame);

            assertTrue(handler.handlePacket());
            assertNotNull(player.capturedPacket);
            assertTrue(player.capturedPacket instanceof PlayStatusPacket);
            assertEquals(ProtocolInfo.v0_14_2, player.capturedPacket.protocol);
        } finally {
            Nukkit.DEBUG = oldDebug;
        }
    }

    @Test
    @DisplayName("ServerHandler 应将 ACK 通知正确分发给 ServerInstance")
    void serverHandlerShouldDispatchAckNotification() throws Exception {
        RakNetServer server = newUnsafeRakNetServer();
        CapturingServerInstance instance = new CapturingServerInstance();
        ServerHandler handler = new ServerHandler(server, instance);

        String identifier = "sid-ack";
        int ackId = 1024;
        byte[] frame = Binary.appendBytes(
                RakNet.PACKET_ACK_NOTIFICATION,
                new byte[]{(byte) (identifier.length() & 0xff)},
                identifier.getBytes(StandardCharsets.UTF_8),
                Binary.writeInt(ackId)
        );
        server.pushThreadToMainPacket(frame);

        assertTrue(handler.handlePacket());
        assertEquals(identifier, instance.ackIdentifier);
        assertEquals(ackId, instance.ackId);
    }

    private static final class CapturingPlayer extends Player {
        private DataPacket capturedPacket;

        private CapturingPlayer() {
            super(null, 0L, "", 0);
            throw new UnsupportedOperationException();
        }

        @Override
        public void handleDataPacket(DataPacket packet) {
            this.capturedPacket = packet;
        }
    }

    private static final class CapturingServerInstance implements ServerInstance {
        private String identifier;
        private EncapsulatedPacket packet;
        private int flags;
        private String ackIdentifier;
        private int ackId;

        @Override
        public void openSession(String identifier, String address, int port, long clientID) {
        }

        @Override
        public void closeSession(String identifier, String reason) {
        }

        @Override
        public void handleEncapsulated(String identifier, EncapsulatedPacket packet, int flags) {
            this.identifier = identifier;
            this.packet = packet;
            this.flags = flags;
        }

        @Override
        public void handleRaw(String address, int port, byte[] payload) {
        }

        @Override
        public void notifyACK(String identifier, int identifierACK) {
            this.ackIdentifier = identifier;
            this.ackId = identifierACK;
        }

        @Override
        public void handleOption(String option, String value) {
        }
    }
}
