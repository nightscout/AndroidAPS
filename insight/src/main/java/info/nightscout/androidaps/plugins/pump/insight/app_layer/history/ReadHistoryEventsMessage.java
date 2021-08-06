package info.nightscout.androidaps.plugins.pump.insight.app_layer.history;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.HistoryEvent;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ReadHistoryEventsMessage extends AppLayerMessage {

    private List<HistoryEvent> historyEvents;

    public ReadHistoryEventsMessage() {
        super(MessagePriority.NORMAL, true, false, Service.HISTORY);
    }

    @Override
    protected void parse(ByteBuf byteBuf) throws Exception {
        historyEvents = new ArrayList<>();
        byteBuf.shift(2);
        int frameCount = byteBuf.readUInt16LE();
        for (int i = 0; i < frameCount; i++) {
            int length = byteBuf.readUInt16LE();
            historyEvents.add(HistoryEvent.deserialize(ByteBuf.from(byteBuf.readBytes(length))));
        }
    }

    public List<HistoryEvent> getHistoryEvents() {
        return historyEvents;
    }
}
