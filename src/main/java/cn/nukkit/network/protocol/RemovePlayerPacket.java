package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.ProtocolInfo.SinceProtocol;
import cn.nukkit.network.protocol.ProtocolInfo.UnsupportedSince;

import java.util.UUID;

/**
 * 仅用于 0.13.x - 0.14.x 客户端的人形实体移除包。
 */
@SinceProtocol(ProtocolInfo.v0_13_0)
@UnsupportedSince(ProtocolInfo.v0_15_0)
public class RemovePlayerPacket extends DataPacket {

    public static final byte NETWORK_ID = (byte) 0x97;

    public long eid;
    public UUID uuid;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putLong(this.eid);
        this.putUUID(this.uuid);
    }
}
