package cn.nukkit.item;

import cn.nukkit.block.BlockItemFrame;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * Created by Pub4Game on 03.07.2016.
 */
public class ItemItemFrame extends Item {

    public ItemItemFrame() {
        this(0, 1);
    }

    public ItemItemFrame(Integer meta) {
        this(meta, 1);
    }

    public ItemItemFrame(Integer meta, int count) {
        super(ITEM_FRAME, meta, count, "Item Frame");
        this.block = new BlockItemFrame();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
