package info.nightscout.androidaps.plugins.OpenAPSMA;

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
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.SP;

public class DetermineBasalAdapterMAJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterMAJS.class);


    private ScriptReader mScriptReader = null;
    V8 mV8rt;
    private V8Object mProfile;
    private V8Object mGlucoseStatus;
    private V8Object mIobData;
    private V8Object mMealData;
    private V8Object mCurrentTemp;

    private final String PARAM_currentTemp = "currentTemp";
    private final String PARAM_iobData = "iobData";
    private final String PARAM_glucoseStatus = "glucose_status";
    private final String PARAM_profile = "profile";
    private final String PARAM_meal_data = "meal_data";

    private String storedCurrentTemp = null;
    public String storedIobData = null;
    private String storedGlucoseStatus = null;
    private String storedProfile = null;
    private String storedMeal_data = null;

     /**
     *  Main code
     */

    public DetermineBasalAdapterMAJS(ScriptReader scriptReader) throws IOException {
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
        mProfile.add("dia", 0);
        mProfile.add("type", "current");
        mProfile.add("max_daily_basal", 0);
        mProfile.add("max_basal", 0);
        mProfile.add("max_bg", 0);
        mProfile.add("min_bg", 0);
        mProfile.add("carb_ratio", 0);
        mProfile.add("sens", 0);
        mProfile.add("current_basal", 0);
        mV8rt.add(PARAM_profile, mProfile);
        // Current temp
        mCurrentTemp = new V8Object(mV8rt);
        mCurrentTemp.add("temp", "absolute");
        mCurrentTemp.add("duration", 0);
        mCurrentTemp.add("rate", 0);
        mV8rt.add(PARAM_currentTemp, mCurrentTemp);
        // IOB data
        mIobData = new V8Object(mV8rt);
        mIobData.add("iob", 0); //netIob
        mIobData.add("activity", 0); //netActivity
        mIobData.add("bolussnooze", 0); //bolusIob
        mIobData.add("basaliob", 0);
        mIobData.add("netbasalinsulin", 0);
        mIobData.add("hightempinsulin", 0);
        mV8rt.add(PARAM_iobData, mIobData);
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
        mV8rt.add(PARAM_meal_data, mMealData);
    }

    public DetermineBasalResultMA invoke() {
        mV8rt.executeVoidScript(
                "console.error(\"determine_basal(\"+\n" +
                        "JSON.stringify(" + PARAM_glucoseStatus + ")+ \", \" +\n" +
                        "JSON.stringify(" + PARAM_currentTemp +   ")+ \", \" +\n" +
                        "JSON.stringify(" + PARAM_iobData +       ")+ \", \" +\n" +
                        "JSON.stringify(" + PARAM_profile +       ")+ \", \" +\n" +
                        "JSON.stringify(" + PARAM_meal_data +     ")+ \") \");"
        );
        mV8rt.executeVoidScript(
                "var rT = determine_basal(" +
                        PARAM_glucoseStatus + ", " +
                        PARAM_currentTemp + ", " +
                        PARAM_iobData + ", " +
                        PARAM_profile + ", " +
                        "undefined, " +
                        PARAM_meal_data + ", " +
                        "setTempBasal" +
                        ");");


        String ret = mV8rt.executeStringScript("JSON.stringify(rT);");
        if (Config.logAPSResult)
            log.debug("Result: " + ret);

        V8Object v8ObjectReuslt = mV8rt.getObject("rT");

        DetermineBasalResultMA result = null;
        try {
            result = new DetermineBasalResultMA(v8ObjectReuslt, new JSONObject(ret));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        storedGlucoseStatus = mV8rt.executeStringScript("JSON.stringify(" + PARAM_glucoseStatus + ");");
        storedIobData = mV8rt.executeStringScript("JSON.stringify(" + PARAM_iobData + ");");
        storedCurrentTemp = mV8rt.executeStringScript("JSON.stringify(" + PARAM_currentTemp + ");");
        storedProfile = mV8rt.executeStringScript("JSON.stringify(" + PARAM_profile + ");");
        storedMeal_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_meal_data + ");");

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

    private void loadScript() throws IOException {
        mV8rt.executeVoidScript(
                readFile("OpenAPSMA/determine-basal.js"),
                "OpenAPSMA/bin/oref0-determine-basal.js",
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
                if (parameters.length() > 0) {
                    Object arg1 = parameters.get(0);
                    if (Config.logAPSResult)
                        log.debug("Input params: " + arg1);
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
                        IobTotal iobData,
                        GlucoseStatus glucoseStatus,
                        MealData mealData) {

        String units = profile.getUnits();

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

        mProfile.add("current_basal", pump.getBaseBasalRate());
        mCurrentTemp.add("duration", pump.getTempBasalRemainingMinutes());
        mCurrentTemp.add("rate", pump.getTempBasalAbsoluteRate());

        mIobData.add("iob", iobData.iob); //netIob
        mIobData.add("activity", iobData.activity); //netActivity
        mIobData.add("bolussnooze", iobData.bolussnooze); //bolusIob
        mIobData.add("basaliob", iobData.basaliob);
        mIobData.add("netbasalinsulin", iobData.netbasalinsulin);
        mIobData.add("hightempinsulin", iobData.hightempinsulin);

        mGlucoseStatus.add("glucose", glucoseStatus.glucose);
        if(SP.getBoolean("always_use_shortavg", false)){
            mGlucoseStatus.add("delta", glucoseStatus.short_avgdelta);
        } else {
            mGlucoseStatus.add("delta", glucoseStatus.delta);
        }
        mGlucoseStatus.add("avgdelta", glucoseStatus.avgdelta);

        mMealData.add("carbs", mealData.carbs);
        mMealData.add("boluses", mealData.boluses);
    }


     public void release() {
        mProfile.release();
        mCurrentTemp.release();
        mIobData.release();
        mMealData.release();
        mGlucoseStatus.release();
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
