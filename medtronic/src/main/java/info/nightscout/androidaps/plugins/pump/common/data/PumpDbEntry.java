package info.nightscout.androidaps.plugins.pump.common.data;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

public class PumpDbEntry {

    long temporaryId;
    PumpType pumpType;
    String serialNumber;
    DetailedBolusInfo detailedBolusInfo;

    public PumpDbEntry(long temporaryId, PumpType pumpType, String serialNumber, DetailedBolusInfo detailedBolusInfo) {
        this.temporaryId = temporaryId;
        this.pumpType = pumpType;
        this.serialNumber = serialNumber;
        this.detailedBolusInfo = detailedBolusInfo;
    }


}
