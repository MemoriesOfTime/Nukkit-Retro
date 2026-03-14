package cn.nukkit.item;

import cn.nukkit.block.BlockDoorAcacia;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorAcacia extends Item {
    public ItemDoorAcacia() {
        this(0, 1);
    }

    public ItemDoorAcacia(Integer meta) {
        this(meta, 1);
    }

    public ItemDoorAcacia(Integer meta, int count) {
        super(ACACIA_DOOR, 0, count, "Acacia Door");
        this.block = new BlockDoorAcacia();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
