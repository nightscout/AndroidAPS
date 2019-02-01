package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.BolusType;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class BolusTypeIDs {

    public static final IDStorage<BolusType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(BolusType.STANDARD, 31);
        IDS.put(BolusType.EXTENDED, 227);
        IDS.put(BolusType.MULTIWAVE, 252);
    }

}
