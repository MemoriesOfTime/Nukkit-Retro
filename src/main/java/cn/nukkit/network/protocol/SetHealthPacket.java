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
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.health = this.getInt();
        } else {
            this.health = (int) this.getUnsignedVarInt();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.putInt(this.health);
        } else {
            this.putUnsignedVarInt(this.health);
        }
    }
}
