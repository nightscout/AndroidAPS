package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 7/16/16.
 */
public class Unknown7ByteEvent1 extends TimeStampedRecord {

    public Unknown7ByteEvent1() {
    }


    @Override
    public String getShortTypeName() {
        return "Unknown7Byte1";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
