package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemMuttonCooked extends ItemEdible {

    public ItemMuttonCooked() {
        this(0, 1);
    }

    public ItemMuttonCooked(Integer meta) {
        this(meta, 1);
    }

    public ItemMuttonCooked(Integer meta, int count) {
        super(COOKED_MUTTON, meta, count, "Cooked Mutton");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
