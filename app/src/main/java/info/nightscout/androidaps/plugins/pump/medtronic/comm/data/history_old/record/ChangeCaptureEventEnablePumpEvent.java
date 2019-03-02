package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeCaptureEventEnablePumpEvent extends TimeStampedRecord {

    public ChangeCaptureEventEnablePumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Capture Event Ena";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
