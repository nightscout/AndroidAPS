package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class AlertStatusIDs {

    public static final IDStorage<AlertStatus, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(AlertStatus.ACTIVE, 31);
        IDS.put(AlertStatus.SNOOZED, 227);
    }

}
