package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.BasalDeliveryChangedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.BolusDeliveredEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.BolusProgrammedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.CannulaFilledEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.CartridgeInsertedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.CartridgeRemovedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.DateTimeChangedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.DefaultDateTimeSetEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.EndOfTBREvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.HistoryEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.OccurrenceOfErrorEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.OccurrenceOfMaintenanceEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.OccurrenceOfWarningEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.OperatingModeChangedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.PowerDownEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.PowerUpEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.SniffingDoneEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.StartOfTBREvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.TotalDailyDoseEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.TubeFilledEvent;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class HistoryEventIDs {

    public static final IDStorage<Class<? extends HistoryEvent>, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(BolusDeliveredEvent.class, 917);
        IDS.put(BolusProgrammedEvent.class, 874);
        IDS.put(CannulaFilledEvent.class, 3264);
        IDS.put(DateTimeChangedEvent.class, 165);
        IDS.put(DefaultDateTimeSetEvent.class, 170);
        IDS.put(EndOfTBREvent.class, 771);
        IDS.put(OccurrenceOfErrorEvent.class, 1011);
        IDS.put(OccurrenceOfMaintenanceEvent.class, 1290);
        IDS.put(OccurrenceOfWarningEvent.class, 1360);
        IDS.put(OperatingModeChangedEvent.class, 195);
        IDS.put(PowerUpEvent.class, 15);
        IDS.put(PowerDownEvent.class, 51);
        IDS.put(SniffingDoneEvent.class, 102);
        IDS.put(StartOfTBREvent.class, 240);
        IDS.put(TotalDailyDoseEvent.class, 960);
        IDS.put(TubeFilledEvent.class, 105);
        IDS.put(CartridgeInsertedEvent.class, 60);
        IDS.put(CartridgeRemovedEvent.class, 85);
        IDS.put(BasalDeliveryChangedEvent.class, 204);
    }

}
