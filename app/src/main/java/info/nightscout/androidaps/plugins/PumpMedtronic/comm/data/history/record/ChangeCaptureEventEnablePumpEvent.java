package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
