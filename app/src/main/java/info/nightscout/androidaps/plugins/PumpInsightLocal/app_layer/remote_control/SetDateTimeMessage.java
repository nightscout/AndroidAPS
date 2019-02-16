package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.remote_control;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.PumpTime;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class SetDateTimeMessage extends AppLayerMessage {

    private PumpTime pumpTime;

    public SetDateTimeMessage() {
        super(MessagePriority.NORMAL, false, true, Service.CONFIGURATION);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(7);
        byteBuf.putUInt16LE(pumpTime.getYear());
        byteBuf.putUInt8((short) pumpTime.getMonth());
        byteBuf.putUInt8((short) pumpTime.getDay());
        byteBuf.putUInt8((short) pumpTime.getHour());
        byteBuf.putUInt8((short) pumpTime.getMinute());
        byteBuf.putUInt8((short) pumpTime.getSecond());
        return byteBuf;
    }

    public void setPumpTime(PumpTime pumpTime) {
        this.pumpTime = pumpTime;
    }
}
