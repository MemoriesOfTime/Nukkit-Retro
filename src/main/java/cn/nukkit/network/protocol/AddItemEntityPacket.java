package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.item.Item;
import cn.nukkit.utils.Binary;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class AddItemEntityPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.ADD_ITEM_ENTITY_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public long entityRuntimeId;
    public Item item;
    public float x;
    public float y;
    public float z;
    public float speedX;
    public float speedY;
    public float speedZ;
    public EntityMetadata metadata = new EntityMetadata();

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putLong(this.entityRuntimeId);
        } else {
            this.putVarLong(this.entityUniqueId);
            this.putVarLong(this.entityRuntimeId);
        }
        this.putSlot(this.item);
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            this.putFloat(this.speedX);
            this.putFloat(this.speedY);
            this.putFloat(this.speedZ);
        } else {
            this.putVector3f(this.x, this.y, this.z);
            this.putVector3f(this.speedX, this.speedY, this.speedZ);
        }
        if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.put(Binary.writeMetadata(metadata));
        }
    }
}
