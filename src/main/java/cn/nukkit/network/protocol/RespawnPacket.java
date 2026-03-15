package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;

/**
 * @author Nukkit Project Team
 */
public class RespawnPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESPAWN_PACKET;

    public float x;
    public float y;
    public float z;

    @Override
    public void decode() {
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.x = this.getFloat();
            this.y = this.getFloat();
            this.z = this.getFloat();
        } else {
            Vector3f v = this.getVector3f();
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
        }
    }

    @Override
    public void encode() {
        this.reset();
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
        } else {
            this.putVector3f(this.x, this.y, this.z);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
