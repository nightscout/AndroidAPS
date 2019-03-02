package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeSensorRateOfChangeAlertSetupPumpEvent extends TimeStampedRecord {

    public ChangeSensorRateOfChangeAlertSetupPumpEvent() {
    }


    @Override
    public int getLength() {
        return 12;
    }


    @Override
    public String getShortTypeName() {
        return "Ch Sensor ROC Alert";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
