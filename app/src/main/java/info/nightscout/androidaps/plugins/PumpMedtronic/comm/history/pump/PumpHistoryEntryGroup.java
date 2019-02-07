package info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by andy on 12/23/18.
 */

public enum PumpHistoryEntryGroup {

    All(R.string.medtronic_history_group_all), //
    Bolus(R.string.danar_history_bolus), //
    Basal(R.string.medtronic_history_group_basal), //
    Prime(R.string.danar_history_prime), //
    Configuration(R.string.medtronic_history_group_configuration), //
    Alarm(R.string.danar_history_alarm), //
    Glucose(R.string.danar_history_glucose), //
    Notification(R.string.medtronic_history_group_notification), //
    Statistic(R.string.medtronic_history_group_statistic),
    Unknown(R.string.medtronic_history_group_unknown), //
    ;

    private int resourceId;
    private String translated;

    public static boolean doNotTranslate = false;

    private static List<PumpHistoryEntryGroup> list;

    static {
        list = new ArrayList<>();

        for (PumpHistoryEntryGroup pumpHistoryEntryGroup : values()) {
            if (doNotTranslate) {
                pumpHistoryEntryGroup.translated = MainApp.gs(pumpHistoryEntryGroup.resourceId);
            }
            list.add(pumpHistoryEntryGroup);
        }
    }


    PumpHistoryEntryGroup(int resourceId) {
        this.resourceId = resourceId;
        // this.translated = MainApp.gs(resourceId);
    }


    public static List<PumpHistoryEntryGroup> getList() {
        return list;
    }


    public int getResourceId() {
        return resourceId;
    }


    public String getTranslated() {
        return translated;
    }


    public String toString() {
        return this.translated;
    }
}
