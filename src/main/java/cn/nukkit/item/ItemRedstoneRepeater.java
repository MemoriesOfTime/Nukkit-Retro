package cn.nukkit.item;

import cn.nukkit.block.BlockRedstoneRepeaterUnpowered;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author CreeperFace
 */
public class ItemRedstoneRepeater extends Item {

    public ItemRedstoneRepeater() {
        this(0);
    }

    public ItemRedstoneRepeater(Integer meta) {
        this(0, 1);
    }

    public ItemRedstoneRepeater(Integer meta, int count) {
        super(REPEATER, meta, count, "Redstone Repeater");
        this.block = new BlockRedstoneRepeaterUnpowered();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
