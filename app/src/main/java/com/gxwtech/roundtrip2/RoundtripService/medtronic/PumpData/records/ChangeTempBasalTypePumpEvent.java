package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeTempBasalTypePumpEvent extends TimeStampedRecord {
    private boolean isPercent=false; // either absolute or percent
    public ChangeTempBasalTypePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch Temp Basal Type";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data,2)) {
            return false;
        }
        if (asUINT8(data[1])==1) {
            isPercent = true;
        } else {
            isPercent = false;
        }
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        isPercent = in.getBoolean("isPercent",false);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        in.putBoolean("isPercent",isPercent);
        super.writeToBundle(in);
    }

}
