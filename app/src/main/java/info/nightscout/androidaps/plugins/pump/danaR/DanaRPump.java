package info.nightscout.androidaps.plugins.pump.danaR;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 04.07.2016.
 */
public class DanaRPump {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);

    private static DanaRPump instance = null;

    public static DanaRPump getInstance() {
        if (instance == null) instance = new DanaRPump();
        return instance;
    }

    public static void reset() {
        log.debug("DanaRPump reset");
        instance = null;
    }

    public static final int UNITS_MGDL = 0;
    public static final int UNITS_MMOL = 1;

    public static final int DELIVERY_PRIME = 0x01;
    public static final int DELIVERY_STEP_BOLUS = 0x02;
    public static final int DELIVERY_BASAL = 0x04;
    public static final int DELIVERY_EXT_BOLUS = 0x08;

    public static final String PROFILE_PREFIX = "DanaR-";

    // v2 history entries
    public static final int TEMPSTART = 1;
    public static final int TEMPSTOP = 2;
    public static final int EXTENDEDSTART = 3;
    public static final int EXTENDEDSTOP = 4;
    public static final int BOLUS = 5;
    public static final int DUALBOLUS = 6;
    public static final int DUALEXTENDEDSTART = 7;
    public static final int DUALEXTENDEDSTOP = 8;
    public static final int SUSPENDON = 9;
    public static final int SUSPENDOFF = 10;
    public static final int REFILL = 11;
    public static final int PRIME = 12;
    public static final int PROFILECHANGE = 13;
    public static final int CARBS = 14;
    public static final int PRIMECANNULA = 15;

    public long lastConnection = 0;
    public long lastSettingsRead =0;

    // Info
    public String serialNumber = "";
    public long shippingDate = 0;
    public String shippingCountry = "";
    public boolean isNewPump = true;
    public int password = -1;
    public long pumpTime = 0;

    public static final int DOMESTIC_MODEL = 0x01;
    public static final int EXPORT_MODEL = 0x03;
    public int model = 0;
    public int protocol = 0;
    public int productCode = 0;

    public boolean isConfigUD;
    public boolean isExtendedBolusEnabled;
    public boolean isEasyModeEnabled;

    // Status
    public boolean pumpSuspended;
    public boolean calculatorEnabled;
    public double dailyTotalUnits;
    public double dailyTotalBolusUnits = 0; // RS only
    public double dailyTotalBasalUnits = 0; // RS only
    public int maxDailyTotalUnits;

    public double bolusStep = 0.1;
    public double basalStep = 0.1;

    public double iob;

    public double reservoirRemainingUnits;
    public int batteryRemaining;

    public boolean bolusBlocked;
    public long lastBolusTime = 0;
    public double lastBolusAmount;

    public double currentBasal;

    public boolean isTempBasalInProgress;
    public int tempBasalPercent;
    public int tempBasalRemainingMin;
    public int tempBasalTotalSec;
    public long tempBasalStart;

    public boolean isDualBolusInProgress;
    public boolean isExtendedInProgress;
    public int extendedBolusMinutes;
    public double extendedBolusAmount;
    public double extendedBolusAbsoluteRate;
    public int extendedBolusSoFarInMinutes;
    public long extendedBolusStart;
    public int extendedBolusRemainingMinutes;
    public double extendedBolusDeliveredSoFar; //RS only

    // Profile
    public int units;
    public int easyBasalMode;
    public boolean basal48Enable = false;
    public int currentCIR;
    public double currentCF;
    public double currentAI;
    public double currentTarget;
    public int currentAIDR;

    public int morningCIR;
    public double morningCF;
    public int afternoonCIR;
    public double afternoonCF;
    public int eveningCIR;
    public double eveningCF;
    public int nightCIR;
    public double nightCF;


    public int activeProfile = 0;
    public double[][] pumpProfiles = null;

    //Limits
    public double maxBolus;
    public double maxBasal;

    // DanaRS specific

    public String rs_password = "";

    // User settings
    public int timeDisplayType;
    public int buttonScrollOnOff;
    public int beepAndAlarm;
    public int lcdOnTimeSec;
    public int backlightOnTimeSec;
    public int selectedLanguage;
    public int shutdownHour;
    public int lowReservoirRate;
    public int cannulaVolume;
    public int refillAmount;
    public byte[] userOptionsFrompump;
    public double initialBolusAmount;
    // Bolus settings
    public int bolusCalculationOption;
    public int missedBolusConfig;

    public String getUnits() {
        return units == UNITS_MGDL ? Constants.MGDL : Constants.MMOL;
    }

    public ProfileStore createConvertedProfile() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

