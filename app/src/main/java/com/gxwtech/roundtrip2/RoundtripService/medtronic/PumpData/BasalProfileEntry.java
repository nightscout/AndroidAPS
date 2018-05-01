package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

import org.joda.time.LocalTime;

/**
 * Created by geoff on 6/1/15.
 * This is a helper class for BasalProfile, only used for interpreting the contents of BasalProfile
 */
public class BasalProfileEntry {
    public byte rate_raw;
    public double rate;
    public byte startTime_raw;
    public LocalTime startTime; // Just a "time of day"
    public BasalProfileEntry() {
        rate = -9.999E6;
        rate_raw = (byte)0xFF;
        startTime = new LocalTime(0);
        startTime_raw = (byte)0xFF;
    }
    public BasalProfileEntry(int rateByte, int startTimeByte) {
        // rateByte is insulin delivery rate, U/hr, in 0.025 U increments
        // startTimeByte is time-of-day, in 30 minute increments
        rate_raw = (byte)rateByte;
        rate = rateByte * 0.025;
        startTime_raw = (byte)startTimeByte;
        startTime = new LocalTime(startTimeByte / 2, (startTimeByte % 2) * 30);
    }
}
