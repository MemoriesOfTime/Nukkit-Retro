package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ContainerSetContentPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.CONTAINER_SET_CONTENT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static final byte SPECIAL_INVENTORY = 0;
    public static final byte SPECIAL_ARMOR = 0x78;
    public static final byte SPECIAL_CREATIVE = 0x79;
    public static final byte SPECIAL_HOTBAR = 0x7a;
    public static final byte SPECIAL_FIXED_INVENTORY = 0x7b;

    public long windowid;
    public long eid;
    public Item[] slots = new Item[0];
    public int[] hotbar = new int[0];

    @Override
    public DataPacket clean() {
        this.slots = new Item[0];
        this.hotbar = new int[0];
        return super.clean();
    }

    @Override
    public void decode() {
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.windowid = this.getByte();
            this.eid = 0;
        } else if (this.protocol < ProtocolInfo.v1_1_0) {
            this.windowid = this.getByte();
            this.eid = 0;
        } else {
            this.windowid = this.getUnsignedVarInt();
            this.eid = this.getVarLong();
        }
        int count = (ProtocolInfo.isBefore0160(this.protocol)) ? this.getShort() : (int) this.getUnsignedVarInt();
        this.slots = new Item[count];

        for (int s = 0; s < count && !this.feof(); ++s) {
            this.slots[s] = this.getSlot();
        }

        if (this.feof()) {
            this.hotbar = new int[0];
            return;
        }

        count = (ProtocolInfo.isBefore0160(this.protocol)) ? this.getShort() : (int) this.getUnsignedVarInt();
        if (this.protocol >= ProtocolInfo.v0_16_0 && this.protocol < ProtocolInfo.v1_1_0 && this.windowid != SPECIAL_INVENTORY) {
            this.hotbar = new int[0];
            for (int s = 0; s < count && !this.feof(); ++s) {
                if (ProtocolInfo.isBefore0160(this.protocol)) {
                    this.getInt();
                } else {
                    this.getVarInt();
                }
            }
        } else {
            this.hotbar = new int[count];
            for (int s = 0; s < count && !this.feof(); ++s) {
                this.hotbar[s] = (ProtocolInfo.isBefore0160(this.protocol)) ? this.getInt() : this.getVarInt();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.putByte((byte) this.windowid);
            this.putShort(this.slots.length);
            for (Item slot : this.slots) {
                this.putSlot(slot);
            }

            if (this.windowid == SPECIAL_INVENTORY && this.hotbar.length > 0) {
                this.putShort(this.hotbar.length);
                for (int slot : this.hotbar) {
                    this.putInt(slot);
                }
            } else {
                this.putShort(0);
            }
            return;
        }

        if (this.protocol < ProtocolInfo.v1_1_0) {
            this.putByte((byte) this.windowid);
        } else {
            this.putUnsignedVarInt(this.windowid);
            this.putVarLong(this.eid);
        }
        this.putUnsignedVarInt(this.slots.length);
        for (Item slot : this.slots) {
            this.putSlot(slot);
        }

        if (this.windowid == SPECIAL_INVENTORY && this.hotbar.length > 0) {
            this.putUnsignedVarInt(this.hotbar.length);
            for (int slot : this.hotbar) {
                this.putVarInt(slot);
            }
        } else {
            this.putUnsignedVarInt(0);
        }
    }

    @Override
    public ContainerSetContentPacket clone() {
        ContainerSetContentPacket pk = (ContainerSetContentPacket) super.clone();
        pk.slots = this.slots.clone();
        return pk;
    }
}
