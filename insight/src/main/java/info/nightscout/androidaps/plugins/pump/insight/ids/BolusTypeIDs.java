package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class BolusTypeIDs {

    public static final IDStorage<BolusType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(BolusType.STANDARD, 31);
        IDS.put(BolusType.EXTENDED, 227);
        IDS.put(BolusType.MULTIWAVE, 252);
    }

}
