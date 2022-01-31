package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.HistoryReadingDirection;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class HistoryReadingDirectionIDs {

    public static final IDStorage<HistoryReadingDirection, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(HistoryReadingDirection.FORWARD, 31);
        IDS.put(HistoryReadingDirection.BACKWARD, 227);
    }

}
