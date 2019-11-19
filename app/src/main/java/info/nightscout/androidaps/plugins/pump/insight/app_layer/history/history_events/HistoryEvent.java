package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.ids.HistoryEventIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryEvent implements Comparable<HistoryEvent> {
    private static final Logger log = LoggerFactory.getLogger(HistoryEvent.class);

    private int eventYear;
    private int eventMonth;
    private int eventDay;
    private int eventHour;
    private int eventMinute;
    private int eventSecond;
    private long eventPosition;

    public static HistoryEvent deserialize(ByteBuf byteBuf) {
        int eventID = byteBuf.readUInt16LE();
        Class<? extends HistoryEvent> eventClass = HistoryEventIDs.IDS.getType(eventID);
        HistoryEvent event = null;
        if (eventClass == null) event = new HistoryEvent();
        else {
            try {
                event = eventClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                log.error("Unhandled exception", e);
            }
        }
        event.parseHeader(byteBuf);
        event.parse(byteBuf);
        return event;
    }

    public final void parseHeader(ByteBuf byteBuf) {
        eventYear = BOCUtil.parseBOC(byteBuf.readByte()) * 100 + BOCUtil.parseBOC(byteBuf.readByte());
        eventMonth = BOCUtil.parseBOC(byteBuf.readByte());
        eventDay = BOCUtil.parseBOC(byteBuf.readByte());
        byteBuf.shift(1);
        eventHour = BOCUtil.parseBOC(byteBuf.readByte());
        eventMinute = BOCUtil.parseBOC(byteBuf.readByte());
        eventSecond = BOCUtil.parseBOC(byteBuf.readByte());
        eventPosition = byteBuf.readUInt32LE();
    }

    public void parse(ByteBuf byteBuf) {

    }

    public int getEventYear() {
        return eventYear;
    }

    public int getEventMonth() {
        return eventMonth;
    }

    public int getEventDay() {
        return eventDay;
    }

    public int getEventHour() {
        return eventHour;
    }

    public int getEventMinute() {
        return eventMinute;
    }

    public int getEventSecond() {
        return eventSecond;
    }

    public long getEventPosition() {
        return eventPosition;
    }

    @Override
    public int compareTo(HistoryEvent historyEvent) {
        return (int) (eventPosition - historyEvent.eventPosition);
    }
}
