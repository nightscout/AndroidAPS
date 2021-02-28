package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.satl.PairingStatus;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class PairingStatusIDs {

    public static final IDStorage<PairingStatus, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(PairingStatus.PENDING, 1683);
        IDS.put(PairingStatus.REJECTED, 7850);
        IDS.put(PairingStatus.CONFIRMED, 11835);
    }

}
