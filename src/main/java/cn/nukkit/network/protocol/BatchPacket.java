package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class BatchPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.BATCH_PACKET;

    public byte[] payload;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (ProtocolInfo.getPacketPoolProtocol(this.protocol) == ProtocolInfo.v0_14_2) {
            this.payload = this.get(this.getInt());
        } else {
            this.payload = this.get();
        }
    }

    @Override
    public void encode() {
        if (ProtocolInfo.getPacketPoolProtocol(this.protocol) == ProtocolInfo.v0_14_2) {
            this.reset();
            this.putInt(this.payload.length);
            this.put(this.payload);
        }
    }
}
