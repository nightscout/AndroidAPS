package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.AlertCategory;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class AlertCategoryIDs {

    public static final IDStorage<AlertCategory, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(AlertCategory.REMINDER, 227);
        IDS.put(AlertCategory.MAINTENANCE, 252);
        IDS.put(AlertCategory.WARNING, 805);
        IDS.put(AlertCategory.ERROR, 826);
    }

}
