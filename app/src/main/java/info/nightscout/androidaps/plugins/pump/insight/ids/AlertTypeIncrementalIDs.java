package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class AlertTypeIncrementalIDs {

    public static final IDStorage<AlertType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(AlertType.REMINDER_01, 1);
        IDS.put(AlertType.REMINDER_02, 2);
        IDS.put(AlertType.REMINDER_03, 3);
        IDS.put(AlertType.REMINDER_04, 4);
        IDS.put(AlertType.REMINDER_07, 7);
        IDS.put(AlertType.WARNING_31, 31);
        IDS.put(AlertType.WARNING_32, 32);
        IDS.put(AlertType.WARNING_33, 33);
        IDS.put(AlertType.WARNING_34, 34);
        IDS.put(AlertType.WARNING_36, 36);
        IDS.put(AlertType.WARNING_38, 38);
        IDS.put(AlertType.WARNING_39, 39);
        IDS.put(AlertType.MAINTENANCE_20, 20);
        IDS.put(AlertType.MAINTENANCE_21, 21);
        IDS.put(AlertType.MAINTENANCE_22, 22);
        IDS.put(AlertType.MAINTENANCE_23, 23);
        IDS.put(AlertType.MAINTENANCE_24, 24);
        IDS.put(AlertType.MAINTENANCE_25, 25);
        IDS.put(AlertType.MAINTENANCE_26, 26);
        IDS.put(AlertType.MAINTENANCE_27, 27);
        IDS.put(AlertType.MAINTENANCE_28, 28);
        IDS.put(AlertType.MAINTENANCE_29, 29);
        IDS.put(AlertType.MAINTENANCE_30, 30);
        IDS.put(AlertType.ERROR_6, 6);
        IDS.put(AlertType.ERROR_10, 10);
        IDS.put(AlertType.ERROR_13, 13);
    }

}
