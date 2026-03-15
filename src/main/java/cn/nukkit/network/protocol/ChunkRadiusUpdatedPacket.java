package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ChunkRadiusUpdatedPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CHUNK_RADIUS_UPDATED_PACKET;

    public int radius;

    @Override
    public void decode() {
        this.radius = (ProtocolInfo.isBefore0160(this.protocol)) ? this.getInt() : this.getVarInt();
    }

    @Override
    public void encode() {
        super.reset();
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.putInt(this.radius);
        } else {
            this.putVarInt(this.radius);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
