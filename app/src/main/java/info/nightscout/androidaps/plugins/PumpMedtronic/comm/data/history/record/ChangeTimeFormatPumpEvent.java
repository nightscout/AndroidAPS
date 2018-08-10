package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class ChangeTimeFormatPumpEvent extends TimeStampedRecord {

    public ChangeTimeFormatPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Time Format";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
