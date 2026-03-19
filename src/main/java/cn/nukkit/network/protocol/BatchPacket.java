package cn.nukkit.network.protocol;

import cn.nukkit.utils.Binary;
import cn.nukkit.utils.Zlib;

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
            // 检查是否以 zlib 头部开头
            byte[] header = Binary.subBytes(this.getBuffer(), this.getOffset(), Math.min(2, this.getCount() - this.getOffset()));
            if (Zlib.hasZlibHeader(header)) {
                // 1.1.0+ 客户端发送的批处理包格式是 [zlib_data]，没有长度前缀
                this.payload = this.get();
            } else {
                int payloadLength = Binary.readInt(Binary.subBytes(this.getBuffer(), this.getOffset(), 4));
                if (payloadLength >= 0 && payloadLength <= this.getCount() - this.getOffset() - 4) {
                    this.setOffset(this.getOffset() + 4);
                    this.payload = this.get(payloadLength);
                } else {
                    this.payload = this.get();
                }
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
