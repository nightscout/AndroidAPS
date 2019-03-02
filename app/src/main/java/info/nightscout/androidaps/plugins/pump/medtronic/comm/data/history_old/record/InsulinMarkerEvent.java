package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 7/16/16.
 */
public class InsulinMarkerEvent extends TimeStampedRecord {

    public InsulinMarkerEvent() {
    }


    @Override
    public int getLength() {
        return 8;
    }


    /*
     * Darrell Wright:
     * it is a manual entry of a bolus that the pump didn't deliver, so opcode, timestamp and at least a number to
     * represent the units of insulin
     */

    @Override
    public String getShortTypeName() {
        return "UnknownInsulin";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
