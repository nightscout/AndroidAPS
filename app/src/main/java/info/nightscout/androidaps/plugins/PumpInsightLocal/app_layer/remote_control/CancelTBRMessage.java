package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.remote_control;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class CancelTBRMessage extends AppLayerMessage {
    public CancelTBRMessage() {
        super(MessagePriority.HIGHER, false, false, Service.REMOTE_CONTROL);
    }
}
