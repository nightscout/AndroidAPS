package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.history.HistoryReadingDirection;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class HistoryReadingDirectionIDs {

    public static final IDStorage<HistoryReadingDirection, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(HistoryReadingDirection.FORWARD, 31);
        IDS.put(HistoryReadingDirection.BACKWARD, 227);
    }

}
