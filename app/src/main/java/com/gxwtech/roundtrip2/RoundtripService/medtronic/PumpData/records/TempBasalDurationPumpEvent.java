package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class TempBasalDurationPumpEvent extends TimeStampedRecord {
    private int durationMinutes = 0;
    public TempBasalDurationPumpEvent() { }

    @Override
    public String getShortTypeName() {
        return "Temp Basal Duration";
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data,2)) {
            return false;
        }
        durationMinutes = asUINT8(data[1]) * 30;
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        durationMinutes = in.getInt("durationMinutes",0);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putInt("durationMinutes",durationMinutes);
    }


}
