package com.gxwtech.roundtrip2.RoundtripService.RileyLink;

import java.util.Date;

/**
 * Created by geoff on 7/16/16.
 *
 * This class is intended to provide compatibility with HAPP, and was
 * modeled after the PumpStatus class from happdanardriver
 *
 * happdanardriver/app/src/main/java/info/nightscout/danar/db/PumpStatus.java
 */
public class PumpManagerStatus {
    public long getTimeIndex() {
        return (long) Math.ceil(time.getTime() / 60000d );
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    public long timeIndex;

    public Date time;

    public double remainUnits = 0;

    public int remainBattery = 0;

    public double currentBasal = 0;

    public int tempBasalInProgress = 0;

    public int tempBasalRatio = 0;

    public int tempBasalRemainMin = 0 ;

    public Date last_bolus_time ;

    public double last_bolus_amount = 0;

    public Date tempBasalStart;

    @Override
    public String toString() {
        return "PumpStatus{" +
                "timeIndex=" + timeIndex +
                ", time=" + time +
                ", remainUnits=" + remainUnits +
                ", remainBattery=" + remainBattery +
                ", currentBasal=" + currentBasal +
                ", tempBasalInProgress=" + tempBasalInProgress +
                ", tempBasalRatio=" + tempBasalRatio +
                ", tempBasalRemainMin=" + tempBasalRemainMin +
                ", last_bolus_time=" + last_bolus_time +
                ", last_bolus_amount=" + last_bolus_amount +
                ", tempBasalStart=" + tempBasalStart +
                '}';
    }

}
