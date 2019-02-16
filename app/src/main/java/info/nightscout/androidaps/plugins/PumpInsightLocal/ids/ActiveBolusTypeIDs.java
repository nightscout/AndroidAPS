package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.BolusType;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class ActiveBolusTypeIDs {

    public static final IDStorage<BolusType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(BolusType.STANDARD, 227);
        IDS.put(BolusType.EXTENDED, 252);
        IDS.put(BolusType.MULTIWAVE, 805);
    }

}
