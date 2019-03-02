package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.PumpTimeStamp;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeFormat;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

public class BolusNormalPumpEvent extends TimeStampedRecord {

    private final static String TAG = "BolusNormalPumpEvent";

    private double programmedAmount = 0.0;
    private double deliveredAmount = 0.0;
    private int duration = 0;
    private double unabsorbedInsulinTotal = 0.0;
    private String bolusType = "Unset";


    public BolusNormalPumpEvent() {
    }


    @Override
    public int getLength() {
        return isLargerFormat() ? 13 : 9;
    }


    @Override
    public String getShortTypeName() {
        return "Normal Bolus";
    }


    private double insulinDecode(int a, int b) {
        return ((a << 8) + b) / 40.0;
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        if (getLength() > data.length) {
            return false;
        }
        if (MedtronicDeviceType.isLargerFormat(model)) {
            programmedAmount = insulinDecode(asUINT8(data[1]), asUINT8(data[2]));
            deliveredAmount = insulinDecode(asUINT8(data[3]), asUINT8(data[4]));
            unabsorbedInsulinTotal = insulinDecode(asUINT8(data[5]), asUINT8(data[6]));
            duration = asUINT8(data[7]) * 30;
            try {
                timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, 8));
            } catch (org.joda.time.IllegalFieldValueException e) {
                return false;
            }
        } else {
            programmedAmount = asUINT8(data[1]) / 10.0f;
            deliveredAmount = asUINT8(data[2]) / 10.0f;
            duration = asUINT8(data[3]) * 30;
            unabsorbedInsulinTotal = 0;
            try {
                timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, 4));
            } catch (org.joda.time.IllegalFieldValueException e) {
                return false;
            }

        }

        bolusType = (duration > 0) ? "square" : "normal";
        return true;
    }


    @Override
    public boolean readFromBundle(Bundle in) {
        programmedAmount = in.getDouble("programmedAmount", 0.0);
        deliveredAmount = in.getDouble("deliveredAmount", 0.0);
        duration = in.getInt("duration", 0);
        unabsorbedInsulinTotal = in.getDouble("unabsorbedInsulinTotal", 0.0);
        bolusType = in.getString("bolusType", "Unset");
        return super.readFromBundle(in);
    }


    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putDouble("programmedAmount", programmedAmount);
        in.putDouble("deliveredAmount", deliveredAmount);
        in.putInt("duration", duration);
        in.putDouble("unabsorbedInsulinTotal", unabsorbedInsulinTotal);
        in.putString("bolusType", bolusType);
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
