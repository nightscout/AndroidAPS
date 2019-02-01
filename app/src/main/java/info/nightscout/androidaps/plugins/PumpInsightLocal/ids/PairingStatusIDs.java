package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.satl.PairingStatus;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class PairingStatusIDs {

    public static final IDStorage<PairingStatus, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(PairingStatus.PENDING, 1683);
        IDS.put(PairingStatus.REJECTED, 7850);
        IDS.put(PairingStatus.CONFIRMED, 11835);
    }

}
