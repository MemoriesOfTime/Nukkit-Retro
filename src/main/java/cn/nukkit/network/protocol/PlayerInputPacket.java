package cn.nukkit.network.protocol;

/**
 * @author Nukkit Project Team
 */
public class PlayerInputPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_INPUT_PACKET;

    public float motionX;
    public float motionY;

    public boolean unknownBool1;
    public boolean unknownBool2;

    @Override
    public void decode() {
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.motionX = this.getFloat();
            this.motionY = this.getFloat();
            int flags = this.getByte();
            this.unknownBool1 = (flags & 0x80) != 0;
            this.unknownBool2 = (flags & 0x40) != 0;
        } else {
            this.motionX = this.getLFloat();
            this.motionY = this.getLFloat();
            this.unknownBool1 = this.getBoolean();
            this.unknownBool2 = this.getBoolean();
        }
    }

    @Override
    public void encode() {

    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
