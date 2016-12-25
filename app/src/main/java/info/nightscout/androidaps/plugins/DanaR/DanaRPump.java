package info.nightscout.androidaps.plugins.DanaR;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 04.07.2016.
 */
public class DanaRPump {
    public static final int UNITS_MGDL = 0;
    public static final int UNITS_MMOL = 1;

    public static final int DELIVERY_PRIME = 0x01;
    public static final int DELIVERY_STEP_BOLUS = 0x02;
    public static final int DELIVERY_BASAL = 0x04;
    public static final int DELIVERY_EXT_BOLUS = 0x08;

    public static final String PROFILE_PREFIX = "DanaR-";

    public Date lastConnection = new Date(0);
    public Date lastSettingsRead = new Date(0);

    // Info
    public String serialNumber = "";
    public Date shippingDate = new Date(0);
    public String shippingCountry = "";
    public boolean isNewPump = false;
    public int password = -1;
    public Date pumpTime = new Date(0);

    public static final int DOMESTIC_MODEL = 0x01;
    public static final int EXPORT_MODEL = 0x03;
    public int model;
    public int protocol;
    public int productCode;

    public boolean isConfigUD;
    public boolean isExtendedBolusEnabled;


    // Status
    public boolean pumpSuspended;
    public boolean calculatorEnabled;
    public double dailyTotalUnits;
    public int maxDailyTotalUnits;

    public double bolusStep;
    public double basalStep;

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

    public boolean isDualBolusInProgress;
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

    public NSProfile createConvertedProfile() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

//        Morning / 6:00–10:59
//        Afternoon / 11:00–16:59
//        Evening / 17:00–21:59
//        Night / 22:00–5:59

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        double dia = SafeParse.stringToDouble(SP.getString("danarprofile_dia", "3"));
        double car = SafeParse.stringToDouble(SP.getString("danarprofile_car", "20"));

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

            profile.put("carbs_hr", car);

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
            e.printStackTrace();
        } catch (Exception e) {
            return null;
        }
        return new NSProfile(json, PROFILE_PREFIX + (activeProfile + 1));
    }

}
