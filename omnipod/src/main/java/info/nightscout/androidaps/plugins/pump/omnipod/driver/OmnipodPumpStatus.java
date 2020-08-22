package info.nightscout.androidaps.plugins.pump.omnipod.driver;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by andy on 4.8.2019
 */
@Singleton
@Deprecated
public class OmnipodPumpStatus extends PumpStatus {
    // TODO remove all fields that can also be obtained via PodStateManager
    //  We can probably get rid of this class altogether

    private final SP sp;

    public boolean inPreInit = true;

    // statuses
    public double currentBasal = 0;
    public long tempBasalStart;
    public long tempBasalEnd;
    public Double tempBasalAmount = 0.0d;
    public Integer tempBasalLength;
    public long tempBasalPumpId;
    public PumpType pumpType;

    @Inject
    public OmnipodPumpStatus(SP sp) {
        super(PumpType.Insulet_Omnipod);
        this.sp = sp;
        initSettings();
    }

    @Override
    public void initSettings() {
        this.activeProfileName = "";
        this.reservoirRemainingUnits = 75d;
        this.batteryRemaining = 75;
        this.lastConnection = sp.getLong(OmnipodConst.Statistics.LastGoodPumpCommunicationTime, 0L);
        this.pumpType = PumpType.Insulet_Omnipod;
    }

    @Override
    public String getErrorInfo() {
        return null;
    }

    public void clearTemporaryBasal() {
        this.tempBasalStart = 0L;
        this.tempBasalEnd = 0L;
        this.tempBasalAmount = 0.0d;
        this.tempBasalLength = 0;
    }


    public TempBasalPair getTemporaryBasal() {
        TempBasalPair tbr = new TempBasalPair();
        tbr.setDurationMinutes(tempBasalLength);
        tbr.setInsulinRate(tempBasalAmount);
        tbr.setStartTime(tempBasalStart);
        tbr.setEndTime(tempBasalEnd);

        return tbr;
    }

    @Override
    public String toString() {
        return "OmnipodPumpStatus{" +
                "inPreInit=" + inPreInit +
                ", tempBasalStart=" + tempBasalStart +
                ", tempBasalEnd=" + tempBasalEnd +
                ", tempBasalAmount=" + tempBasalAmount +
                ", tempBasalLength=" + tempBasalLength +
                ", lastDataTime=" + lastDataTime +
                ", lastConnection=" + lastConnection +
                ", previousConnection=" + previousConnection +
                ", lastBolusTime=" + lastBolusTime +
                ", lastBolusAmount=" + lastBolusAmount +
                ", activeProfileName='" + activeProfileName + '\'' +
                ", reservoirRemainingUnits=" + reservoirRemainingUnits +
                ", reservoirFullUnits=" + reservoirFullUnits +
                ", batteryRemaining=" + batteryRemaining +
                ", batteryVoltage=" + batteryVoltage +
                ", iob='" + iob + '\'' +
                ", dailyTotalUnits=" + dailyTotalUnits +
                ", maxDailyTotalUnits='" + maxDailyTotalUnits + '\'' +
                ", validBasalRateProfileSelectedOnPump=" + validBasalRateProfileSelectedOnPump +
                ", pumpType=" + pumpType +
                ", profileStore=" + profileStore +
                ", units='" + units + '\'' +
                ", pumpStatusType=" + pumpStatusType +
                ", basalsByHour=" + Arrays.toString(basalsByHour) +
                ", currentBasal=" + currentBasal +
                ", tempBasalInProgress=" + tempBasalInProgress +
                ", tempBasalRatio=" + tempBasalRatio +
                ", tempBasalRemainMin=" + tempBasalRemainMin +
                ", tempBasalStart=" + tempBasalStart +
                ", pumpType=" + pumpType +
                "} ";
    }
}
