package cn.nukkit.network.protocol;

/**
 * @author Nukkit Project Team
 */
public class SetDifficultyPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_DIFFICULTY_PACKET;

    public int difficulty;

    @Override
    public void decode() {
        this.difficulty = (this.protocol < ProtocolInfo.v0_16_0) ? this.getInt() : (int) this.getUnsignedVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putInt(this.difficulty);
        } else {
            this.putUnsignedVarInt(this.difficulty);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
