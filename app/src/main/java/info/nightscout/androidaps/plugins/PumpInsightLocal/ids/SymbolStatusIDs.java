package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.SymbolStatus;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class SymbolStatusIDs {

    public static final IDStorage<SymbolStatus, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(SymbolStatus.FULL, 31);
        IDS.put(SymbolStatus.LOW, 227);
        IDS.put(SymbolStatus.EMPTY, 252);
    }

}
