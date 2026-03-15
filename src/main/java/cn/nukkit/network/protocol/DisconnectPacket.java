package cn.nukkit.network.protocol;

/**
 * Created by on 15-10-12.
 */
public class DisconnectPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.DISCONNECT_PACKET;

    public boolean hideDisconnectionScreen = false;
    public String message;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.hideDisconnectionScreen = false;
            this.message = this.getString();
        } else {
            this.hideDisconnectionScreen = this.getBoolean();
            this.message = this.feof() ? "" : this.getString();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if ((ProtocolInfo.isBefore0160(this.protocol))) {
            this.putString(this.message);
            return;
        }
        this.putBoolean(this.hideDisconnectionScreen);
        if (ProtocolInfo.isLegacyProtocol(this.protocol) || !this.hideDisconnectionScreen) {
            this.putString(this.message);
        }
    }


}
