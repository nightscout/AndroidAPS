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
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.client.data.NSProfile;

public class DetermineBasalAdapterAMAJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterAMAJS.class);


    private ScriptReader mScriptReader = null;
    V8 mV8rt;
    private V8Object mProfile;
    private V8Object mGlucoseStatus;
    private V8Array mIobData;
    private V8Object mMealData;
    private V8Object mCurrentTemp;
    private V8Object mAutosensData;

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

        init();
        initLogCallback();
        initProcessExitCallback();
        initModuleParent();
        loadScript();
    }

    public void init() {
        // Profile
        mProfile = new V8Object(mV8rt);
        mProfile.add("max_iob", 0);
        mProfile.add("carbs_hr", 0);
        mProfile.add("dia", 0);
        mProfile.add("type", "current");
        mProfile.add("max_daily_basal", 0);
        mProfile.add("max_basal", 0);
        mProfile.add("max_bg", 0);
        mProfile.add("min_bg", 0);
        mProfile.add("carb_ratio", 0);
        mProfile.add("sens", 0);
        mProfile.add("max_daily_safety_multiplier", Constants.MAX_DAILY_SAFETY_MULTIPLIER);
        mProfile.add("current_basal_safety_multiplier", Constants.CURRENT_BASAL_SAFETY_MULTIPLIER);
        mProfile.add("skip_neutral_temps", true);
        mProfile.add("temptargetSet", false);
        mProfile.add("autosens_adjust_targets", false);
        mProfile.add("min_5m_carbimpact", 0);
        mProfile.add("current_basal", 0);
        mV8rt.add(PARAM_profile, mProfile);
        // Current temp
        mCurrentTemp = new V8Object(mV8rt);
        mCurrentTemp.add("temp", "absolute");
        mCurrentTemp.add("duration", 0);
        mCurrentTemp.add("rate", 0);
        mV8rt.add(PARAM_currentTemp, mCurrentTemp);
        // IOB data
//        mIobData = new V8Array(mV8rt);
//        mV8rt.add(PARAM_iobData, mIobData);
        // Glucose status
        mGlucoseStatus = new V8Object(mV8rt);
        mGlucoseStatus.add("glucose", 0);
        mGlucoseStatus.add("delta", 0);
        mGlucoseStatus.add("avgdelta", 0);
        mV8rt.add(PARAM_glucoseStatus, mGlucoseStatus);
        // Meal data
        mMealData = new V8Object(mV8rt);
        mMealData.add("carbs", 0);
        mMealData.add("boluses", 0);
        mMealData.add("mealCOB", 0.0d);
        mMealData.add("ratio", 0.0d);
        mV8rt.add(PARAM_meal_data, mMealData);
        // Autosens data
        mAutosensData = new V8Object(mV8rt);
        mV8rt.add(PARAM_autosens_data, mAutosensData);
    }

    public DetermineBasalResultAMA invoke() {

        log.debug(">>> Invoking detemine_basal <<<");
        log.debug("Glucose status: " + mV8rt.executeStringScript("JSON.stringify(" + PARAM_glucoseStatus + ");"));
        log.debug("IOB data:       " + mV8rt.executeStringScript("JSON.stringify(" + PARAM_iobData + ");"));
        log.debug("Current temp:   " + mV8rt.executeStringScript("JSON.stringify(" + PARAM_currentTemp + ");"));
        log.debug("Profile:        " + mV8rt.executeStringScript("JSON.stringify(" + PARAM_profile + ");"));
        log.debug("Meal data:      " + mV8rt.executeStringScript("JSON.stringify(" + PARAM_meal_data + ");"));
        log.debug("Autosens data:  " + mV8rt.executeStringScript("JSON.stringify(" + PARAM_autosens_data + ");"));

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
        if (Config.logAPSResult)
            log.debug("Result: " + ret);

        V8Object v8ObjectReuslt = mV8rt.getObject("rT");

        DetermineBasalResultAMA result = null;
        try {
            result = new DetermineBasalResultAMA(v8ObjectReuslt, new JSONObject(ret));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        storedGlucoseStatus = mV8rt.executeStringScript("JSON.stringify(" + PARAM_glucoseStatus + ");");
        storedIobData = mV8rt.executeStringScript("JSON.stringify(" + PARAM_iobData + ");");
        storedCurrentTemp = mV8rt.executeStringScript("JSON.stringify(" + PARAM_currentTemp + ");");
        storedProfile = mV8rt.executeStringScript("JSON.stringify(" + PARAM_profile + ");");
        storedMeal_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_meal_data + ");");
        storedAutosens_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_autosens_data + ");");

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
        mV8rt.executeVoidScript(readFile("OpenAPSAMA/round-basal.js"), "OpenAPSAMA/round-basal.js", 0);
        mV8rt.executeVoidScript("var round_basal = module.exports;");
        mV8rt.executeVoidScript("require = function() {return round_basal;};");

        mV8rt.executeVoidScript(readFile("OpenAPSAMA/basal-set-temp.js"), "OpenAPSAMA/basal-set-temp.js ", 0);
        mV8rt.executeVoidScript("var tempBasalFunctions = module.exports;");

        mV8rt.executeVoidScript(
                readFile("OpenAPSAMA/determine-basal.js"),
                "OpenAPSAMA/determine-basal.js",
                0);
        mV8rt.executeVoidScript("var determine_basal = module.exports;");
        mV8rt.executeVoidScript(
                "var setTempBasal = function (rate, duration, profile, rT, offline) {" +
                        "rT.duration = duration;\n" +
                        "    rT.rate = rate;" +
                        "return rT;" +
                        "};",
                "setTempBasal.js",
                0
        );
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

        mProfile.add("max_iob", maxIob);
        mProfile.add("carbs_hr", profile.getCarbAbsorbtionRate());
        mProfile.add("dia", profile.getDia());
        mProfile.add("type", "current");
        mProfile.add("max_daily_basal", profile.getMaxDailyBasal());
        mProfile.add("max_basal", maxBasal);
        mProfile.add("min_bg", minBg);
        mProfile.add("max_bg", maxBg);
        mProfile.add("target_bg", targetBg);
        mProfile.add("carb_ratio", profile.getIc(profile.secondsFromMidnight()));
        mProfile.add("sens", NSProfile.toMgdl(profile.getIsf(NSProfile.secondsFromMidnight()).doubleValue(), units));
        mProfile.add("current_basal", pump.getBaseBasalRate());
        mProfile.add("temptargetSet", tempTargetSet);
        mProfile.add("autosens_adjust_targets", MainApp.getConfigBuilder().isAMAModeEnabled());
        mProfile.add("min_5m_carbimpact", min_5m_carbimpact);

        mCurrentTemp.add("duration", pump.getTempBasalRemainingMinutes());
        mCurrentTemp.add("rate", pump.getTempBasalAbsoluteRate());

        mIobData = mV8rt.executeArrayScript(IobTotal.convertToJSONArray(iobArray).toString());
        mV8rt.add(PARAM_iobData, mIobData);

        mGlucoseStatus.add("glucose", glucoseStatus.glucose);
        mGlucoseStatus.add("delta", glucoseStatus.delta);
        mGlucoseStatus.add("short_avgdelta", glucoseStatus.short_avgdelta);
        mGlucoseStatus.add("long_avgdelta", glucoseStatus.long_avgdelta);

        mMealData.add("carbs", mealData.carbs);
        mMealData.add("boluses", mealData.boluses);
        mMealData.add("mealCOB", mealData.mealCOB);

        mAutosensData.add("ratio", autosensDataRatio);
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
