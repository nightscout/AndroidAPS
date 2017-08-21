package info.nightscout.androidaps.plugins.OpenAPSSMB;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.utils.SP;

public class DetermineBasalAdapterSMBJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterSMBJS.class);


    private ScriptReader mScriptReader = null;
    V8 mV8rt;
    private V8Object mProfile;
    private V8Object mGlucoseStatus;
    private V8Array mIobData;
    private V8Object mMealData;
    private V8Object mCurrentTemp;
    private V8Object mAutosensData = null;

    private final String PARAM_currentTemp = "currentTemp";
    private final String PARAM_iobData = "iobData";
    private final String PARAM_glucoseStatus = "glucose_status";
    private final String PARAM_profile = "profile";
    private final String PARAM_meal_data = "meal_data";
    private final String PARAM_autosens_data = "autosens_data";
	private final String PARAM_reservoirData = "reservoirData";
	private final String PARAM_microBolusAllowed = "microBolusAllowed";
	

    private String storedCurrentTemp = null;
    private String storedIobData = null;
    private String storedGlucoseStatus = null;
    private String storedProfile = null;
    private String storedMeal_data = null;
    private String storedAutosens_data = null;
    private String storedMicroBolusAllowed = null;

    private String scriptDebug = "";

    /**
     * Main code
     */

    public DetermineBasalAdapterSMBJS(ScriptReader scriptReader) throws IOException {
        mV8rt = V8.createV8Runtime();
        mScriptReader = scriptReader;

        initLogCallback();
        initProcessExitCallback();
        initModuleParent();
        loadScript();
    }

    public DetermineBasalResultSMB invoke() {

        log.debug(">>> Invoking detemine_basal_oref1 <<<");
        log.debug("Glucose status: " + (storedGlucoseStatus = mV8rt.executeStringScript("JSON.stringify(" + PARAM_glucoseStatus + ");")));
        log.debug("IOB data:       " + (storedIobData = mV8rt.executeStringScript("JSON.stringify(" + PARAM_iobData + ");")));
        log.debug("Current temp:   " + (storedCurrentTemp = mV8rt.executeStringScript("JSON.stringify(" + PARAM_currentTemp + ");")));
        log.debug("Profile:        " + (storedProfile = mV8rt.executeStringScript("JSON.stringify(" + PARAM_profile + ");")));
        log.debug("Meal data:      " + (storedMeal_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_meal_data + ");")));
        if (mAutosensData != null)
            log.debug("Autosens data:  " + (storedAutosens_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_autosens_data + ");")));
        else
            log.debug("Autosens data:  " + (storedAutosens_data = "undefined"));
        log.debug("Reservoir data: " + "undefined");
        log.debug("MicroBolusAllowed:  " + (storedMicroBolusAllowed = mV8rt.executeStringScript("JSON.stringify(" + PARAM_microBolusAllowed + ");")));

        mV8rt.executeVoidScript(
                "var rT = determine_basal(" +
                        PARAM_glucoseStatus + ", " +
                        PARAM_currentTemp + ", " +
                        PARAM_iobData + ", " +
                        PARAM_profile + ", " +
                        PARAM_autosens_data + ", " +
                        PARAM_meal_data + ", " +
                        "tempBasalFunctions" + ", " +
						PARAM_microBolusAllowed  + ", " +
						PARAM_reservoirData +
                        ");");


        String ret = mV8rt.executeStringScript("JSON.stringify(rT);");
        log.debug("Result: " + ret);

        DetermineBasalResultSMB result = null;
        try {
            result = new DetermineBasalResultSMB(new JSONObject(ret));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    String getGlucoseStatusParam() {
        return storedGlucoseStatus;
    }

    String getCurrentTempParam() {
        return storedCurrentTemp;
    }

    String getIobDataParam() {
        return storedIobData;
    }

    String getProfileParam() {
        return storedProfile;
    }

    String getMealDataParam() {
        return storedMeal_data;
    }

    String getAutosensDataParam() {
        return storedAutosens_data;
    }

    String getMicroBolusAllowedParam() {
        return storedMicroBolusAllowed;
    }

    String getScriptDebug() {
        return scriptDebug;
    }

    private void loadScript() throws IOException {
        mV8rt.executeVoidScript("var round_basal = function round_basal(basal, profile) { return basal; };");
        mV8rt.executeVoidScript("require = function() {return round_basal;};");

        mV8rt.executeVoidScript(readFile("OpenAPSSMB/basal-set-temp.js"), "OpenAPSSMB/basal-set-temp.js ", 0);
        mV8rt.executeVoidScript("var tempBasalFunctions = module.exports;");

        mV8rt.executeVoidScript(
                readFile("OpenAPSSMB/determine-basal.js"),
                "OpenAPSSMB/determine-basal.js",
                0);
        mV8rt.executeVoidScript("var determine_basal = module.exports;");
    }

    private void initModuleParent() {
        mV8rt.executeVoidScript("var module = {\"parent\":Boolean(1)};");
    }

    private void initProcessExitCallback() {
        JavaVoidCallback callbackProccessExit = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object arg0, V8Array parameters) {
                if (parameters.length() > 0) {
                    Object arg1 = parameters.get(0);
                    log.error("ProccessExit " + arg1);
                }
            }
        };
        mV8rt.registerJavaMethod(callbackProccessExit, "proccessExit");
        mV8rt.executeVoidScript("var process = {\"exit\": function () { proccessExit(); } };");
    }

    private void initLogCallback() {
        JavaVoidCallback callbackLog = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object arg0, V8Array parameters) {
                int i = 0;
                String s = "";
                while (i < parameters.length()) {
                    Object arg = parameters.get(i);
                    s += arg + " ";
                    i++;
                }
                if (!s.equals("") && Config.logAPSResult) {
                    log.debug("Script debug: " + s);
                    scriptDebug += s + "\n";
                }
            }
        };
        mV8rt.registerJavaMethod(callbackLog, "log");
        mV8rt.executeVoidScript("var console = {\"log\":log, \"error\":log};");
    }


    public void setData(Profile profile,
                        double maxIob,
                        double maxBasal,
                        double minBg,
                        double maxBg,
                        double targetBg,
                        PumpInterface pump,
                        IobTotal[] iobArray,
                        GlucoseStatus glucoseStatus,
                        MealData mealData,
                        double autosensDataRatio,
                        boolean tempTargetSet,
                        boolean microBolusAllowed
                        ) {

        String units = profile.getUnits();

        mProfile = new V8Object(mV8rt);
        mProfile.add("max_iob", maxIob);
        mProfile.add("dia", profile.getDia());
        mProfile.add("type", "current");
        mProfile.add("max_daily_basal", profile.getMaxDailyBasal());
        mProfile.add("max_basal", maxBasal);
        mProfile.add("min_bg", minBg);
        mProfile.add("max_bg", maxBg);
        mProfile.add("target_bg", targetBg);
        mProfile.add("carb_ratio", profile.getIc());
        mProfile.add("sens", Profile.toMgdl(profile.getIsf().doubleValue(), units));
        mProfile.add("max_daily_safety_multiplier", SP.getInt("openapsama_max_daily_safety_multiplier", 3));
        mProfile.add("current_basal_safety_multiplier", SP.getInt("openapsama_current_basal_safety_multiplier", 4));
        mProfile.add("skip_neutral_temps", true);
        mProfile.add("current_basal", pump.getBaseBasalRate());
        mProfile.add("temptargetSet", tempTargetSet);
        mProfile.add("autosens_adjust_targets", SP.getBoolean("openapsama_autosens_adjusttargets", true));
        mProfile.add("min_5m_carbimpact", SP.getDouble("openapsama_min_5m_carbimpact", 3d));
        mProfile.add("enableSMB_with_bolus", SP.getBoolean(R.string.key_use_smb, false));
        mProfile.add("enableSMB_with_COB", SP.getBoolean(R.string.key_use_smb, false));
        mProfile.add("enableSMB_with_temptarget", SP.getBoolean(R.string.key_use_smb, false));
        mProfile.add("enableUAM", SP.getBoolean(R.string.key_use_uam, false));
        mProfile.add("adv_target_adjustments", true); // lower target automatically when BG and eventualBG are high
        // create maxCOB and default it to 120 because that's the most a typical body can absorb over 4 hours.
        // (If someone enters more carbs or stacks more; OpenAPS will just truncate dosing based on 120.
        // Essentially, this just limits AMA as a safety cap against weird COB calculations)
        mProfile.add("maxCOB", 120);
        mProfile.add("autotune_isf_adjustmentFraction", 0.5); // keep autotune ISF closer to pump ISF via a weighted average of fullNewISF and pumpISF.  1.0 allows full adjustment, 0 is no adjustment from pump ISF.
        mProfile.add("remainingCarbsFraction", 1.0d); // fraction of carbs we'll assume will absorb over 4h if we don't yet see carb absorption
        mProfile.add("remainingCarbsCap", 90); // max carbs we'll assume will absorb over 4h if we don't yet see carb absorption
        mV8rt.add(PARAM_profile, mProfile);

        mCurrentTemp = new V8Object(mV8rt);
        mCurrentTemp.add("temp", "absolute");
        mCurrentTemp.add("duration", MainApp.getConfigBuilder().getTempBasalRemainingMinutesFromHistory());
        mCurrentTemp.add("rate", MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory());

        // as we have non default temps longer than 30 mintues
        TemporaryBasal tempBasal = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        if (tempBasal != null) {
            mCurrentTemp.add("minutesrunning", tempBasal.getRealDuration());
        }

        mV8rt.add(PARAM_currentTemp, mCurrentTemp);

        mIobData = mV8rt.executeArrayScript(IobCobCalculatorPlugin.convertToJSONArray(iobArray).toString());
        mV8rt.add(PARAM_iobData, mIobData);

        mGlucoseStatus = new V8Object(mV8rt);
        mGlucoseStatus.add("glucose", glucoseStatus.glucose);

        if (SP.getBoolean("always_use_shortavg", false)) {
            mGlucoseStatus.add("delta", glucoseStatus.short_avgdelta);
        } else {
            mGlucoseStatus.add("delta", glucoseStatus.delta);
        }
        mGlucoseStatus.add("short_avgdelta", glucoseStatus.short_avgdelta);
        mGlucoseStatus.add("long_avgdelta", glucoseStatus.long_avgdelta);
        mV8rt.add(PARAM_glucoseStatus, mGlucoseStatus);

        mMealData = new V8Object(mV8rt);
        mMealData.add("carbs", mealData.carbs);
        mMealData.add("boluses", mealData.boluses);
        mMealData.add("mealCOB", mealData.mealCOB);
        mMealData.add("minDeviationSlope", mealData.minDeviationSlope);
        mMealData.add("lastBolusTime", mealData.lastBolusTime);
        mV8rt.add(PARAM_meal_data, mMealData);

        if (MainApp.getConfigBuilder().isAMAModeEnabled()) {
            mAutosensData = new V8Object(mV8rt);
            mAutosensData.add("ratio", autosensDataRatio);
            mV8rt.add(PARAM_autosens_data, mAutosensData);
        } else {
            mV8rt.addUndefined(PARAM_autosens_data);
        }

        mV8rt.addUndefined(PARAM_reservoirData);
        mV8rt.add(PARAM_microBolusAllowed, microBolusAllowed);

    }


    public void release() {
        mProfile.release();
        mCurrentTemp.release();
        mIobData.release();
        mMealData.release();
        mGlucoseStatus.release();
        if (mAutosensData != null) {
            mAutosensData.release();
        }
        mV8rt.release();
    }

    public String readFile(String filename) throws IOException {
        byte[] bytes = mScriptReader.readFile(filename);
        String string = new String(bytes, "UTF-8");
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20);
        }
        return string;
    }

}
