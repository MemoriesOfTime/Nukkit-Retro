package cn.nukkit.item;

import cn.nukkit.block.BlockDoorBirch;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorBirch extends Item {
    public ItemDoorBirch() {
        this(0, 1);
    }

    public ItemDoorBirch(Integer meta) {
        this(meta, 1);
    }

    public ItemDoorBirch(Integer meta, int count) {
        super(BIRCH_DOOR, 0, count, "Birch Door");
        this.block = new BlockDoorBirch();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
