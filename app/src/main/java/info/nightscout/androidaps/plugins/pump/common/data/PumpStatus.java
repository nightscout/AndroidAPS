package info.nightscout.androidaps.plugins.pump.common.data;

import java.util.Date;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

/**
 * Created by andy on 4/28/18.
 */

public abstract class PumpStatus {

    // connection
    public LocalDateTime lastDataTime;
    public long lastConnection = 0L;
    public long previousConnection = 0L; // here should be stored last connection of previous session (so needs to be
                                         // read before lastConnection is modified for first time).

    // last bolus
    public Date lastBolusTime;
    public Double lastBolusAmount;

    // other pump settings
    public String activeProfileName = "0";
    public double reservoirRemainingUnits = 0d;
    public int reservoirFullUnits = 0;
    public int batteryRemaining = 0; // percent, so 0-100
    public Double batteryVoltage = null; 


    // iob
    public String iob = null;

    // TDD
    public Double dailyTotalUnits;
    public String maxDailyTotalUnits;
    public boolean validBasalRateProfileSelectedOnPump = true;
    public PumpType pumpType = PumpType.GenericAAPS;
    public ProfileStore profileStore;
    public String units; // Constants.MGDL or Constants.MMOL
    public PumpStatusType pumpStatusType = PumpStatusType.Running;
    public Double[] basalsByHour;
    public double currentBasal = 0;
    public int tempBasalInProgress = 0;
    public int tempBasalRatio = 0;
    public int tempBasalRemainMin = 0;
    public Date tempBasalStart;
    protected PumpDescription pumpDescription;


    public PumpStatus(PumpDescription pumpDescription) {
        this.pumpDescription = pumpDescription;

        this.initSettings();
    }


    public abstract void initSettings();


    public void setLastCommunicationToNow() {
        this.lastDataTime = LocalDateTime.now();
        this.lastConnection = System.currentTimeMillis();
    }


    public abstract String getErrorInfo();


    public abstract void refreshConfiguration();


    public PumpType getPumpType() {
        return pumpType;
    }


    public void setPumpType(PumpType pumpType) {
        this.pumpType = pumpType;
    }

    // public Date last_bolus_time;
    // public double last_bolus_amount = 0;

}
