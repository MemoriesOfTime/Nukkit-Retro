package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class SetTimePacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.SET_TIME_PACKET;

    public int time;
    public boolean started = true;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putInt(this.time);
            this.putByte((byte) (this.started ? 1 : 0));
        } else {
            this.putVarInt(this.time);
        }
        if (ProtocolInfo.isLegacyProtocol(this.protocol) && !(this.protocol < ProtocolInfo.v0_16_0)) {
            this.putBoolean(this.started);
        }
    }

}
