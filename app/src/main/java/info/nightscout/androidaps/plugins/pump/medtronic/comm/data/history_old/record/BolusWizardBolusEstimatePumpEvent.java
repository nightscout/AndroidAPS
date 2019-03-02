package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

public class BolusWizardBolusEstimatePumpEvent extends TimeStampedRecord {

    private int carbohydrates;
    private int bloodGlucose;
    private double foodEstimate;
    private double correctionEstimate;
    private double bolusEstimate;
    private double unabsorbedInsulinTotal;
    private int bgTargetLow;
    private int bgTargetHigh;
    private int insulinSensitivity;
    private double carbRatio;


    public BolusWizardBolusEstimatePumpEvent() {
        correctionEstimate = (double)0.0;
        bloodGlucose = 0;
        carbohydrates = 0;
        carbRatio = 0.0;
        insulinSensitivity = 0;
        bgTargetLow = 0;
        bgTargetHigh = 0;
        bolusEstimate = 0.0;
        foodEstimate = 0.0;
        unabsorbedInsulinTotal = 0.0;
    }


    @Override
    public int getLength() {
        return isLargerFormat() ? 22 : 20;
    }


    @Override
    public String getShortTypeName() {
        return "Bolus Wizard Est.";
    }


    @Override
    public boolean readFromBundle(Bundle in) {
        carbohydrates = in.getInt("carbohydrates", 0);
        bloodGlucose = in.getInt("bloodGlucose", 0);
        foodEstimate = in.getDouble("foodEstimate", 0);
        correctionEstimate = in.getDouble("correctionEstimate", 0);
        bolusEstimate = in.getDouble("bolusEstimate", 0);
        unabsorbedInsulinTotal = in.getDouble("unabsorbedInsulinTotal", 0);
        bgTargetLow = in.getInt("bgTargetLow", 0);
        bgTargetHigh = in.getInt("bgTargetHigh", 0);
        insulinSensitivity = in.getInt("insulinSensitivity", 0);
        carbRatio = in.getDouble("carbRatio", 0);
        return super.readFromBundle(in);
    }


    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putInt("carbohydrates", carbohydrates);
        in.putInt("bloodGlucose", bloodGlucose);
        in.putDouble("foodEstimate", foodEstimate);
        in.putDouble("correctionEstimate", correctionEstimate);
        in.putDouble("bolusEstimate", bolusEstimate);
        in.putDouble("unabsorbedInsulinTotal", unabsorbedInsulinTotal);
        in.putInt("bgTargetLow", bgTargetLow);
        in.putInt("bgTargetHigh", bgTargetHigh);
        in.putInt("insulinSensitivity", insulinSensitivity);
        in.putDouble("carbRatio", carbRatio);
    }


    public double getCorrectionEstimate() {
        return correctionEstimate;
    }


    public long getBG() {
        return bloodGlucose;
    }


    public int getCarbohydrates() {
        return carbohydrates;
    }


    public double getICRatio() {
        return carbRatio;
    }


    public int getInsulinSensitivity() {
        return insulinSensitivity;
    }


    public int getBgTargetLow() {
        return bgTargetLow;
    }


    public int getBgTargetHigh() {
        return bgTargetHigh;
    }


    public double getBolusEstimate() {
        return bolusEstimate;
    }


    public double getFoodEstimate() {
        return foodEstimate;
    }


    public double getUnabsorbedInsulinTotal() {
        return unabsorbedInsulinTotal;
    }


    private double insulinDecode(int a, int b) {
        return ((a << 8) + b) / 40.0;
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        if (!simpleParse(data, 2)) {
            return false;
        }
        if (MedtronicDeviceType.isLargerFormat(model)) {
            carbohydrates = (asUINT8(data[8]) & 0x0c << 6) + asUINT8(data[7]);
            bloodGlucose = (asUINT8(data[8]) & 0x03 << 8) + asUINT8(data[1]);
            foodEstimate = insulinDecode(asUINT8(data[14]), asUINT8(data[15]));
            correctionEstimate = (double)((asUINT8(data[16]) & 0b111000) << 5 + asUINT8(data[13])) / 40.0;
            bolusEstimate = insulinDecode(asUINT8(data[19]), asUINT8(data[20]));
            unabsorbedInsulinTotal = insulinDecode(asUINT8(data[17]), asUINT8(data[18]));
            bgTargetLow = asUINT8(data[12]);
            bgTargetHigh = asUINT8(data[21]);
            insulinSensitivity = asUINT8(data[11]);
            carbRatio = (double)(((asUINT8(data[9]) & 0x07) << 8) + asUINT8(data[10])) / 40.0;
        } else {
            carbohydrates = asUINT8(data[7]);
            bloodGlucose = ((asUINT8(data[8]) & 0x03) << 8) + asUINT8(data[1]);
            foodEstimate = (double)(asUINT8(data[13])) / 10.0;
            correctionEstimate = (double)((asUINT8(data[14]) << 8) + asUINT8(data[12])) / 10.0;
            bolusEstimate = (double)(asUINT8(data[18])) / 10.0;
            unabsorbedInsulinTotal = (double)(asUINT8(data[16])) / 10.0;
            bgTargetLow = asUINT8(data[11]);
            bgTargetHigh = asUINT8(data[19]);
            insulinSensitivity = asUINT8(data[10]);
            carbRatio = (double)asUINT8(data[9]);
        }

        return true;
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
