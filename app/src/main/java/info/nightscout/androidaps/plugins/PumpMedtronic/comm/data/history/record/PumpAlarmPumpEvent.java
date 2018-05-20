package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;


import android.os.Bundle;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.PumpModel;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class PumpAlarmPumpEvent extends TimeStampedRecord {
    private int rawtype = 0;

    public PumpAlarmPumpEvent() {
    }

    @Override
    public int getLength() {
        return 9;
    }

    @Override
    public String getShortTypeName() {
        return "Pump Alarm";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data, 4)) {
            return false;
        }
        rawtype = asUINT8(data[1]);
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        rawtype = in.getInt("rawtype", 0);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        in.putInt("rawtype", rawtype);
        super.writeToBundle(in);
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
