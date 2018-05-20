package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
