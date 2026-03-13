package cn.nukkit.network.protocol;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.Zlib;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Created by on 15-10-13.
 */
public class LoginPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.LOGIN_PACKET;

    public String username;
    public int clientProtocol;
    @ProtocolInfo.SinceProtocol(ProtocolInfo.v0_16_0)
    public byte gameEdition;
    public UUID clientUUID;
    public long clientId;
    public String serverAddress;
    public String clientSecret;

    public Skin skin;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (ProtocolInfo.getPacketPoolProtocol(this.protocol) == ProtocolInfo.v0_14_2) {
            this.username = this.getLegacyString();
            this.clientProtocol = this.getInt();
            this.getInt(); // secondary protocol field, kept for compatibility with old login payload
            this.clientId = this.getLong();
            this.clientUUID = this.getUUID();
            this.serverAddress = this.getLegacyString();
            this.clientSecret = this.getLegacyString();
            this.skin = this.decodeLegacySkin();
            return;
        }

        this.clientProtocol = this.getInt();
        if (Nukkit.DEBUG > 1) {
            Server.getInstance().getLogger().debug("LoginPacket.decode: clientProtocol=" + this.clientProtocol + ", pk.protocol=" + this.protocol);
        }
        if ((this.clientProtocol < ProtocolInfo.v0_16_0)) {
            int zlibLen = this.getInt();
            if (Nukkit.DEBUG > 1) {
                Server.getInstance().getLogger().debug("LoginPacket.decode: 0.15.x format, zlibLen=" + zlibLen);
            }
            byte[] str;
            try {
                str = Zlib.inflate(this.get(zlibLen), 64 * 1024 * 1024);
            } catch (Exception e) {
                Server.getInstance().getLogger().error("Failed to decompress login data for protocol " + this.clientProtocol, e);
                return;
            }
            this.setBuffer(str, 0);
        } else {
            this.gameEdition = (byte) this.getByte();
            if (Nukkit.DEBUG > 1) {
                Server.getInstance().getLogger().debug("LoginPacket.decode: 0.16.0+ format, gameEdition=" + this.gameEdition);
            }
            if (ProtocolInfo.isLegacyProtocol(this.clientProtocol)) {
                byte[] str;
                try {
                    str = Zlib.inflate(this.get((int) this.getUnsignedVarInt()), 64 * 1024 * 1024);
                } catch (Exception e) {
                    Server.getInstance().getLogger().error("Failed to decompress login data for legacy protocol " + this.clientProtocol, e);
                    return;
                }
                this.setBuffer(str, 0);
            } else {
                this.setBuffer(this.getByteArray(), 0);
            }
        }
        decodeChainData();
        decodeSkinData();
    }

    @Override
    public void encode() {

    }

    public int getProtocol() {
        return clientProtocol;
    }

    private void decodeChainData() {
        Map<String, List<String>> map = new Gson().fromJson(new String(this.get(getLInt()), StandardCharsets.UTF_8),
                new TypeToken<Map<String, List<String>>>() {
                }.getType());
        if (map.isEmpty() || !map.containsKey("chain") || map.get("chain").isEmpty()) return;
        List<String> chains = map.get("chain");
        for (String c : chains) {
            JsonObject chainMap = decodeToken(c);
            if (chainMap == null) continue;
            if (chainMap.has("extraData")) {
                JsonObject extra = chainMap.get("extraData").getAsJsonObject();
                if (extra.has("displayName")) this.username = extra.get("displayName").getAsString();
                if (extra.has("identity")) this.clientUUID = UUID.fromString(extra.get("identity").getAsString());
            }
        }
    }

    private void decodeSkinData() {
        JsonObject skinToken = decodeToken(new String(this.get(this.getLInt())));
        String skinId = null;
        if (skinToken.has("ClientRandomId")) this.clientId = skinToken.get("ClientRandomId").getAsLong();
        if (skinToken.has("SkinId")) skinId = skinToken.get("SkinId").getAsString();
        if (skinToken.has("SkinData")) this.skin = this.decodeSkinOrDefault(skinToken.get("SkinData").getAsString(), skinId);
    }

    private JsonObject decodeToken(String token) {
        String[] base = token.split("\\.");
        if (base.length < 2) return null;
        return new Gson().fromJson(new String(Base64.getDecoder().decode(base[1]), StandardCharsets.UTF_8), JsonObject.class);
    }

    private Skin decodeLegacySkin() {
        String skinModel = this.getLegacyString();
        int remaining = Math.max(0, this.getCount() - this.getOffset());

        if (remaining == Skin.SINGLE_SKIN_SIZE || remaining == Skin.DOUBLE_SKIN_SIZE) {
            return this.decodeSkinOrDefault(this.get(remaining), skinModel);
        }

        if (remaining >= 4) {
            int intLength = Binary.readInt(Binary.subBytes(this.getBuffer(), this.getOffset(), 4));
            if ((intLength == Skin.SINGLE_SKIN_SIZE || intLength == Skin.DOUBLE_SKIN_SIZE) && intLength <= remaining - 4) {
                this.setOffset(this.getOffset() + 4);
                return this.decodeSkinOrDefault(this.get(intLength), skinModel);
            }
        }

        int skinLength = remaining >= 2 ? this.getShort() & 0xffff : 0;
        int readable = Math.max(0, this.getCount() - this.getOffset());
        return this.decodeSkinOrDefault(this.get(Math.min(skinLength, readable)), skinModel);
    }

    private String getLegacyString() {
        return new String(this.get(this.getShort() & 0xffff), StandardCharsets.UTF_8);
    }

    private Skin decodeSkinOrDefault(String base64Skin, String skinModel) {
        try {
            return new Skin(base64Skin, skinModel);
        } catch (IllegalArgumentException e) {
            return this.decodeSkinOrDefault(new byte[Skin.SINGLE_SKIN_SIZE], skinModel);
        }
    }

    private Skin decodeSkinOrDefault(byte[] skinData, String skinModel) {
        try {
            return new Skin(skinData, skinModel);
        } catch (IllegalArgumentException e) {
            return new Skin(new byte[Skin.SINGLE_SKIN_SIZE], skinModel);
        }
    }

    @Override
    public Skin getSkin() {
        return this.skin;
    }
}
