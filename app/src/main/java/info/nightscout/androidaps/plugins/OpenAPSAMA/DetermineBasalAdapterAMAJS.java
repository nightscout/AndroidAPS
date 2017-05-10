package info.nightscout.androidaps.plugins.OpenAPSAMA;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.SP;

public class DetermineBasalAdapterAMAJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterAMAJS.class);


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

    private String storedCurrentTemp = null;
    private String storedIobData = null;
    private String storedGlucoseStatus = null;
    private String storedProfile = null;
    private String storedMeal_data = null;
    private String storedAutosens_data = null;

    private String scriptDebug = "";

    /**
     * Main code
     */

    public DetermineBasalAdapterAMAJS(ScriptReader scriptReader) throws IOException {
        mV8rt = V8.createV8Runtime();
        mScriptReader = scriptReader;

        initLogCallback();
        initProcessExitCallback();
        initModuleParent();
        loadScript();
    }

    public DetermineBasalResultAMA invoke() {

        log.debug(">>> Invoking detemine_basal <<<");
        log.debug("Glucose status: " + (storedGlucoseStatus = mV8rt.executeStringScript("JSON.stringify(" + PARAM_glucoseStatus + ");")));
        log.debug("IOB data:       " + (storedIobData = mV8rt.executeStringScript("JSON.stringify(" + PARAM_iobData + ");")));
        log.debug("Current temp:   " + (storedCurrentTemp = mV8rt.executeStringScript("JSON.stringify(" + PARAM_currentTemp + ");")));
        log.debug("Profile:        " + (storedProfile = mV8rt.executeStringScript("JSON.stringify(" + PARAM_profile + ");")));
        log.debug("Meal data:      " + (storedMeal_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_meal_data + ");")));
        if (mAutosensData != null)
            log.debug("Autosens data:  " + (storedAutosens_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_autosens_data + ");")));
        else
            log.debug("Autosens data:  " + (storedAutosens_data = "undefined"));

        mV8rt.executeVoidScript(
                "var rT = determine_basal(" +
                        PARAM_glucoseStatus + ", " +
                        PARAM_currentTemp + ", " +
                        PARAM_iobData + ", " +
                        PARAM_profile + ", " +
                        PARAM_autosens_data + ", " +
                        PARAM_meal_data + ", " +
                        "tempBasalFunctions" +
                        ");");


        String ret = mV8rt.executeStringScript("JSON.stringify(rT);");
        log.debug("Result: " + ret);

        V8Object v8ObjectReuslt = mV8rt.getObject("rT");

        DetermineBasalResultAMA result = null;
        try {
            result = new DetermineBasalResultAMA(v8ObjectReuslt, new JSONObject(ret));
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

    String getScriptDebug() {
        return scriptDebug;
    }

    private void loadScript() throws IOException {
        mV8rt.executeVoidScript("var round_basal = function round_basal(basal, profile) { return basal; };");
        mV8rt.executeVoidScript("require = function() {return round_basal;};");

        mV8rt.executeVoidScript(readFile("OpenAPSAMA/basal-set-temp.js"), "OpenAPSAMA/basal-set-temp.js ", 0);
        mV8rt.executeVoidScript("var tempBasalFunctions = module.exports;");

        mV8rt.executeVoidScript(
                readFile("OpenAPSAMA/determine-basal.js"),
                "OpenAPSAMA/determine-basal.js",
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


    public void setData(NSProfile profile,
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
                        double min_5m_carbimpact) {

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
        mProfile.add("carb_ratio", profile.getIc(profile.secondsFromMidnight()));
        mProfile.add("sens", NSProfile.toMgdl(profile.getIsf(NSProfile.secondsFromMidnight()).doubleValue(), units));
        mProfile.add("max_daily_safety_multiplier", SP.getInt("openapsama_max_daily_safety_multiplier", 3));
        mProfile.add("current_basal_safety_multiplier", SP.getInt("openapsama_current_basal_safety_multiplier", 4));
        mProfile.add("skip_neutral_temps", true);
        mProfile.add("current_basal", pump.getBaseBasalRate());
        mProfile.add("temptargetSet", tempTargetSet);
        mProfile.add("autosens_adjust_targets", SP.getBoolean("openapsama_autosens_adjusttargets", true));
        mProfile.add("min_5m_carbimpact", SP.getDouble("openapsama_min_5m_carbimpact", 3d));
        mV8rt.add(PARAM_profile, mProfile);

        mCurrentTemp = new V8Object(mV8rt);
        mCurrentTemp.add("temp", "absolute");
        mCurrentTemp.add("duration", pump.getTempBasalRemainingMinutes());
        mCurrentTemp.add("rate", pump.getTempBasalAbsoluteRate());

        // as we have non default temps longer than 30 mintues
        TempBasal tempBasal = pump.getTempBasal();
        if(tempBasal != null){
            mCurrentTemp.add("minutesrunning", tempBasal.getRealDuration());
        }

        mV8rt.add(PARAM_currentTemp, mCurrentTemp);

        mIobData = mV8rt.executeArrayScript(IobCobCalculatorPlugin.convertToJSONArray(iobArray).toString());
        mV8rt.add(PARAM_iobData, mIobData);

        mGlucoseStatus = new V8Object(mV8rt);
        mGlucoseStatus.add("glucose", glucoseStatus.glucose);

        if(SP.getBoolean("always_use_shortavg", false)){
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
        mV8rt.add(PARAM_meal_data, mMealData);

        if (MainApp.getConfigBuilder().isAMAModeEnabled()) {
            mAutosensData = new V8Object(mV8rt);
            mAutosensData.add("ratio", autosensDataRatio);
            mV8rt.add(PARAM_autosens_data, mAutosensData);
        } else {
            mV8rt.addUndefined(PARAM_autosens_data);
        }
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
