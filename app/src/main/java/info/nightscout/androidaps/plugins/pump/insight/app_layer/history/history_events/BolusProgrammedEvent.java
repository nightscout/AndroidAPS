package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType;
import info.nightscout.androidaps.plugins.pump.insight.ids.BolusTypeIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class BolusProgrammedEvent extends HistoryEvent {

    private BolusType bolusType;
    private double immediateAmount;
    private double extendedAmount;
    private int duration;
    private int bolusID;

    @Override
    public void parse(ByteBuf byteBuf) {
        bolusType = BolusTypeIDs.IDS.getType(byteBuf.readUInt16LE());
        immediateAmount = byteBuf.readUInt16Decimal();
        extendedAmount = byteBuf.readUInt16Decimal();
        duration = byteBuf.readUInt16LE();
        byteBuf.shift(4);
        bolusID = byteBuf.readUInt16LE();
    }


    public BolusType getBolusType() {
        return bolusType;
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
