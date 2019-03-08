package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class OperatingModeIDs {

    public static final IDStorage<OperatingMode, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(OperatingMode.STOPPED, 31);
        IDS.put(OperatingMode.STARTED, 227);
        IDS.put(OperatingMode.PAUSED, 252);
    }

}
