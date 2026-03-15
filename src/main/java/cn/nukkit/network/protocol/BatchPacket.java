package cn.nukkit.network.protocol;

import cn.nukkit.utils.Binary;

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
        if (this.protocol != Integer.MAX_VALUE && ProtocolInfo.isBefore0150(this.protocol)) {
            this.payload = this.get(this.getInt());
        } else if (this.protocol == Integer.MAX_VALUE && this.getCount() - this.getOffset() >= 4) {
            int payloadLength = Binary.readInt(Binary.subBytes(this.getBuffer(), this.getOffset(), 4));
            if (payloadLength >= 0 && payloadLength <= this.getCount() - this.getOffset() - 4) {
                this.setOffset(this.getOffset() + 4);
                this.payload = this.get(payloadLength);
            } else {
                this.payload = this.get();
            }
        } else {
            this.payload = this.get();
        }
    }

    @Override
    public void encode() {
        if (this.protocol != Integer.MAX_VALUE && ProtocolInfo.isBefore0150(this.protocol)) {
            this.reset();
            this.putInt(this.payload.length);
            this.put(this.payload);
        }
    }
}
