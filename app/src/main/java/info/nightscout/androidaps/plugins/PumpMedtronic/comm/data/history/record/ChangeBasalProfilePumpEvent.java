package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class ChangeBasalProfilePumpEvent extends TimeStampedRecord {

    public ChangeBasalProfilePumpEvent() {
    }


    @Override
    public int getLength() {
        return 152;
    }


    @Override
    public String getShortTypeName() {
        return "Ch Basal Profile";
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }

}
