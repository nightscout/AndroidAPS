package info.nightscout.androidaps.plugins.aps.openAPSAMA;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader;
import info.nightscout.androidaps.plugins.aps.openAPSMA.LoggerCallback;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

public class DetermineBasalAdapterAMAJS {
    private static Logger log = LoggerFactory.getLogger(L.APS);


    private ScriptReader mScriptReader = null;

    private JSONObject mProfile;
    private JSONObject mGlucoseStatus;
    private JSONArray mIobData;
    private JSONObject mMealData;
    private JSONObject mCurrentTemp;
    private JSONObject mAutosensData = null;

    private String storedCurrentTemp = null;
    private String storedIobData = null;
    private String storedGlucoseStatus = null;
    private String storedProfile = null;
    private String storedMeal_data = null;
    private String storedAutosens_data = null;

    private String scriptDebug = "";

    public DetermineBasalAdapterAMAJS(ScriptReader scriptReader) {
        mScriptReader = scriptReader;
    }

    public DetermineBasalResultAMA invoke() {

        if (L.isEnabled(L.APS)) {
            log.debug(">>> Invoking detemine_basal <<<");
            log.debug("Glucose status: " + (storedGlucoseStatus = mGlucoseStatus.toString()));
            log.debug("IOB data:       " + (storedIobData = mIobData.toString()));
            log.debug("Current temp:   " + (storedCurrentTemp = mCurrentTemp.toString()));
            log.debug("Profile:        " + (storedProfile = mProfile.toString()));
            log.debug("Meal data:      " + (storedMeal_data = mMealData.toString()));
            if (mAutosensData != null)
                log.debug("Autosens data:  " + (storedAutosens_data = mAutosensData.toString()));
            else
                log.debug("Autosens data:  " + (storedAutosens_data = "undefined"));
        }

        DetermineBasalResultAMA determineBasalResultAMA = null;

        Context rhino = Context.enter();
        Scriptable scope = rhino.initStandardObjects();
        // Turn off optimization to make Rhino Android compatible
        rhino.setOptimizationLevel(-1);

        try {

            //register logger callback for console.log and console.error
            ScriptableObject.defineClass(scope, LoggerCallback.class);
            Scriptable myLogger = rhino.newObject(scope, "LoggerCallback", null);
            scope.put("console2", scope, myLogger);
            rhino.evaluateString(scope, readFile("OpenAPSAMA/loggerhelper.js"), "JavaScript", 0, null);

            //set module parent
            rhino.evaluateString(scope, "var module = {\"parent\":Boolean(1)};", "JavaScript", 0, null);
            rhino.evaluateString(scope, "var round_basal = function round_basal(basal, profile) { return basal; };", "JavaScript", 0, null);
            rhino.evaluateString(scope, "require = function() {return round_basal;};", "JavaScript", 0, null);

            //generate functions "determine_basal" and "setTempBasal"
            rhino.evaluateString(scope, readFile("OpenAPSAMA/determine-basal.js"), "JavaScript", 0, null);
            rhino.evaluateString(scope, readFile("OpenAPSAMA/basal-set-temp.js"), "setTempBasal.js", 0, null);
            Object determineBasalObj = scope.get("determine_basal", scope);
            Object setTempBasalFunctionsObj = scope.get("tempBasalFunctions", scope);

            //call determine-basal
            if (determineBasalObj instanceof Function && setTempBasalFunctionsObj instanceof NativeObject) {
                Function determineBasalJS = (Function) determineBasalObj;

                //prepare parameters
                Object[] params = new Object[]{
                        makeParam(mGlucoseStatus, rhino, scope),
                        makeParam(mCurrentTemp, rhino, scope),
                        makeParamArray(mIobData, rhino, scope),
                        makeParam(mProfile, rhino, scope),
                        makeParam(mAutosensData, rhino, scope),
                        makeParam(mMealData, rhino, scope),
                        setTempBasalFunctionsObj};

                NativeObject jsResult = (NativeObject) determineBasalJS.call(rhino, scope, scope, params);
                scriptDebug = LoggerCallback.getScriptDebug();

                // Parse the jsResult object to a JSON-String
                String result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString();
                if (L.isEnabled(L.APS))
                    log.debug("Result: " + result);
                try {
                    determineBasalResultAMA = new DetermineBasalResultAMA(jsResult, new JSONObject(result));
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            } else {
                log.error("Problem loading JS Functions");
            }
        } catch (IOException e) {
            log.error("IOException");
        } catch (RhinoException e) {
            log.error("RhinoException: (" + e.lineNumber() + "," + e.columnNumber() + ") " + e.toString());
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            log.error(e.toString());
        } finally {
            Context.exit();
        }

        storedGlucoseStatus = mGlucoseStatus.toString();
        storedIobData = mIobData.toString();
        storedCurrentTemp = mCurrentTemp.toString();
        storedProfile = mProfile.toString();
        storedMeal_data = mMealData.toString();

        return determineBasalResultAMA;

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

    public void setData(Profile profile,
                        double maxIob,
                        double maxBasal,
                        double minBg,
                        double maxBg,
                        double targetBg,
                        double basalrate,
                        IobTotal[] iobArray,
                        GlucoseStatus glucoseStatus,
                        MealData mealData,
                        double autosensDataRatio,
                        boolean tempTargetSet) throws JSONException {

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
        mProfile.put("max_daily_safety_multiplier", SP.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3));
        mProfile.put("current_basal_safety_multiplier", SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d));
        mProfile.put("skip_neutral_temps", true);
        mProfile.put("current_basal", basalrate);
        mProfile.put("temptargetSet", tempTargetSet);
        mProfile.put("autosens_adjust_targets", SP.getBoolean(R.string.key_openapsama_autosens_adjusttargets, true));
        //align with max-absorption model in AMA sensitivity
        if (mealData.usedMinCarbsImpact > 0) {
            mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        } else {
            mProfile.put("min_5m_carbimpact", SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact));
        }

