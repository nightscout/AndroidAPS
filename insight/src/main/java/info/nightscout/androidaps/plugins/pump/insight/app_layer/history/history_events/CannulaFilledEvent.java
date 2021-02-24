package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class CannulaFilledEvent extends HistoryEvent {

    private double amount;

    @Override
    public void parse(ByteBuf byteBuf) {
        amount = byteBuf.readUInt16Decimal();
    }

    public double getAmount() {
        return amount;
    }
}
