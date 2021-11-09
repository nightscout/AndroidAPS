package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class DateTimeChangedEvent extends HistoryEvent {

    private int beforeYear;
    private int beforeMonth;
    private int beforeDay;
    private int beforeHour;
    private int beforeMinute;
    private int beforeSecond;

    @Override
    public void parse(ByteBuf byteBuf) {
        beforeYear = BOCUtil.parseBOC(byteBuf.readByte()) * 100 + BOCUtil.parseBOC(byteBuf.readByte());
        beforeMonth = BOCUtil.parseBOC(byteBuf.readByte());
        beforeDay = BOCUtil.parseBOC(byteBuf.readByte());
        byteBuf.shift(1);
        beforeHour = BOCUtil.parseBOC(byteBuf.readByte());
        beforeMinute = BOCUtil.parseBOC(byteBuf.readByte());
        beforeSecond = BOCUtil.parseBOC(byteBuf.readByte());
    }

    public int getBeforeYear() {
        return beforeYear;
    }

    public int getBeforeMonth() {
        return beforeMonth;
    }

    public int getBeforeDay() {
        return beforeDay;
    }

    public int getBeforeHour() {
        return beforeHour;
    }

    public int getBeforeMinute() {
        return beforeMinute;
    }

    public int getBeforeSecond() {
        return beforeSecond;
    }
}
