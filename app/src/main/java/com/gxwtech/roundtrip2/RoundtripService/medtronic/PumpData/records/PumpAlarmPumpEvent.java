package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class PumpAlarmPumpEvent extends TimeStampedRecord {
    private int rawtype = 0;
    public PumpAlarmPumpEvent() {
    }

    @Override
    public int getLength() { return 9; }

    @Override
    public String getShortTypeName() {
        return "Pump Alarm";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data,4)) {
            return false;
        }
        rawtype = asUINT8(data[1]);
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        rawtype = in.getInt("rawtype",0);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        in.putInt("rawtype",rawtype);
        super.writeToBundle(in);
    }

}
