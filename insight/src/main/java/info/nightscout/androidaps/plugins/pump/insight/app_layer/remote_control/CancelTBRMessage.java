package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;

public class CancelTBRMessage extends AppLayerMessage {
    public CancelTBRMessage() {
        super(MessagePriority.HIGHER, false, false, Service.REMOTE_CONTROL);
    }
}
