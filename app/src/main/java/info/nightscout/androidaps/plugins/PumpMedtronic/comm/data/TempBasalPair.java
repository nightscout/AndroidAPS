package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Created by geoff on 5/29/15.
 * <p>
 * Just need a class to keep the pair together, for parcel transport.
 */
public class TempBasalPair {
    private double mInsulinRate = 0.0;
    private int mDurationMinutes = 0;
    private boolean mIsPercent = false;

    public double getInsulinRate() {
        return mInsulinRate;
    }

    public void setInsulinRate(double insulinRate) {
        this.mInsulinRate = insulinRate;
    }

    public int getDurationMinutes() {
        return mDurationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.mDurationMinutes = durationMinutes;
    }

    public boolean isPercent() {
        return mIsPercent;
    }

    public void setIsPercent(boolean yesIsPercent) {
        this.mIsPercent = yesIsPercent;
    }

    public TempBasalPair() {
    }

    public TempBasalPair(double insulinRate, boolean isPercent, int durationMinutes) {
        mInsulinRate = insulinRate;
        mIsPercent = isPercent;
        mDurationMinutes = durationMinutes;
    }


    public TempBasalPair(byte[] response) {

        mIsPercent = response[0] == 1;

        if (mIsPercent) {
            mInsulinRate = response[1];
        } else {
            int strokes = MedtronicUtil.makeUnsignedShort(response[2], response[3]);

            mInsulinRate = strokes / 40.0d;
        }

        mDurationMinutes = MedtronicUtil.makeUnsignedShort(response[4], response[5]);

    }

    public byte[] getAsRawData() {

        List<Byte> list = new ArrayList<Byte>();

        list.add((byte) 0); // absolute
        list.add((byte) 0); // percent amount

        byte[] insulinRate = MedtronicUtil.getBasalStrokes(mInsulinRate, true);

        list.add(insulinRate[0]);
        list.add(insulinRate[1]);

        byte[] timeMin = MedtronicUtil.getByteArrayFromUnsignedShort(mDurationMinutes, true);

        list.add(timeMin[0]);
        list.add(timeMin[1]);

        return MedtronicUtil.createByteArray(list);
    }
}