        if (units.equals(Constants.MMOL)) {
            mProfile.put("out_units", "mmol/L");
        }

        long now = System.currentTimeMillis();
        TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);

        mCurrentTemp = new JSONObject();
        mCurrentTemp.put("temp", "absolute");
        mCurrentTemp.put("duration", tb != null ? tb.getPlannedRemainingMinutes() : 0);
        mCurrentTemp.put("rate", tb != null ? tb.tempBasalConvertedToAbsolute(now, profile) : 0d);

        // as we have non default temps longer than 30 mintues
        TemporaryBasal tempBasal = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        if (tempBasal != null) {
            mCurrentTemp.put("minutesrunning", tempBasal.getRealDuration());
        }

        mIobData = IobCobCalculatorPlugin.convertToJSONArray(iobArray);

        mGlucoseStatus = new JSONObject();
        mGlucoseStatus.put("glucose", glucoseStatus.glucose);

        if (SP.getBoolean(R.string.key_always_use_shortavg, false)) {
            mGlucoseStatus.put("delta", glucoseStatus.short_avgdelta);
        } else {
            mGlucoseStatus.put("delta", glucoseStatus.delta);
        }
        mGlucoseStatus.put("short_avgdelta", glucoseStatus.short_avgdelta);
        mGlucoseStatus.put("long_avgdelta", glucoseStatus.long_avgdelta);

        mMealData = new JSONObject();
        mMealData.put("carbs", mealData.carbs);
        mMealData.put("boluses", mealData.boluses);
        mMealData.put("mealCOB", mealData.mealCOB);

        if (MainApp.getConstraintChecker().isAutosensModeEnabled().value()) {
            mAutosensData = new JSONObject();
            mAutosensData.put("ratio", autosensDataRatio);
        } else {
            mAutosensData = null;
        }
    }


    private Object makeParam(JSONObject jsonObject, Context rhino, Scriptable scope) {

        if (jsonObject == null) return Undefined.instance;

        Object param = NativeJSON.parse(rhino, scope, jsonObject.toString(), (context, scriptable, scriptable1, objects) -> objects[1]);
        return param;
    }

    private Object makeParamArray(JSONArray jsonArray, Context rhino, Scriptable scope) {
        //Object param = NativeJSON.parse(rhino, scope, "{myarray: " + jsonArray.toString() + " }", new Callable() {
        Object param = NativeJSON.parse(rhino, scope, jsonArray.toString(), (context, scriptable, scriptable1, objects) -> objects[1]);
        return param;
    }

    private String readFile(String filename) throws IOException {
        byte[] bytes = mScriptReader.readFile(filename);
        String string = new String(bytes, "UTF-8");
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20);
        }
        return string;
    }

}
