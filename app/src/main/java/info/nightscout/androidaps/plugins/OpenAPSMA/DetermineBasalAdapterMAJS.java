package info.nightscout.androidaps.plugins.OpenAPSMA;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.SP;

public class DetermineBasalAdapterMAJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterMAJS.class);

    private ScriptReader mScriptReader = null;
    private JSONObject mProfile;
    private JSONObject mGlucoseStatus;
    private JSONObject mIobData;
    private JSONObject mMealData;
    private JSONObject mCurrentTemp;

    private String storedCurrentTemp = null;
    public String storedIobData = null;
    private String storedGlucoseStatus = null;
    private String storedProfile = null;
    private String storedMeal_data = null;

    public DetermineBasalAdapterMAJS(ScriptReader scriptReader) throws IOException {
        mScriptReader = scriptReader;
    }

    public DetermineBasalResultMA invoke() {
        DetermineBasalResultMA determineBasalResultMA = null;

        Context rhino = Context.enter();
        Scriptable scope = rhino.initStandardObjects();
        // Turn off optimization to make Rhino Android compatible
        rhino.setOptimizationLevel(-1);

        try {

            //register logger callback for console.log and console.error
            ScriptableObject.defineClass(scope, LoggerCallback.class);
            Scriptable myLogger = rhino.newObject(scope, "LoggerCallback", null);
            scope.put("console", scope, myLogger);

            //set module parent
            rhino.evaluateString(scope, "var module = {\"parent\":Boolean(1)};", "JavaScript", 0, null);

            //generate functions "determine_basal" and "setTempBasal"
            rhino.evaluateString(scope, readFile("OpenAPSMA/determine-basal.js"), "JavaScript", 0, null);

            String setTempBasalCode = "var setTempBasal = function (rate, duration, profile, rT, offline) {" +
                    "rT.duration = duration;\n" +
                    "    rT.rate = rate;" +
                    "return rT;" +
                    "};";
            rhino.evaluateString(scope, setTempBasalCode, "setTempBasal.js", 0, null);
            Object determineBasalObj = scope.get("determine_basal", scope);
            Object setTempBasalObj = scope.get("setTempBasal", scope);

            //call determine-basal
            if (determineBasalObj instanceof Function && setTempBasalObj instanceof Function) {
                Function determineBasalJS = (Function) determineBasalObj;
                Function setTempBasalJS = (Function) setTempBasalObj;

                //prepare parameters
                Object[] params = new Object[]{
                        makeParam(mGlucoseStatus, rhino, scope),
                        makeParam(mCurrentTemp, rhino, scope),
                        makeParam(mIobData, rhino, scope),
                        makeParam(mProfile, rhino, scope),
                        "undefined",
                        makeParam(mMealData, rhino, scope),
                        setTempBasalJS};

                NativeObject jsResult = (NativeObject) determineBasalJS.call(rhino, scope, scope, params);

                // Parse the jsResult object to a JSON-String
                String result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString();
                if (Config.logAPSResult)
                    log.debug("Result: " + result);
                try {
                    determineBasalResultMA = new DetermineBasalResultMA(jsResult, new JSONObject(result));
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            } else {
                log.debug("Problem loading JS Functions");
            }
        } catch (IOException e) {
            log.debug("IOException");
        } catch (RhinoException e) {
            log.error("RhinoException: (" + e.lineNumber() + "," + e.columnNumber() + ") " + e.toString());
        } catch (IllegalAccessException e) {
            log.error(e.toString());
        } catch (InstantiationException e) {
            log.error(e.toString());
        } catch (InvocationTargetException e) {
            log.error(e.toString());
        } finally {
            Context.exit();
        }

        storedGlucoseStatus = mGlucoseStatus.toString();
        storedIobData = mIobData.toString();
        storedCurrentTemp = mCurrentTemp.toString();
        storedProfile = mProfile.toString();
        storedMeal_data = mMealData.toString();

        return determineBasalResultMA;
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

    public void setData(Profile profile,
                        double maxIob,
                        double maxBasal,
                        double minBg,
                        double maxBg,
                        double targetBg,
                        double basalRate,
                        IobTotal iobData,
                        GlucoseStatus glucoseStatus,
                        MealData mealData) throws JSONException {

        String units = profile.getUnits();

        mProfile = new JSONObject();
        mProfile.put("max_iob", maxIob);
        mProfile.put("dia", Math.min(profile.getDia(), 3d));
        mProfile.put("type", "current");
        mProfile.put("max_daily_basal", profile.getMaxDailyBasal());
        mProfile.put("max_basal", maxBasal);
        mProfile.put("min_bg", minBg);
        mProfile.put("max_bg", maxBg);
        mProfile.put("target_bg", targetBg);
        mProfile.put("carb_ratio", profile.getIc());
        mProfile.put("sens", Profile.toMgdl(profile.getIsf(), units));

        mProfile.put("current_basal", basalRate);

        if (units.equals(Constants.MMOL)) {
            mProfile.put("out_units", "mmol/L");
        }

        long now = System.currentTimeMillis();
        TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);

        mCurrentTemp = new JSONObject();
        mCurrentTemp.put("duration", tb != null ? tb.getPlannedRemainingMinutes() : 0);
        mCurrentTemp.put("rate", tb != null ? tb.tempBasalConvertedToAbsolute(now, profile) : 0d);

        mIobData = new JSONObject();
        mIobData.put("iob", iobData.iob); //netIob
        mIobData.put("activity", iobData.activity); //netActivity
        mIobData.put("bolussnooze", iobData.bolussnooze); //bolusIob
        mIobData.put("basaliob", iobData.basaliob);
        mIobData.put("netbasalinsulin", iobData.netbasalinsulin);
        mIobData.put("hightempinsulin", iobData.hightempinsulin);

        mGlucoseStatus = new JSONObject();
        mGlucoseStatus.put("glucose", glucoseStatus.glucose);
        if (SP.getBoolean(R.string.key_always_use_shortavg, false)) {
            mGlucoseStatus.put("delta", glucoseStatus.short_avgdelta);
        } else {
            mGlucoseStatus.put("delta", glucoseStatus.delta);
        }
        mGlucoseStatus.put("avgdelta", glucoseStatus.avgdelta);

        mMealData = new JSONObject();
        mMealData.put("carbs", mealData.carbs);
        mMealData.put("boluses", mealData.boluses);
    }

    public String readFile(String filename) throws IOException {
        byte[] bytes = mScriptReader.readFile(filename);
        String string = new String(bytes, "UTF-8");
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20);
        }
        return string;
    }

    public Object makeParam(JSONObject jsonObject, Context rhino, Scriptable scope) {
        Object param = NativeJSON.parse(rhino, scope, jsonObject.toString(), new Callable() {
            @Override
            public Object call(Context context, Scriptable scriptable, Scriptable scriptable1, Object[] objects) {
                return objects[1];
            }
        });
        return param;
    }

}
