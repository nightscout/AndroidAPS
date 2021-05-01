package info.nightscout.androidaps.plugins.pump.common.defs;

import java.util.ArrayList;
import java.util.List;


import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.utils.resources.ResourceHelper;


/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 * <p>
 * Author: Andy {andy.rozman@gmail.com}
 */

public enum PumpHistoryEntryGroup {

    All(R.string.history_group_all),
    Bolus(R.string.history_group_bolus),
    Basal(R.string.history_group_basal),
    Prime(R.string.history_group_prime),
    Configuration(R.string.history_group_configuration),
    Alarm(R.string.history_group_alarm),
    Glucose(R.string.history_group_glucose),
    Notification(R.string.history_group_notification),
    Statistic(R.string.history_group_statistic),
    Unknown(R.string.history_group_unknown),
    ;

    private final int resourceId;
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
