package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;

/**
 * Created on 15-10-14.
 */
public class MovePlayerPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOVE_PLAYER_PACKET;

    public static final byte MODE_NORMAL = 0;
    public static final byte MODE_RESET = 1;
    public static final byte MODE_ROTATION = 2;

    public long eid;
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float headYaw;
    public float pitch;
    public byte mode = MODE_NORMAL;
    public boolean onGround;
    public long ridingEid;

    @Override
    public void decode() {
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.eid = this.getLong();
            this.x = this.getFloat();
            this.y = this.getFloat();
            this.z = this.getFloat();
            this.yaw = this.getFloat();
            this.headYaw = this.getFloat();
            this.pitch = this.getFloat();
        } else {
            this.eid = this.getVarLong();
            Vector3f v = this.getVector3f();
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
            this.pitch = this.getLFloat();
            this.yaw = this.getLFloat();
            this.headYaw = this.getLFloat();
        }
        this.mode = (byte) this.getByte();
        this.onGround = ProtocolInfo.isBefore0160(this.protocol) ? this.getByte() > 0 : this.getBoolean();
        if (this.protocol >= ProtocolInfo.v1_1_0) {
            this.ridingEid = this.getVarLong();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.putLong(this.eid);
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            this.putFloat(this.yaw);
            this.putFloat(this.headYaw);
            this.putFloat(this.pitch);
        } else {
            this.putVarLong(this.eid);
            this.putVector3f(this.x, this.y, this.z);
            this.putLFloat(this.pitch);
            this.putLFloat(this.yaw);
            this.putLFloat(this.headYaw);
        }
        this.putByte(this.mode);
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.putByte(this.onGround ? (byte) 1 : 0);
        } else {
            this.putBoolean(this.onGround);
        }
        if (this.protocol >= ProtocolInfo.v1_1_0) {
            this.putVarLong(this.ridingEid);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
