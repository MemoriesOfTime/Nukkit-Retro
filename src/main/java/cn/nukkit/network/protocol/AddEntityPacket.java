package cn.nukkit.network.protocol;

import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.utils.Binary;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class AddEntityPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.ADD_ENTITY_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public long entityRuntimeId;
    public int type;
    public float x;
    public float y;
    public float z;
    public float speedX = 0f;
    public float speedY = 0f;
    public float speedZ = 0f;
    public float yaw;
    public float pitch;
    public EntityMetadata metadata = new EntityMetadata();
    public Attribute[] attributes = new Attribute[0];
    public final Object[][] links = new Object[0][3];

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putLong(this.entityRuntimeId);
            this.putInt(this.type);
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            this.putFloat(this.speedX);
            this.putFloat(this.speedY);
            this.putFloat(this.speedZ);
            if (this.protocol <= ProtocolInfo.v0_15_0) {
                this.putFloat(this.yaw);
                this.putFloat(this.pitch);
            } else {
                this.putFloat(this.yaw * 0.71f);
                this.putFloat(this.pitch * 0.71f);
                this.putInt(0);
            }
            this.put(Binary.writeMetadata(this.protocol, this.metadata));
            this.putShort(this.links.length);
            for (Object[] link : this.links) {
                this.putLong((long) link[0]);
                this.putLong((long) link[1]);
                this.putByte((byte) link[2]);
            }
        } else {
            this.putVarLong(this.entityUniqueId);
            this.putVarLong(this.entityRuntimeId);
            this.putUnsignedVarInt(this.type);
            this.putVector3f(this.x, this.y, this.z);
            this.putVector3f(this.speedX, this.speedY, this.speedZ);
            if (ProtocolInfo.isLegacyProtocol(this.protocol)) {
                this.putLFloat(this.pitch * (256f / 360f));
                this.putLFloat(this.yaw * (256f / 360f));
                this.putUnsignedVarInt(0);
            } else {
                this.putLFloat(this.pitch);
                this.putLFloat(this.yaw);
                this.putAttributeList(this.attributes);
            }
            this.put(Binary.writeMetadata(this.protocol, this.metadata));
            this.putUnsignedVarInt(this.links.length);
            for (Object[] link : this.links) {
                this.putVarLong((long) link[0]);
                this.putVarLong((long) link[1]);
                this.putByte((byte) link[2]);
            }
        }
    }
}
