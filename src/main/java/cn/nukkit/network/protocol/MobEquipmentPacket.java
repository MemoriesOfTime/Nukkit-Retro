package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class MobEquipmentPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.MOB_EQUIPMENT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long eid;
    public Item item;
    public int slot;
    public int selectedSlot;
    public int windowId;

    @Override
    public void decode() {
        this.eid = (this.protocol < ProtocolInfo.v0_16_0) ? this.getLong() : this.getVarLong(); //EntityRuntimeID
        this.item = this.getSlot();
        this.slot = this.getByte();
        this.selectedSlot = this.getByte();
        this.windowId = ProtocolInfo.isLegacyProtocol(this.protocol) || this.feof() ? 0 : this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putLong(this.eid);
        } else {
            this.putVarLong(this.eid); //EntityRuntimeID
        }
        this.putSlot(this.item);
        this.putByte((byte) this.slot);
        this.putByte((byte) this.selectedSlot);
        if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.putByte((byte) this.windowId);
        }
    }
}
