package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class BlockEventPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.BLOCK_EVENT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public int x;
    public int y;
    public int z;
    public int case1;
    public int case2;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putInt(this.x);
            this.putInt(this.y);
            this.putInt(this.z);
            this.putInt(this.case1);
            this.putInt(this.case2);
        } else {
            this.putBlockCoords(this.x, this.y, this.z);
            this.putVarInt(this.case1);
            this.putVarInt(this.case2);
        }
    }
}
