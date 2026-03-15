package cn.nukkit.network.protocol;

/**
 * Created on 15-10-13.
 */
public class TextPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TEXT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static final byte TYPE_RAW = 0;
    public static final byte TYPE_CHAT = 1;
    public static final byte TYPE_TRANSLATION = 2;
    public static final byte TYPE_POPUP = 3;
    public static final byte TYPE_TIP = 4;
    public static final byte TYPE_SYSTEM = 5;
    public static final byte TYPE_WHISPER = 6;
    public static final byte TYPE_ANNOUNCEMENT = 7;

    public byte type;
    public String source = "";
    public String message = "";
    public String[] parameters = new String[0];

    @Override
    public void decode() {
        this.type = (byte) getByte();
        switch (type) {
            case TYPE_POPUP:
            case TYPE_CHAT:
                this.source = this.getString();
                this.message = this.getString();
                break;
            case TYPE_WHISPER:
            case TYPE_ANNOUNCEMENT:
                if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
                    this.source = this.getString();
                }
            case TYPE_RAW:
            case TYPE_TIP:
            case TYPE_SYSTEM:
                this.message = this.getString();
                break;

            case TYPE_TRANSLATION:
                this.message = this.getString();
                int count = (ProtocolInfo.isBefore0160(this.protocol)) ? this.getByte() : (int) this.getUnsignedVarInt();
                this.parameters = new String[count];
                for (int i = 0; i < count; i++) {
                    this.parameters[i] = this.getString();
                }
        }
    }

    @Override
    public void encode() {
        this.reset();
        byte type = this.type;
        if (ProtocolInfo.isLegacyProtocol(this.protocol) && type == TYPE_ANNOUNCEMENT) {
            type = TYPE_SYSTEM;
        }
        if ((ProtocolInfo.isBefore0160(this.protocol)) && (type == TYPE_WHISPER || type == TYPE_ANNOUNCEMENT)) {
            type = TYPE_SYSTEM;
        }
        this.putByte(type);
        switch (type) {
            case TYPE_POPUP:
            case TYPE_CHAT:
                this.putString(this.source);
                this.putString(this.message);
                break;
            case TYPE_WHISPER:
            case TYPE_ANNOUNCEMENT:
                if (!ProtocolInfo.isLegacyProtocol(this.protocol)) {
                    this.putString(this.source);
                }
            case TYPE_RAW:
            case TYPE_TIP:
            case TYPE_SYSTEM:
                this.putString(this.message);
                break;

            case TYPE_TRANSLATION:
                this.putString(this.message);
                if ((ProtocolInfo.isBefore0160(this.protocol))) {
                    this.putByte((byte) this.parameters.length);
                } else {
                    this.putUnsignedVarInt(this.parameters.length);
                }
                for (String parameter : this.parameters) {
                    this.putString(parameter);
                }
        }
    }

}
