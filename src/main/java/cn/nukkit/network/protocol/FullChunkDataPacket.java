package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class FullChunkDataPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.FULL_CHUNK_DATA_PACKET;
    public static final byte ORDER_COLUMNS = 0;
    public static final byte ORDER_LAYERED = 1;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public int chunkX;
    public int chunkZ;
    public byte order = ORDER_COLUMNS;
    public byte[] data;

    @Override
    public void decode() {
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.chunkX = this.getInt();
            this.chunkZ = this.getInt();
            this.order = (byte) this.getByte();
            this.data = this.get(this.getInt());
        } else {
            this.chunkX = this.getVarInt();
            this.chunkZ = this.getVarInt();
            this.data = this.getByteArray();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putInt(this.chunkX);
            this.putInt(this.chunkZ);
            this.putByte(this.order);
            this.putInt(this.data.length);
            this.put(this.data);
        } else {
            this.putVarInt(this.chunkX);
            this.putVarInt(this.chunkZ);
            this.putByteArray(this.data);
        }
    }
}
