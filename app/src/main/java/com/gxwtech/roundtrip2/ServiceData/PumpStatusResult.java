package com.gxwtech.roundtrip2.ServiceData;

import com.gxwtech.roundtrip2.RoundtripService.RileyLink.PumpManagerStatus;

/**
 * Created by geoff on 7/16/16.
 */
public class PumpStatusResult extends ServiceResult {
    private static final String TAG = "PumpStatusResult";
    public PumpStatusResult() { }

    @Override
    public void init() {
        map.putString("ServiceMessageType","PumpStatusResult");
    }

    /**
     * pumpStatus.remainBattery = statusEvent.remainBattery;
     pumpStatus.remainUnits = statusEvent.remainUnits;
     pumpStatus.currentBasal = statusEvent.currentBasal;
     pumpStatus.last_bolus_amount = statusEvent.last_bolus_amount;
     pumpStatus.last_bolus_time = statusEvent.last_bolus_time;
     pumpStatus.tempBasalInProgress = statusEvent.tempBasalInProgress;
     pumpStatus.tempBasalRatio = statusEvent.tempBasalRatio;
     pumpStatus.tempBasalRemainMin = statusEvent.tempBasalRemainMin;
     pumpStatus.tempBasalStart = statusEvent.tempBasalStart;
     pumpStatus.time = statusEvent.time;//Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime();
     statusEvent.timeLastSync = statusEvent.time;
     */

    /*
    double remainBattery;
    double remainUnits;
    double currentBasal;
    double lastBolusAmount;
    String lastBolusTime;
    int tempBasalInProgress;
    double tempBasalRatio;
    double tempBasalRemainMin;
    String tempBasalStart;
    String time;
    String timeLastSync;
    */

    public double getRemainBattery() {
        return map.getDouble("remainBattery");
    }

    public void setRemainBattery(double remainBattery) {
        map.putDouble("remainBattery",remainBattery);
    }

    public double getRemainUnits() {
        return map.getDouble("remainUnits");
    }

    public void setRemainUnits(double remainUnits) {
        map.putDouble("remainUnits",remainUnits);
    }

    public double getCurrentBasal() {
        return map.getDouble("currentBasal");
    }

    public void setCurrentBasal(double currentBasal) {
        map.putDouble("currentBasal",currentBasal);
    }

    public double getLastBolusAmount() {
        return map.getDouble("lastBolusAmount");
    }

    public void setLastBolusAmount(double lastBolusAmount) {
        map.putDouble("lastBolusAmount",lastBolusAmount);
    }

    public String getLastBolusTime() {
        return map.getString("lastBolusTime","");
    }

    public void setLastBolusTime(String lastBolusTime) {
        map.putString("lastBolusTime",lastBolusTime);
    }

    public int getTempBasalInProgress() {
        return map.getInt("tempBasalInProgress");
    }

    public void setTempBasalInProgress(int tempBasalInProgress) {
        map.putInt("tempBasalInProgress",tempBasalInProgress);
    }

    public double getTempBasalRatio() {
        return map.getDouble("tempBasalRatio");
    }

    public void setTempBasalRatio(double tempBasalRatio) {
        map.putDouble("tempBasalRatio",tempBasalRatio);
    }

    public double getTempBasalRemainMin() {
        return map.getDouble("tempBasalRemainMin");
    }

    public void setTempBasalRemainMin(double tempBasalRemainMin) {
        map.putDouble("tempBasalRemainMin",tempBasalRemainMin);
    }

    public String getTempBasalStart() {
        return map.getString("tempBasalStart","");
    }

    public void setTempBasalStart(String tempBasalStart) {
        map.putString("tempBasalStart",tempBasalStart);
    }

    public String getTime() {
        return map.getString("time","");
    }

    public void setTime(String time) {
        map.putString("time",time);
    }

    public String getTimeLastSync() {
        return map.getString("timeLastSync","");
    }

    public void setTimeLastSync(String timeLastSync) {
        map.putString("timeLastSync",timeLastSync);
    }

    public void initFromServiceResult(ServiceResult serviceResult) {
        setMap(serviceResult.getMap());
    }

}
