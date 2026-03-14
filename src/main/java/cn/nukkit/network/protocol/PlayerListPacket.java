package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.Skin;

import java.util.UUID;

/**
 * @author Nukkit Project Team
 */
public class PlayerListPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_LIST_PACKET;

    public static final byte TYPE_ADD = 0;
    public static final byte TYPE_REMOVE = 1;

    public byte type;
    public Entry[] entries = new Entry[0];

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putByte(this.type);
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putInt(this.entries.length);
        } else {
            this.putUnsignedVarInt(this.entries.length);
        }
        for (Entry entry : this.entries) {
            if (type == TYPE_ADD) {
                this.putUUID(entry.uuid);
                if ((this.protocol < ProtocolInfo.v0_16_0)) {
                    this.putLong(entry.entityId);
                } else {
                    this.putVarLong(entry.entityId);
                }
                this.putString(entry.name);
                if (this.protocol < ProtocolInfo.v0_13_1) {
                    byte[] skinData = entry.skin != null ? entry.skin.getData() : new byte[Skin.SINGLE_SKIN_SIZE];
                    this.putBoolean(entry.skin != null && Skin.MODEL_ALEX.equals(entry.skin.getModel()));
                    this.putByte((byte) 0);
                    this.putByteArray(skinData);
                } else {
                    this.putSkin(entry.skin);
                }
            } else {
                this.putUUID(entry.uuid);
            }
        }

    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static class Entry {

        public final UUID uuid;
        public long entityId = 0;
        public String name = "";
        public Skin skin;

        public Entry(UUID uuid) {
            this.uuid = uuid;
        }

        public Entry(UUID uuid, long entityId, String name, Skin skin) {
            this.uuid = uuid;
            this.entityId = entityId;
            this.name = name;
            this.skin = skin;
        }
    }

}
