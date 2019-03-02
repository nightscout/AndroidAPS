package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

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
