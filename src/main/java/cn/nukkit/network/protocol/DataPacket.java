package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.raknet.protocol.EncapsulatedPacket;
import cn.nukkit.utils.BinaryStream;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public abstract class DataPacket extends BinaryStream implements Cloneable {

    public int protocol = Integer.MAX_VALUE;
    public boolean isEncoded = false;
    private int channel = 0;

    public EncapsulatedPacket encapsulatedPacket;
    public byte reliability;
    public Integer orderIndex = null;
    public Integer orderChannel = null;

    public abstract byte pid();

    public abstract void decode();

    public abstract void encode();

    @Override
    public void reset() {
        super.reset();
        int packetId = this.pid() & 0xff;
        int targetProtocol = this.protocol == Integer.MAX_VALUE ? ProtocolInfo.CURRENT_PROTOCOL : this.protocol;
        Server server = Server.getInstance();

        if (server != null && server.getNetwork() != null) {
            try {
                packetId = server.getNetwork().getPacketPool(targetProtocol).getPacketId(this.getClass());
            } catch (Exception ignore) {
                packetId = 0x6a;
            }
        }

        this.putByte((byte) packetId);
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getChannel() {
        return channel;
    }

    public DataPacket clean() {
        this.setBuffer(null);
        this.setOffset(0);
        this.isEncoded = false;
        return this;
    }

    @Override
    public DataPacket clone() {
        try {
            DataPacket packet = (DataPacket) super.clone();
            if (this.getCount() >= 0) {
                packet.setBuffer(this.getBuffer());
            }
            return packet;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
