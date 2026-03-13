package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChorusFruitPopped extends Item {

    public ItemChorusFruitPopped() {
        this(0, 1);
    }

    public ItemChorusFruitPopped(Integer meta) {
        this(meta, 1);
    }

    public ItemChorusFruitPopped(Integer meta, int count) {
        super(POPPED_CHORUS_FRUIT, 0, count, "Popped Chorus Fruit");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_0_0;
    }
}
