package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class OperatingModeChangedEvent extends HistoryEvent {

    private OperatingMode oldValue;
    private OperatingMode newValue;

    @Override
    public void parse(ByteBuf byteBuf) {
        oldValue = OperatingMode.Companion.fromId(byteBuf.readUInt16LE());
        newValue = OperatingMode.Companion.fromId(byteBuf.readUInt16LE());
    }


    public OperatingMode getOldValue() {
        return oldValue;
    }

    public OperatingMode getNewValue() {
        return newValue;
    }
}
