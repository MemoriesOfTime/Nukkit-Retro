package cn.nukkit.network.protocol;

// A wild TransferPacket appeared!
public class TransferPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.TRANSFER_PACKET;

    public String address; // Server address
    public int port = 19132; // Server port
    public byte version = 4; // IPv?

    @Override
    public void decode() {
        this.address = this.getString();
        this.port = (short) this.getLShort();
    }

    @Override
    public void encode() {
        this.reset();
        if(ProtocolInfo.isBefore0130(this.protocol)){
            this.putByte(this.version);
            if(this.version == 4){
                String[] parts = address.split("\\.");
                for (String seg : parts) {
                    int val = Integer.parseInt(seg);
                    int rev = (~val) & 0xff;
                    this.putByte((byte) rev);
                }
                this.putShort(port);
            }else if(this.version == 6){
                //IPv6
            }
        }else{
            this.putString(address);
            this.putLShort(port);
        }
    }

    @Override
    public byte pid() {
        return ProtocolInfo.TRANSFER_PACKET;
    }
}
