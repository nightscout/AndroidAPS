package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType;
import info.nightscout.androidaps.plugins.pump.insight.ids.BolusTypeIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class BolusDeliveredEvent extends HistoryEvent {

    private BolusType bolusType;
    private int startHour;
    private int startMinute;
    private int startSecond;
    private double immediateAmount;
    private double extendedAmount;
    private int duration;
    private int bolusID;

    @Override
    public void parse(ByteBuf byteBuf) {
        bolusType = BolusTypeIDs.IDS.getType(byteBuf.readUInt16LE());
        byteBuf.shift(1);
        startHour = BOCUtil.parseBOC(byteBuf.readByte());
        startMinute = BOCUtil.parseBOC(byteBuf.readByte());
        startSecond = BOCUtil.parseBOC(byteBuf.readByte());
        immediateAmount = byteBuf.readUInt16Decimal();
        extendedAmount = byteBuf.readUInt16Decimal();
        duration = byteBuf.readUInt16LE();
        byteBuf.shift(2);
        bolusID = byteBuf.readUInt16LE();
    }

    public BolusType getBolusType() {
        return bolusType;
    }

    public int getStartHour() {
        return startHour;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getStartSecond() {
        return startSecond;
    }

    public double getImmediateAmount() {
        return immediateAmount;
    }

    public double getExtendedAmount() {
        return extendedAmount;
    }

    public int getDuration() {
        return duration;
    }

    public int getBolusID() {
        return bolusID;
    }
}
