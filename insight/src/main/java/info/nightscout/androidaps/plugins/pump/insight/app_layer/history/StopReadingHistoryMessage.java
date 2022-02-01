package info.nightscout.androidaps.plugins.pump.insight.app_layer.history;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;

public class StopReadingHistoryMessage extends AppLayerMessage {

    public StopReadingHistoryMessage() {
        super(MessagePriority.NORMAL, false, false, Service.HISTORY);
    }
}
