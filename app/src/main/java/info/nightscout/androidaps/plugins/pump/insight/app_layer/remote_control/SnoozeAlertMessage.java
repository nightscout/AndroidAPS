package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class SnoozeAlertMessage extends AppLayerMessage {

    private int alertID;

    public SnoozeAlertMessage() {
        super(MessagePriority.NORMAL, false, true, Service.REMOTE_CONTROL);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(alertID);
        return byteBuf;
    }

    public void setAlertID(int alertID) {
        this.alertID = alertID;
    }
}
