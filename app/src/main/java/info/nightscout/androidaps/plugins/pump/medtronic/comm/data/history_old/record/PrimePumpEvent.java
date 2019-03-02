package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

@Deprecated
public class PrimePumpEvent extends TimeStampedRecord {

    private double amount = 0.0;
    private double programmedAmount = 0.0;
    private String primeType = "unknown";


    public PrimePumpEvent() {
    }


    @Override
    public int getLength() {
        return 10;
    }


    @Override
    public String getShortTypeName() {
        return "Prime Pump";
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        if (!simpleParse(data, 5)) {
            return false;
        }
        amount = (double)(asUINT8(data[4]) << 2) / 40.0;
        programmedAmount = (double)(asUINT8(data[2]) << 2) / 40.0;
        primeType = programmedAmount == 0 ? "manual" : "fixed";
        return true;
    }


    @Override
    public boolean readFromBundle(Bundle in) {
        amount = in.getDouble("amount", 0.0);
        programmedAmount = in.getDouble("programmedAmount", 0);
        primeType = in.getString("primeType", "unknown");
        return super.readFromBundle(in);
    }


    @Override
    public void writeToBundle(Bundle in) {
        in.putDouble("amount", amount);
        in.putDouble("programmedAmount", programmedAmount);
        in.putString("primeType", primeType);
        super.writeToBundle(in);
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
