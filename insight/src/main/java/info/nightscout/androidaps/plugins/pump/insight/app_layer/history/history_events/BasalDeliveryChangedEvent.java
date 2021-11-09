package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class BasalDeliveryChangedEvent extends HistoryEvent {

    private double oldBasalRate;
    private double newBasalRate;

    @Override
    public void parse(ByteBuf byteBuf) {
        oldBasalRate = byteBuf.readUInt32Decimal1000();
        newBasalRate = byteBuf.readUInt32Decimal1000();
    }

    public double getOldBasalRate() {
        return oldBasalRate;
    }

    public double getNewBasalRate() {
        return newBasalRate;
    }
}
