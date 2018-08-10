package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import org.joda.time.LocalTime;

import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/1/15.
 * This is a helper class for BasalProfile, only used for interpreting the contents of BasalProfile
 * - fixed rate is not one bit but two
 */
public class BasalProfileEntry {

    public byte[] rate_raw;
    public double rate;
    public byte startTime_raw;
    public LocalTime startTime; // Just a "time of day"


    public BasalProfileEntry() {
        rate = -9.999E6;
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(0xFF, true);
        startTime = new LocalTime(0);
        startTime_raw = (byte)0xFF;
    }


    public BasalProfileEntry(int rateStrokes, int startTimeInterval) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(rateStrokes, true);
        rate = rateStrokes * 0.025;
        startTime_raw = (byte)startTimeInterval;
        startTime = new LocalTime(startTimeInterval / 2, (startTimeInterval % 2) * 30);
    }


    public BasalProfileEntry(byte rateByte, int startTimeByte) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(rateByte, true);
        rate = rateByte * 0.025;
        startTime_raw = (byte)startTimeByte;
        startTime = new LocalTime(startTimeByte / 2, (startTimeByte % 2) * 30);
    }


    public void setStartTime(LocalTime localTime) {
        this.startTime = localTime;
    }


    public void setRate(double rate) {
        this.rate = rate;
    }

}
