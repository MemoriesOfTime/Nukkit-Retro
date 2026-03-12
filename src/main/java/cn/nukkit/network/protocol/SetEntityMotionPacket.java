package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class SetEntityMotionPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.SET_ENTITY_MOTION_PACKET;

    public long eid;
    public float motionX;
    public float motionY;
    public float motionZ;

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
            if (this.protocol == ProtocolInfo.v0_15_0) {
                this.putInt(1);
            }
            this.putLong(this.eid);
            this.putFloat(this.motionX);
            this.putFloat(this.motionY);
            this.putFloat(this.motionZ);
        } else {
            this.putVarLong(this.eid);
            this.putVector3f(this.motionX, this.motionY, this.motionZ);
        }
    }
}
