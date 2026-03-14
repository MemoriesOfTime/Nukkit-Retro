package cn.nukkit.item;

import cn.nukkit.block.BlockDoorDarkOak;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorDarkOak extends Item {
    public ItemDoorDarkOak() {
        this(0, 1);
    }

    public ItemDoorDarkOak(Integer meta) {
        this(meta, 1);
    }

    public ItemDoorDarkOak(Integer meta, int count) {
        super(DARK_OAK_DOOR, 0, count, "Dark Oak Door");
        this.block = new BlockDoorDarkOak();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
