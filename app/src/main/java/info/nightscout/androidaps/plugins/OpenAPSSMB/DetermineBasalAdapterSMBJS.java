package info.nightscout.androidaps.plugins.OpenAPSSMB;

import org.json.JSONArray;
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
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSMA.LoggerCallback;
import info.nightscout.utils.SP;

public class DetermineBasalAdapterSMBJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterSMBJS.class);


    private ScriptReader mScriptReader = null;
    private JSONObject mProfile;
    private JSONObject mGlucoseStatus;
    private JSONArray mIobData;
    private JSONObject mMealData;
    private JSONObject mCurrentTemp;
    private JSONObject mAutosensData = null;
    private boolean mMicrobolusAllowed;

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
        mScriptReader = scriptReader;
    }


    public DetermineBasalResultSMB invoke() {


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
        log.debug("Reservoir data: " + "undefined");
        log.debug("MicroBolusAllowed:  " + (storedMicroBolusAllowed = "" + mMicrobolusAllowed));

        DetermineBasalResultSMB determineBasalResultSMB = null;

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
            rhino.evaluateString(scope, readFile("OpenAPSSMB/determine-basal.js"), "JavaScript", 0, null);
            rhino.evaluateString(scope, readFile("OpenAPSSMB/basal-set-temp.js"), "setTempBasal.js", 0, null);
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
                        setTempBasalFunctionsObj,
                        new Boolean(mMicrobolusAllowed),
                        makeParam(null, rhino, scope) // reservoir data as undefined
                };


                NativeObject jsResult = (NativeObject) determineBasalJS.call(rhino, scope, scope, params);
                scriptDebug = LoggerCallback.getScriptDebug();

                // Parse the jsResult object to a JSON-String
                String result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString();
                if (Config.logAPSResult)
                    log.debug("Result: " + result);
                try {
                    determineBasalResultSMB = new DetermineBasalResultSMB(new JSONObject(result));
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

        return determineBasalResultSMB;

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
                        boolean tempTargetSet,
                        boolean microBolusAllowed
    ) throws JSONException {

        String units = profile.getUnits();

        mProfile = new JSONObject();
        ;
        mProfile.put("max_iob", maxIob);
        mProfile.put("dia", profile.getDia());
        mProfile.put("type", "current");
        mProfile.put("max_daily_basal", profile.getMaxDailyBasal());
        mProfile.put("max_basal", maxBasal);
        mProfile.put("min_bg", minBg);
        mProfile.put("max_bg", maxBg);
        mProfile.put("target_bg", targetBg);
        mProfile.put("carb_ratio", profile.getIc());
        mProfile.put("sens", Profile.toMgdl(profile.getIsf().doubleValue(), units));
        mProfile.put("max_daily_safety_multiplier", SP.getInt("openapsama_max_daily_safety_multiplier", 3));
        mProfile.put("current_basal_safety_multiplier", SP.getInt("openapsama_current_basal_safety_multiplier", 4));
        mProfile.put("skip_neutral_temps", true);
        mProfile.put("current_basal", basalrate);
        mProfile.put("temptargetSet", tempTargetSet);
        mProfile.put("autosens_adjust_targets", SP.getBoolean("openapsama_autosens_adjusttargets", true));
        mProfile.put("min_5m_carbimpact", SP.getDouble("openapsama_min_5m_carbimpact", 3d));
        mProfile.put("enableSMB_with_bolus", SP.getBoolean(R.string.key_use_smb, false));
        mProfile.put("enableSMB_with_COB", SP.getBoolean(R.string.key_use_smb, false));
        mProfile.put("enableSMB_with_temptarget", SP.getBoolean(R.string.key_use_smb, false));
        mProfile.put("enableUAM", SP.getBoolean(R.string.key_use_uam, false));
        mProfile.put("adv_target_adjustments", true); // lower target automatically when BG and eventualBG are high
        // create maxCOB and default it to 120 because that's the most a typical body can absorb over 4 hours.
        // (If someone enters more carbs or stacks more; OpenAPS will just truncate dosing based on 120.
        // Essentially, this just limits AMA as a safety cap against weird COB calculations)
        mProfile.put("maxCOB", 120);
        mProfile.put("autotune_isf_adjustmentFraction", 0.5); // keep autotune ISF closer to pump ISF via a weighted average of fullNewISF and pumpISF.  1.0 allows full adjustment, 0 is no adjustment from pump ISF.
        mProfile.put("remainingCarbsFraction", 1.0d); // fraction of carbs we'll assume will absorb over 4h if we don't yet see carb absorption
        mProfile.put("remainingCarbsCap", 90); // max carbs we'll assume will absorb over 4h if we don't yet see carb absorption

        mCurrentTemp = new JSONObject();
        ;
        mCurrentTemp.put("temp", "absolute");
        mCurrentTemp.put("duration", MainApp.getConfigBuilder().getTempBasalRemainingMinutesFromHistory());
        mCurrentTemp.put("rate", MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory());

        // as we have non default temps longer than 30 mintues
        TemporaryBasal tempBasal = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        if (tempBasal != null) {
            mCurrentTemp.put("minutesrunning", tempBasal.getRealDuration());
        }

        mIobData = IobCobCalculatorPlugin.convertToJSONArray(iobArray);

        mGlucoseStatus = new JSONObject();
        ;
        mGlucoseStatus.put("glucose", glucoseStatus.glucose);

        if (SP.getBoolean("always_use_shortavg", false)) {
            mGlucoseStatus.put("delta", glucoseStatus.short_avgdelta);
        } else {
            mGlucoseStatus.put("delta", glucoseStatus.delta);
        }
        mGlucoseStatus.put("short_avgdelta", glucoseStatus.short_avgdelta);
        mGlucoseStatus.put("long_avgdelta", glucoseStatus.long_avgdelta);

        mMealData = new JSONObject();
        ;
        mMealData.put("carbs", mealData.carbs);
        mMealData.put("boluses", mealData.boluses);
        mMealData.put("mealCOB", mealData.mealCOB);
        mMealData.put("minDeviationSlope", mealData.minDeviationSlope);
        mMealData.put("lastBolusTime", mealData.lastBolusTime);

        if (MainApp.getConfigBuilder().isAMAModeEnabled()) {
            mAutosensData = new JSONObject();
            ;
            mAutosensData.put("ratio", autosensDataRatio);
        } else {
            mAutosensData = null;
        }
        mMicrobolusAllowed = microBolusAllowed;

    }

    public Object makeParam(JSONObject jsonObject, Context rhino, Scriptable scope) {

        if (jsonObject == null) return Undefined.instance;

        Object param = NativeJSON.parse(rhino, scope, jsonObject.toString(), new Callable() {
            @Override
            public Object call(Context context, Scriptable scriptable, Scriptable scriptable1, Object[] objects) {
                return objects[1];
            }
        });
        return param;
    }

    public Object makeParamArray(JSONArray jsonArray, Context rhino, Scriptable scope) {
        //Object param = NativeJSON.parse(rhino, scope, "{myarray: " + jsonArray.toString() + " }", new Callable() {
        Object param = NativeJSON.parse(rhino, scope, jsonArray.toString(), new Callable() {
            @Override
            public Object call(Context context, Scriptable scriptable, Scriptable scriptable1, Object[] objects) {
                return objects[1];
            }
        });
        return param;
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
