package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

public class ChangeOtherDeviceIDPumpEvent extends TimeStampedRecord {

    public ChangeOtherDeviceIDPumpEvent() {
    }


    @Override
    public int getLength() {
        return 37;
    }


    @Override
    public String getShortTypeName() {
        return "Ch Other Dev ID";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