//        Morning / 6:00–10:59
//        Afternoon / 11:00–16:59
//        Evening / 17:00–21:59
//        Night / 22:00–5:59

        double dia = SP.getDouble(R.string.key_danarprofile_dia, Constants.defaultDIA);

        try {
            json.put("defaultProfile", PROFILE_PREFIX + (activeProfile + 1));
            json.put("store", store);
            profile.put("dia", dia);

            JSONArray carbratios = new JSONArray();
            carbratios.put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", nightCIR));
            carbratios.put(new JSONObject().put("time", "06:00").put("timeAsSeconds", 6 * 3600).put("value", morningCIR));
            carbratios.put(new JSONObject().put("time", "11:00").put("timeAsSeconds", 11 * 3600).put("value", afternoonCIR));
            carbratios.put(new JSONObject().put("time", "14:00").put("timeAsSeconds", 17 * 3600).put("value", eveningCIR));
            carbratios.put(new JSONObject().put("time", "22:00").put("timeAsSeconds", 22 * 3600).put("value", nightCIR));
            profile.put("carbratio", carbratios);

            JSONArray sens = new JSONArray();
            sens.put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", nightCF));
            sens.put(new JSONObject().put("time", "06:00").put("timeAsSeconds", 6 * 3600).put("value", morningCF));
            sens.put(new JSONObject().put("time", "11:00").put("timeAsSeconds", 11 * 3600).put("value", afternoonCF));
            sens.put(new JSONObject().put("time", "17:00").put("timeAsSeconds", 17 * 3600).put("value", eveningCF));
            sens.put(new JSONObject().put("time", "22:00").put("timeAsSeconds", 22 * 3600).put("value", nightCF));
            profile.put("sens", sens);

            JSONArray basals = new JSONArray();
            int basalValues = basal48Enable ? 48 : 24;
            int basalIncrement = basal48Enable ? 30 * 60 : 60 * 60;
            for (int h = 0; h < basalValues; h++) {
                String time;
                DecimalFormat df = new DecimalFormat("00");
                if (basal48Enable) {
                    time = df.format((long) h / 2) + ":" + df.format(30 * (h % 2));
                } else {
                    time = df.format(h) + ":00";
                }
                basals.put(new JSONObject().put("time", time).put("timeAsSeconds", h * basalIncrement).put("value", pumpProfiles[activeProfile][h]));
            }
            profile.put("basal", basals);

            profile.put("target_low", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentTarget)));
            profile.put("target_high", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentTarget)));
            profile.put("units", units == UNITS_MGDL ? Constants.MGDL : Constants.MMOL);
            store.put(PROFILE_PREFIX + (activeProfile + 1), profile);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        } catch (Exception e) {
            return null;
        }

        return new ProfileStore(json);
    }

    public String createConvertedProfileName() {
        return PROFILE_PREFIX + (activeProfile + 1);
    }

    public double[] buildDanaRProfileRecord(Profile nsProfile) {
        double[] record = new double[24];
        for (Integer hour = 0; hour < 24; hour++) {
            //Some values get truncated to the next lower one.
            // -> round them to two decimals and make sure we are a small delta larger (that will get truncated)
            double value = Math.round(100d * nsProfile.getBasalTimeFromMidnight((Integer) (hour * 60 * 60)))/100d + 0.00001;
            if (L.isEnabled(L.PUMP))
                log.debug("NS basal value for " + hour + ":00 is " + value);
            record[hour] = value;
        }
        return record;
    }

    public boolean isPasswordOK() {
        if (password != -1 && password != SP.getInt(R.string.key_danar_password, -1)) {
            return false;
        }
        return true;
    }
}
