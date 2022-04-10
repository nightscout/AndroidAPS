package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.SymbolStatus;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class SymbolStatusIDs {

    public static final IDStorage<SymbolStatus, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(SymbolStatus.FULL, 31);
        IDS.put(SymbolStatus.LOW, 227);
        IDS.put(SymbolStatus.EMPTY, 252);
    }

}
