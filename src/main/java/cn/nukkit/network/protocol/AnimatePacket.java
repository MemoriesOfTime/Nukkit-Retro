package cn.nukkit.network.protocol;

/**
 * @author Nukkit Project Team
 */
public class AnimatePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ANIMATE_PACKET;

    public long eid;
    public int action;
    public float unknown;

    @Override
    public void decode() {
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.action = this.getByte();
            this.eid = this.getLong();
        } else {
            this.action = (int) this.getUnsignedVarInt();
            this.eid = getVarLong();
        }
        if (!ProtocolInfo.isLegacyProtocol(this.protocol) && (this.action & 0x80) != 0) {
            this.unknown = this.getLFloat();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putByte((byte) this.action);
            this.putLong(this.eid);
        } else {
            this.putUnsignedVarInt(this.action);
            this.putVarLong(this.eid);
        }
        if (!ProtocolInfo.isLegacyProtocol(this.protocol) && (this.action & 0x80) != 0) {
            this.putLFloat(this.unknown);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
