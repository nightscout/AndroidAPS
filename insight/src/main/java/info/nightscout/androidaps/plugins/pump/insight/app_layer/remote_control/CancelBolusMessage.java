package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class CancelBolusMessage extends AppLayerMessage {

    private int bolusID;

    public CancelBolusMessage() {
        super(MessagePriority.HIGHEST, false, true, Service.REMOTE_CONTROL);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(bolusID);
        return byteBuf;
    }

    public void setBolusID(int bolusID) {
        this.bolusID = bolusID;
    }
}
