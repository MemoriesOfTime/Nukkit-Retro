package cn.nukkit.network.protocol;

import cn.nukkit.utils.RuleData;

/**
 * Created on 15-10-13.
 */
public class StartGamePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.START_GAME_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public long entityRuntimeId;
    public int playerGamemode;
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;
    public int seed;
    public byte dimension;
    public int generator = 1;
    public int gamemode;
    public int difficulty;
    public int spawnX;
    public int spawnY;
    public int spawnZ;
    public boolean b1 = true;
    public boolean b2 = true;
    public boolean b3 = false;
    public String unknownstr = "";
    public boolean hasAchievementsDisabled = true;
    public int dayCycleStopTime = -1; //-1 = not stopped, any positive value = stopped at that time
    public boolean eduMode = false;
    public float rainLevel;
    public float lightningLevel;
    public boolean commandsEnabled;
    public boolean isTexturePacksRequired = false;
    public RuleData[] ruleDatas = new RuleData[0];
    public String levelId = ""; //base64 string, usually the same as world folder name in vanilla
    public String worldName;
    public String premiumWorldTemplateId = "";
    public boolean unknown = false;
    public long currentTick;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol < ProtocolInfo.v0_14_0) {
            this.putInt(this.seed);
            this.putByte(this.dimension);
            this.putInt(this.generator);
            this.putInt(this.gamemode);
            this.putLong(this.entityUniqueId);
            this.putInt(this.spawnX);
            this.putInt(this.spawnY);
            this.putInt(this.spawnZ);
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            this.putByte((byte) 0);
            return;
        }

        if ((this.protocol < ProtocolInfo.v0_16_0)) {
            this.putInt(this.seed);
            this.putByte(this.dimension);
            this.putInt(this.generator);
            this.putInt(this.gamemode);
            this.putLong(this.entityUniqueId);
            this.putInt(this.spawnX);
            this.putInt(this.spawnY);
            this.putInt(this.spawnZ);
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            this.putBoolean(this.b1);
            this.putBoolean(this.b2);
            this.putBoolean(this.b3);
            this.putString(this.unknownstr);
            return;
        }

        this.putVarLong(this.entityUniqueId);
        this.putVarLong(this.entityRuntimeId);
        if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.putVarInt(this.playerGamemode);
        }
        this.putVector3f(this.x, this.y, this.z);
        if (ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.putLFloat(0);
            this.putLFloat(0);
        } else {
            this.putLFloat(this.yaw);
            this.putLFloat(this.pitch);
        }
        this.putVarInt(this.seed);
        this.putVarInt(this.dimension);
        this.putVarInt(this.generator);
        this.putVarInt(this.gamemode);
        this.putVarInt(this.difficulty);
        this.putBlockCoords(this.spawnX, this.spawnY, this.spawnZ);
        this.putBoolean(this.hasAchievementsDisabled);
        this.putVarInt(this.dayCycleStopTime);
        this.putBoolean(this.eduMode);
        this.putLFloat(this.rainLevel);
        this.putLFloat(this.lightningLevel);
        this.putBoolean(this.commandsEnabled);
        this.putBoolean(this.isTexturePacksRequired);
        if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.putUnsignedVarInt(this.ruleDatas.length);
            for (RuleData rule : this.ruleDatas) {
                this.putRuleData(rule);
            }
        }
        this.putString(this.levelId);
        this.putString(this.worldName);
        if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
            this.putString(this.premiumWorldTemplateId);
            this.putBoolean(this.unknown);
            this.putLLong(this.currentTick);
        }
    }

}
