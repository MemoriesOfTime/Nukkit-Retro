package cn.nukkit.network.protocol;

/**
 * @author Nukkit Project Team
 */
public class SetSpawnPositionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_SPAWN_POSITION_PACKET;

    public static final int TYPE_PLAYER_SPAWN = 0;
    public static final int TYPE_WORLD_SPAWN = 1;

    public int spawnType;
    public int y;
    public int z;
    public int x;
    public boolean spawnForced = false;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putInt(this.x);
            this.putInt(this.y);
            this.putInt(this.z);
        } else if (ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.putUnsignedVarInt(this.spawnType);
            this.putBlockCoords(this.x, this.y, this.z);
            this.putBoolean(this.spawnForced);
        } else {
            this.putVarInt(this.spawnType);
            this.putBlockCoords(this.x, this.y, this.z);
            this.putBoolean(this.spawnForced);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
