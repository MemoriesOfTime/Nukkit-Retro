package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * Created by Snake1999 on 2016/1/14.
 * Package cn.nukkit.item in project nukkit.
 */
public class ItemRabbitRaw extends ItemEdible {
    public ItemRabbitRaw() {
        this(0, 1);
    }

    public ItemRabbitRaw(Integer meta) {
        this(meta, 1);
    }

    public ItemRabbitRaw(Integer meta, int count) {
        super(RAW_RABBIT, meta, count, "Raw Rabbit");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v0_14_0;
    }
}
