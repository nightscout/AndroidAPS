package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.history.history_events;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.AlertType;
import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.AlertTypeIncrementalIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public abstract class OccurrenceOfAlertEvent extends HistoryEvent {

    private AlertType alertType;
    private int alertID;

    @Override
    public void parse(ByteBuf byteBuf) {
        alertType = AlertTypeIncrementalIDs.IDS.getType(byteBuf.readUInt16LE());
        alertID = byteBuf.readUInt16LE();
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public int getAlertID() {
        return alertID;
    }
}
