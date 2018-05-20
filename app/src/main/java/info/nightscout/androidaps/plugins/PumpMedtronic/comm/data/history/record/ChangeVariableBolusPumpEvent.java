package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeVariableBolusPumpEvent extends TimeStampedRecord {
    public ChangeVariableBolusPumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Ch Var. Bolus";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
