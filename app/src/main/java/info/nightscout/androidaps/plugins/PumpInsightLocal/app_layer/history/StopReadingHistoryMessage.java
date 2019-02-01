package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.history;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class StopReadingHistoryMessage extends AppLayerMessage {

    public StopReadingHistoryMessage() {
        super(MessagePriority.NORMAL, false, false, Service.HISTORY);
    }
}
