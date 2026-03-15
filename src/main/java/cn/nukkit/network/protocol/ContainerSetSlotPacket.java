package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ContainerSetSlotPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.CONTAINER_SET_SLOT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public int windowid;
    public int slot;
    public int hotbarSlot;
    public Item item;
    public int selectedSlot;

    @Override
    public void decode() {
        this.windowid = this.getByte();
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.slot = this.getShort();
            this.hotbarSlot = this.getShort();
        } else {
            this.slot = this.getVarInt();
            this.hotbarSlot = this.getVarInt();
        }
        this.item = this.getSlot();
        this.selectedSlot = (ProtocolInfo.isBefore0160(this.protocol)) || this.feof() ? 0 : this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte((byte) this.windowid);
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putShort(this.slot);
            this.putShort(this.hotbarSlot);
        } else {
            this.putVarInt(this.slot);
            this.putVarInt(this.hotbarSlot);
        }
        this.putSlot(this.item);
        if (!(ProtocolInfo.isBefore0160(this.protocol))) {
            this.putByte((byte) this.selectedSlot);
        }
    }
}
