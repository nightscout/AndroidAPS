package info.nightscout.androidaps.plugins.pump.insight.app_layer.connection;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ActivateServiceMessage extends AppLayerMessage {

    private byte serviceID;
    private short version;
    private byte[] servicePassword;

    public ActivateServiceMessage() {
        super(MessagePriority.NORMAL, false, false, Service.CONNECTION);
    }

    protected void parse(ByteBuf byteBuf) {
        serviceID = byteBuf.readByte();
        version = byteBuf.readShort();
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(19);
        byteBuf.putByte(serviceID);
        byteBuf.putShort(version);
        byteBuf.putBytes(servicePassword);
        return byteBuf;
    }

    public byte getServiceID() {
        return this.serviceID;
    }

    public short getVersion() {
        return this.version;
    }

    public void setServiceID(byte serviceID) {
        this.serviceID = serviceID;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public void setServicePassword(byte[] servicePassword) {
        this.servicePassword = servicePassword;
    }
}
