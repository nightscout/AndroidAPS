package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class AlertTypeIDs {

    public static final IDStorage<AlertType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(AlertType.REMINDER_01, 31);
        IDS.put(AlertType.REMINDER_02, 227);
        IDS.put(AlertType.REMINDER_03, 252);
        IDS.put(AlertType.REMINDER_04, 805);
        IDS.put(AlertType.REMINDER_07, 826);
        IDS.put(AlertType.WARNING_31, 966);
        IDS.put(AlertType.WARNING_32, 985);
        IDS.put(AlertType.WARNING_33, 1354);
        IDS.put(AlertType.WARNING_34, 1365);
        IDS.put(AlertType.WARNING_36, 1449);
        IDS.put(AlertType.WARNING_38, 1462);
        IDS.put(AlertType.WARNING_39, 1647);
        IDS.put(AlertType.MAINTENANCE_20, 1648);
        IDS.put(AlertType.MAINTENANCE_21, 1676);
        IDS.put(AlertType.MAINTENANCE_22, 1683);
        IDS.put(AlertType.MAINTENANCE_23, 6182);
        IDS.put(AlertType.MAINTENANCE_24, 6201);
        IDS.put(AlertType.MAINTENANCE_25, 6341);
        IDS.put(AlertType.MAINTENANCE_26, 6362);
        IDS.put(AlertType.MAINTENANCE_27, 6915);
        IDS.put(AlertType.MAINTENANCE_28, 6940);
        IDS.put(AlertType.MAINTENANCE_29, 7136);
        IDS.put(AlertType.MAINTENANCE_30, 7167);
        IDS.put(AlertType.ERROR_6, 7532);
        IDS.put(AlertType.ERROR_10, 7539);
        IDS.put(AlertType.ERROR_13, 7567);
    }

}
