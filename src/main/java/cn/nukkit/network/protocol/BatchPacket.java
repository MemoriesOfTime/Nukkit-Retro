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
        if (this.protocol != Integer.MAX_VALUE && this.protocol < ProtocolInfo.v0_15_0) {
            this.payload = this.get(this.getInt());
        } else {
            this.payload = this.get();
        }
    }

    @Override
    public void encode() {
        if (this.protocol != Integer.MAX_VALUE && this.protocol < ProtocolInfo.v0_15_0) {
            this.reset();
            this.putInt(this.payload.length);
            this.put(this.payload);
        }
    }
}
