package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BatteryType;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class BatteryTypeIDs {

    public static final IDStorage<BatteryType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(BatteryType.ALKALI, 31);
        IDS.put(BatteryType.LITHIUM, 227);
        IDS.put(BatteryType.NI_MH, 252);
    }

}
