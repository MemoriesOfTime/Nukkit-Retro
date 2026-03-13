package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemShulkerShell extends Item {

    public ItemShulkerShell() {
        this(0, 1);
    }

    public ItemShulkerShell(Integer meta) {
        this(meta, 1);
    }

    public ItemShulkerShell(Integer meta, int count) {
        super(SHULKER_SHELL, 0, count, "Shulker Shell");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_0_0;
    }
}
