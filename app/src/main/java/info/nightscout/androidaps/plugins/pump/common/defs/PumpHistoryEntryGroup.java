package info.nightscout.androidaps.plugins.pump.common.defs;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.resources.ResourceHelper;


/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 * <p>
 * Author: Andy {andy.rozman@gmail.com}
 */

public enum PumpHistoryEntryGroup {

    All(R.string.medtronic_history_group_all),
    Bolus(R.string.danar_history_bolus),
    Basal(R.string.medtronic_history_group_basal),
    Prime(R.string.danar_history_prime),
    Configuration(R.string.medtronic_history_group_configuration),
    Alarm(R.string.danar_history_alarm),
    Glucose(R.string.danar_history_glucose),
    Notification(R.string.medtronic_history_group_notification),
    Statistic(R.string.medtronic_history_group_statistic),
    Unknown(R.string.medtronic_history_group_unknown),
    ;

    private int resourceId;
    private String translated;

    private static List<PumpHistoryEntryGroup> translatedList;

    PumpHistoryEntryGroup(int resourceId) {
        this.resourceId = resourceId;
    }

    private static void doTranslation(ResourceHelper resourceHelper) {
        translatedList = new ArrayList<>();

        for (PumpHistoryEntryGroup pumpHistoryEntryGroup : values()) {
            pumpHistoryEntryGroup.translated = resourceHelper.gs(pumpHistoryEntryGroup.resourceId);
            translatedList.add(pumpHistoryEntryGroup);
        }
    }

    public static List<PumpHistoryEntryGroup> getTranslatedList(ResourceHelper resourceHelper) {
        if (translatedList == null) doTranslation(resourceHelper);
        return translatedList;
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
