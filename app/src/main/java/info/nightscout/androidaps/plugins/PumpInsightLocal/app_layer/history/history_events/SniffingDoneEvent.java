package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.history.history_events;


import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class SniffingDoneEvent extends HistoryEvent {

    private double amount;

    @Override
    public void parse(ByteBuf byteBuf) {
        amount = byteBuf.readUInt16Decimal();
    }

    public double getAmount() {
        return amount;
    }
}
