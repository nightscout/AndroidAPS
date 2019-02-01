package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class StartOfTBREvent extends HistoryEvent {

    private int amount;
    private int duration;

    @Override
    public void parse(ByteBuf byteBuf) {
        amount = byteBuf.readUInt16LE();
        duration = byteBuf.readUInt16LE();
    }

    public int getAmount() {
        return amount;
    }

    public int getDuration() {
        return duration;
    }
}
