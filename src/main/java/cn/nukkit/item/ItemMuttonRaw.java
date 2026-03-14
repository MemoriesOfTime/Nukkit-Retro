package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemMuttonRaw extends ItemEdible {

    public ItemMuttonRaw() {
        this(0, 1);
    }

    public ItemMuttonRaw(Integer meta) {
        this(meta, 1);
    }

    public ItemMuttonRaw(Integer meta, int count) {
        super(RAW_MUTTON, meta, count, "Raw Mutton");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
