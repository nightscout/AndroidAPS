package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeTempBasalTypePumpEvent extends TimeStampedRecord {

    private boolean isPercent = false; // either absolute or percent


    public ChangeTempBasalTypePumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Temp Basal Type";
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        if (!simpleParse(data, 2)) {
            return false;
        }
        if (asUINT8(data[1]) == 1) {
            isPercent = true;
        } else {
            isPercent = false;
        }
        return true;
    }


    @Override
    public boolean readFromBundle(Bundle in) {
        isPercent = in.getBoolean("isPercent", false);
        return super.readFromBundle(in);
    }


    @Override
    public void writeToBundle(Bundle in) {
        in.putBoolean("isPercent", isPercent);
        super.writeToBundle(in);
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
