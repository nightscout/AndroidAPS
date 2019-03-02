package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeSensorSetup2PumpEvent extends TimeStampedRecord {

    public ChangeSensorSetup2PumpEvent() {
    }


    @Override
    public int getLength() {
        return 37;
    }


    @Override
    public String getShortTypeName() {
        return "Ch Sensor Setup2";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }

}
