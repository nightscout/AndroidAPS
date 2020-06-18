package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.joda.time.LocalTime;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/1/15.
 * This is a helper class for BasalProfile, only used for interpreting the contents of BasalProfile
 * - fixed rate is not one bit but two
 */
public class BasalProfileEntry {

    byte[] rate_raw;
    public double rate;
    byte startTime_raw;
    public LocalTime startTime; // Just a "time of day"

    public BasalProfileEntry() {
        rate = -9.999E6;
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(0xFF, true);
        startTime = new LocalTime(0);
        startTime_raw = (byte) 0xFF;
    }

    public BasalProfileEntry(double rate, int hour, int minutes) {
        byte[] data = MedtronicUtil.getBasalStrokes(rate, true);

        rate_raw = new byte[2];
        rate_raw[0] = data[1];
        rate_raw[1] = data[0];

        int interval = hour * 2;

        if (minutes == 30) {
            interval++;
        }

        startTime_raw = (byte) interval;
        startTime = new LocalTime(hour, minutes == 30 ? 30 : 0);
    }

    BasalProfileEntry(AAPSLogger aapsLogger, int rateStrokes, int startTimeInterval) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(rateStrokes, true);
        rate = rateStrokes * 0.025;
        startTime_raw = (byte) startTimeInterval;

        try {
            startTime = new LocalTime(startTimeInterval / 2, (startTimeInterval % 2) * 30);
        } catch (Exception ex) {
            aapsLogger.error(LTag.PUMPCOMM,
                    "Error creating BasalProfileEntry: startTimeInterval={}, startTime_raw={}, hours={}, rateStrokes={}",
                    startTimeInterval, startTime_raw, startTimeInterval / 2, rateStrokes);
            throw ex;
        }

    }

    BasalProfileEntry(byte rateByte, int startTimeByte) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = MedtronicUtil.getByteArrayFromUnsignedShort(rateByte, true);
        rate = rateByte * 0.025;
        startTime_raw = (byte) startTimeByte;
        startTime = new LocalTime(startTimeByte / 2, (startTimeByte % 2) * 30);
    }

    public void setStartTime(LocalTime localTime) {
        this.startTime = localTime;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }
}
