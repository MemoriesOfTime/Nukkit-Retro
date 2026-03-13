package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChorusFruit extends ItemEdible {

    public ItemChorusFruit() {
        this(0, 1);
    }

    public ItemChorusFruit(Integer meta) {
        this(meta, 1);
    }

    public ItemChorusFruit(Integer meta, int count) {
        super(CHORUS_FRUIT, 0, count, "Chorus Fruit");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_0_0;
    }
}
