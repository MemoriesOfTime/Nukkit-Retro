package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.raknet.protocol.EncapsulatedPacket;
import cn.nukkit.utils.BinaryStream;

import java.nio.charset.StandardCharsets;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public abstract class DataPacket extends BinaryStream implements Cloneable {

    public int protocol = Integer.MAX_VALUE;
    public volatile boolean isEncoded = false;
    private int channel = 0;

    public EncapsulatedPacket encapsulatedPacket;
    public byte reliability;
    public Integer orderIndex = null;
    public Integer orderChannel = null;

    public abstract byte pid();

    public abstract void decode();

    public abstract void encode();

    public final void tryEncode() {
        if (!this.isEncoded) {
            this.isEncoded = true;
            this.encode();
        }
    }

    @Override
    public byte[] getByteArray() {
        if (this.protocol < ProtocolInfo.v0_15_0) {
            return this.get(this.getShort() & 0xffff);
        }
        return super.getByteArray();
    }

    @Override
    public void putByteArray(byte[] b) {
        if (this.protocol < ProtocolInfo.v0_15_0) {
            this.putShort(b.length);
            this.put(b);
            return;
        }
        super.putByteArray(b);
    }

    @Override
    public String getString() {
        return new String(this.getByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void putString(String string) {
        this.putByteArray(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Item getSlot() {
        if (this.protocol < ProtocolInfo.v0_15_0) {
            int id = this.getShort();
            if (id <= 0) {
                return Item.get(0, 0, 0);
            }

            int count = this.getByte() & 0xff;
            int data = this.getShort();
            int nbtLen = this.getLShort();
            byte[] nbt = nbtLen > 0 ? this.get(nbtLen) : new byte[0];

            return Item.get(id, data, count, nbt);
        }
        return super.getSlot();
    }

    @Override
    public void putSlot(Item item) {
        if (this.protocol < ProtocolInfo.v0_15_0) {
            if (item == null || item.getId() == 0) {
                this.putShort(0);
                return;
            }

            this.putShort(item.getId());
            this.putByte((byte) item.getCount());
            this.putShort(item.hasMeta() ? item.getDamage() : -1);
            byte[] nbt = item.getCompoundTag();
            this.putLShort(nbt.length);
            this.put(nbt);
            return;
        }
        super.putSlot(item);
    }

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
            byte[] buffer = this.getBuffer();
            if (buffer != null) {
                packet.setBuffer(buffer);
            }
            return packet;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
