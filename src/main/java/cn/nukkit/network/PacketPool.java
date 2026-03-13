package cn.nukkit.network;

import cn.nukkit.Server;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo.SupportedProtocol;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 按协议版本维护数据包 ID 和数据包类之间的映射。
 */
public class PacketPool {

    @SupportedProtocol
    private final int protocolVersion;
    private final String minecraftVersion;
    private final Class<? extends DataPacket>[] packetsById;
    private final Map<Class<? extends DataPacket>, Integer> packetsByClass;

    @SuppressWarnings("unchecked")
    private PacketPool(@SupportedProtocol int protocolVersion, String minecraftVersion, Class<? extends DataPacket>[] packetsById,
                       Map<Class<? extends DataPacket>, Integer> packetsByClass) {
        this.protocolVersion = protocolVersion;
        this.minecraftVersion = minecraftVersion;
        this.packetsById = packetsById;
        this.packetsByClass = packetsByClass;
    }

    @SupportedProtocol
    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public DataPacket getPacket(int id) {
        if (id < 0 || id >= this.packetsById.length) {
            return null;
        }

        Class<? extends DataPacket> clazz = this.packetsById[id];
        if (clazz == null) {
            return null;
        }

        try {
            return clazz.newInstance();
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
            return null;
        }
    }

    public int getPacketId(Class<? extends DataPacket> clazz) {
        Integer id = this.packetsByClass.get(clazz);
        if (id == null) {
            throw new IllegalArgumentException("Unknown packet class " + clazz.getName());
        }
        return id;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.protocolVersion = this.protocolVersion;
        builder.minecraftVersion = this.minecraftVersion;
        System.arraycopy(this.packetsById, 0, builder.packetsById, 0, this.packetsById.length);
        builder.packetsByClass.putAll(this.packetsByClass);
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int protocolVersion = -1;
        private String minecraftVersion;
        private final Class<? extends DataPacket>[] packetsById = new Class[256];
        private final Map<Class<? extends DataPacket>, Integer> packetsByClass = new IdentityHashMap<>();

        public Builder protocolVersion(@SupportedProtocol int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder minecraftVersion(String minecraftVersion) {
            this.minecraftVersion = minecraftVersion;
            return this;
        }

        public <T extends DataPacket> Builder registerPacket(int id, Class<T> clazz) {
            if (id < 0 || id >= this.packetsById.length) {
                throw new IllegalArgumentException("Packet id out of range: " + id);
            }

            this.packetsById[id] = clazz;
            this.packetsByClass.put(clazz, id);
            return this;
        }

        public <T extends DataPacket> Builder registerPacket(byte id, Class<T> clazz) {
            return registerPacket(id & 0xff, clazz);
        }

        public Builder deregisterPacket(int id) {
            if (id >= 0 && id < this.packetsById.length) {
                Class<? extends DataPacket> clazz = this.packetsById[id];
                this.packetsById[id] = null;
                if (clazz != null) {
                    this.packetsByClass.remove(clazz);
                }
            }
            return this;
        }

        public PacketPool build() {
            if (this.protocolVersion < 0) {
                throw new IllegalArgumentException("No protocol version defined");
            }
            if (this.minecraftVersion == null || this.minecraftVersion.isEmpty()) {
                throw new IllegalArgumentException("No minecraft version defined");
            }

            return new PacketPool(
                    this.protocolVersion,
                    this.minecraftVersion,
                    Arrays.copyOf(this.packetsById, this.packetsById.length),
                    new IdentityHashMap<>(this.packetsByClass)
            );
        }
    }
}
