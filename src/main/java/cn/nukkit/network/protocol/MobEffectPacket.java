package cn.nukkit.network.protocol;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class MobEffectPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOB_EFFECT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static final byte EVENT_ADD = 1;
    public static final byte EVENT_MODIFY = 2;
    public static final byte EVENT_REMOVE = 3;

    public long eid;
    public int eventId;
    public int effectId;
    public int amplifier = 0;
    public boolean particles = true;
    public int duration = 0;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putLong(this.eid);
            this.putByte((byte) this.eventId);
            this.putByte((byte) this.effectId);
            this.putByte((byte) this.amplifier);
            this.putBoolean(this.particles);
            this.putInt(this.duration);
            return;
        }

        this.putVarLong(this.eid);
        this.putByte((byte) this.eventId);
        this.putVarInt(this.effectId);
        this.putVarInt(this.amplifier);
        this.putBoolean(this.particles);
        this.putVarInt(this.duration);
    }
}
