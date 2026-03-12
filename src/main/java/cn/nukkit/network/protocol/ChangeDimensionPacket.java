package cn.nukkit.network.protocol;

/**
 * Created on 2016/1/5 by xtypr.
 * Package cn.nukkit.network.protocol in project nukkit .
 */
public class ChangeDimensionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CHANGE_DIMENSION_PACKET;

    public int dimension;

    public float x;
    public float y;
    public float z;

    public boolean unknown;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putByte((byte) this.dimension);
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            this.putByte((byte) 0);
        } else {
            this.putVarInt(this.dimension);
            this.putVector3f(this.x, this.y, this.z);
            this.putBoolean(this.unknown);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
