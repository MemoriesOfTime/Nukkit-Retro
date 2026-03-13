package cn.nukkit.network.protocol;

public class SetHealthPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_HEALTH_PACKET;

    public int health;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (this.protocol < ProtocolInfo.v0_16_0) {
            this.health = this.getInt();
        } else {
            this.health = (int) this.getUnsignedVarInt();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol < ProtocolInfo.v0_16_0) {
            this.putInt(this.health);
        } else {
            this.putUnsignedVarInt(this.health);
        }
    }
}
