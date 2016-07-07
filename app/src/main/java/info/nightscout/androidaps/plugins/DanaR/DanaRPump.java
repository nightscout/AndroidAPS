package info.nightscout.androidaps.plugins.DanaR;

import java.util.Date;

/**
 * Created by mike on 04.07.2016.
 */
public class DanaRPump {
    public static final int UNITS_MGDL = 0;
    public static final int UNITS_MMOL = 1;

    // Info
    public String serialNumber = "";
    public Date shippingDate = new Date(0);
    public String shippingCountry = "";
    public boolean isNewPump = false;
    public int accessCode = -1;
    public Date pumpTime = new Date(0);

    // Status
    public boolean pumpSuspended;
    public boolean calculatorEnabled;
    public double dailyTotalUnits;
    public int dailyMaxRate;

    public double iob;

    public double reservoirRemainingUnits;
    public int batteryRemaining;

    public boolean bolusBlocked;
    public Date lastBolusTime = new Date(0);
    public double lastBolusAmount;

    public double currentBasal;

    public boolean isTempBasalInProgress;
    public int tempBasalPercent;
    public int tempBasalRemainingMin;
    public int tempBasalTotalSec;
    public Date tempBasalStart;

    public boolean isExtendedInProgress;
    public int extendedBolusMinutes;
    public double extendedBolusAmount;
    public double extendedBolusAbsoluteRate;
    public int extendedBolusSoFarInMinutes;
    public Date extendedBolusStart;
    public int extendedBolusRemainingMinutes;

    // Profile
    public int units;
    public int easyBasalMode;
    public boolean basal48Enable = false;
    public int currentCIR;
    public int currentCF;
    public int currentAI;
    public int currentTarget;
    public int currentAIDR;

    public int morningCIR;
    public int morningCF;
    public int afternoonCIR;
    public int afternoonCF;
    public int eveningCIR;
    public int eveningCF;
    public int nightCIR;
    public int nightCF;


    public class PumpProfile {
        public double[] basalValue = new double[48];
    }
    public int activeProfile = 0;
    public PumpProfile[] pumpProfiles = new PumpProfile[4];

    //Limits
    public double maxBolus;
    public double maxBasal;
    public double dailyMax;
}
