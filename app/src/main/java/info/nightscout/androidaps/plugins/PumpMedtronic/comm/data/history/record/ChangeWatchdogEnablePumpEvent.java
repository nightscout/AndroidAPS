package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeWatchdogEnablePumpEvent extends TimeStampedRecord {

    public ChangeWatchdogEnablePumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Watchdog Enable";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
