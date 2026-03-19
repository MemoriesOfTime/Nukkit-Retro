package cn.nukkit.network.protocol;

/**
 * @author Nukkit Project Team
 */
public class AdventureSettingsPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.ADVENTURE_SETTINGS_PACKET;

    public boolean worldImmutable;
    public boolean noPvp;
    public boolean noPvm;
    public boolean noMvp;

    public boolean autoJump;
    public boolean allowFlight;
    public boolean noClip;
    public boolean worldBuilder;
    public boolean isFlying;
    public boolean muted;

    /*
     bit mask | flag name
	0x00000001 world_immutable
	0x00000002 no_pvp
	0x00000004 no_pvm
	0x00000008 no_mvp
	0x00000010 ?
	0x00000020 auto_jump
	0x00000040 allow_fly
	0x00000080 noclip
	0x00000100 ?
	0x00000200 is_flying
	*/

    public int flags = 0;
    public int userPermission;
    public int globalPermission;

    @Override
    public void decode() {
        if (ProtocolInfo.isBefore0150(this.protocol)) {
            this.flags = this.getInt();
            this.userPermission = this.getInt();
            this.globalPermission = this.getInt();

            this.worldImmutable = (this.flags & 0x01) != 0;
            this.noPvp = false;
            this.noPvm = false;
            this.noMvp = false;
            this.autoJump = (this.flags & 0x40) != 0;
            this.allowFlight = (this.flags & 0x80) != 0;
            this.noClip = (this.flags & 0x100) != 0;
            this.worldBuilder = false;
            this.isFlying = false;
            this.muted = false;
            return;
        }

        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.flags = this.getInt();
            this.userPermission = this.getInt();
            this.globalPermission = this.getInt();
        } else {
            this.flags = (int) this.getUnsignedVarInt();
            this.userPermission = (int) this.getUnsignedVarInt();
        }
        this.worldImmutable = (this.flags & 1) != 0;
        this.noPvp = (this.flags & (1 << 1)) != 0;
        this.noPvm = (this.flags & (1 << 2)) != 0;
        this.noMvp = (this.flags & (1 << 3)) != 0;

        this.autoJump = (this.flags & (1 << 5)) != 0;
        this.allowFlight = (this.flags & (1 << 6)) != 0;
        this.noClip = (this.flags & (1 << 7)) != 0;
        this.worldBuilder = (this.flags & (1 << 8)) != 0;
        this.isFlying = (this.flags & (1 << 9)) != 0;
        this.muted = this.protocol >= ProtocolInfo.v1_1_0 && (this.flags & (1 << 10)) != 0;
    }

    @Override
    public void encode() {
        this.reset();
        this.flags = 0;
        if (ProtocolInfo.isBefore0150(this.protocol)) {
            if (this.worldImmutable) this.flags |= 0x01;
            if (this.autoJump) this.flags |= 0x40;
            if (this.allowFlight) this.flags |= 0x80;
            if (this.noClip) this.flags |= 0x100;

            this.putInt(this.flags);
            this.putInt(2);
            this.putInt(2);
            return;
        }

        if (this.worldImmutable) this.flags |= 1;
        if (this.noPvp) this.flags |= 1 << 1;
        if (this.noPvm) this.flags |= 1 << 2;
        if (this.noMvp) this.flags |= 1 << 3;

        if (this.autoJump) this.flags |= 1 << 5;
        if (this.allowFlight) this.flags |= 1 << 6;
        if (this.noClip) this.flags |= 1 << 7;
        if (this.worldBuilder) this.flags |= 1 << 8;
        if (this.isFlying) this.flags |= 1 << 9;
        if (this.protocol >= ProtocolInfo.v1_1_0 && this.muted) this.flags |= 1 << 10;
        if (ProtocolInfo.isBefore0160(this.protocol)) {
            this.putInt(this.flags);
            this.putInt(this.userPermission);
            this.putInt(this.globalPermission);
        } else {
            this.putUnsignedVarInt(this.flags);
            this.putUnsignedVarInt(this.userPermission);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
