package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.util.TimeZone;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import info.nightscout.androidaps.utils.SafeParse;

public class Opts {
    public Profile profile;
    public String profilename;
    public Profile pumpprofile;
    public String pumpprofilename;
    public List<BgReading> glucose;
    public List<NsTreatment> pumpHistory;
    public List<ExtendedBolus> pumpExtBolusHistory;
    public List<TemporaryBasal> pumpTempBasalHistory;
    public List<CareportalEvent> careportalEvents;
    public List<NsTreatment> nsTreatments;
    public List<Treatment> treatments;
    public long start;
    public long end;
    public boolean categorize_uam_as_basal;
    public boolean tune_insulin_curve;
    @Inject AAPSLogger aapsLogger;
    @Inject ProfileFunction profileFunction;
    @Inject ActivePluginProvider activePlugin;
    @Inject SP sp;
    @Inject TreatmentsPlugin treatmentsPlugin;
//    @Inject public info.nightscout.androidaps.utils.sharedPreferences.SP sp;

    private final HasAndroidInjector injector;
    public boolean ascending;

    public Opts() {
        injector = MainApp.instance();
        injector.androidInjector().inject(this);
    }

    public Opts(
        HasAndroidInjector injector
    ) {
        this.injector = injector;
        this.injector.androidInjector().inject(this);
    }

    public void setGlucose (long from, long to, boolean ascending) {
        MainApp.getDbHelper().getBgreadingsDataFromTime(from, to, ascending);
        glucose  = MainApp.getDbHelper().getBgreadingsDataFromTime(from,to, ascending);
    }


    public void setTreaments(long from, long to, boolean ascending) {
        this.ascending=ascending;
        pumpHistory = new ArrayList<NsTreatment>();
        setTempBasalHistory(MainApp.getDbHelper().getTemporaryBasalsDataFromTime(from,to,ascending));
        setExtBolusHistory(MainApp.getDbHelper().getExtendedBolusDataFromTime(from,to,ascending));
        setTreatments(treatmentsPlugin.getService().getTreatmentDataFromTime(from,to,ascending));
        careportalEvents=MainApp.getDbHelper().getCareportalEvents(from,to,ascending);
        if (ascending)
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o1.date  - o2.date) );
        else
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o2.date  - o1.date) );
    }

    public void setTempBasalHistory( List<TemporaryBasal> lt) {
        if (pumpHistory==null)
            pumpHistory = new ArrayList<NsTreatment>();
        pumpTempBasalHistory=lt;
        //Convert
        for (TemporaryBasal tp:pumpTempBasalHistory ) {
            Profile ps = profileFunction.getProfile(tp.date);
            if (ps!=null)
                tp.absoluteRate = tp.tempBasalConvertedToAbsolute(tp.date, ps);
            pumpHistory.add(new NsTreatment(tp));
        }
        if (ascending)
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o1.date  - o2.date) );
        else
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o2.date  - o1.date) );
    }

    public void setExtBolusHistory(List<ExtendedBolus> lt) {
        if (pumpHistory==null)
            pumpHistory = new ArrayList<NsTreatment>();
        pumpExtBolusHistory=lt;
        for (ExtendedBolus t:pumpExtBolusHistory) {
            pumpHistory.add(new NsTreatment(t));
        }
        if (ascending)
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o1.date  - o2.date) );
        else
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o2.date  - o1.date) );
    }

    public void setTreatments(List<Treatment> lt) {
        if (pumpHistory==null)
            pumpHistory = new ArrayList<NsTreatment>();
        treatments=lt;
        nsTreatments = new ArrayList<NsTreatment>();
        for (Treatment t:treatments) {
            pumpHistory.add(new NsTreatment(t));
            nsTreatments.add(new NsTreatment(t));
        }
        if (ascending)
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o1.date  - o2.date) );
        else
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o2.date  - o1.date) );

    }

    public void setCareportalEvents(List<CareportalEvent> lt) {
        if (pumpHistory==null)
            pumpHistory = new ArrayList<NsTreatment>();
        careportalEvents=lt;
        for (CareportalEvent t:careportalEvents) {
            pumpHistory.add(new NsTreatment(t));
        }
        if (ascending)
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o1.date  - o2.date) );
        else
            Collections.sort(pumpHistory, (o1, o2) -> (int) (o2.date  - o1.date) );
    }

    // on each loop glucose containts only one day BG Value
    public JSONArray glucosetoJSON()  {
        JSONArray glucoseJson = new JSONArray();
        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));
        BgSourceInterface activeBgSource = activePlugin.getActiveBgSource();
        //String device = activeBgSource.getClass().getTypeName();
        try {
            for (BgReading bgreading:glucose ) {
                JSONObject bgjson = new JSONObject();
                bgjson.put("_id",bgreading._id);
                bgjson.put("device","AndroidAPS");
                bgjson.put("date",bgreading.date);
                bgjson.put("dateString", DateUtil.toISOString(bgreading.date));
                bgjson.put("sgv",bgreading.value);
                bgjson.put("direction",bgreading.direction);
                bgjson.put("type","sgv");
                bgjson.put("systime", DateUtil.toISOString(bgreading.date));
                bgjson.put("utcOffset", utcOffset);
                glucoseJson.put(bgjson);
            }
        } catch (JSONException e) {}
        return glucoseJson;
    }


    public JSONArray nsHistorytoJSON() {
        JSONArray json = new JSONArray();
        for (NsTreatment t: pumpHistory ) {
            if (t.isValid)
                json.put(t.toJson());
        }
        return json;
    }


    public JSONObject profiletoOrefJSON()  {
        // Create a json profile with oref0 format
        // Include min_5m_carbimpact, insulin type, single value for carb_ratio and isf
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject convertedProfile = new JSONObject();
        int basalIncrement = 60 ;
        InsulinInterface insulinInterface = activePlugin.getActiveInsulin();

        try {
            json.put("name",profilename);
            json.put("min_5m_carbimpact",sp.getDouble("openapsama_min_5m_carbimpact", 3.0));
            json.put("dia", profile.getDia());

            JSONArray basals = new JSONArray();
            for (int h = 0; h < 24; h++) {
                int secondfrommidnight = h * 60 * 60;
                String time;
                time = (h<10 ? "0"+ h : h)  + ":00:00";
                //basals.put(new JSONObject().put("start", time).put("minutes", h * basalIncrement).put("rate", getProfileBasal(h)));
                basals.put(new JSONObject().put("start", time).put("minutes", h * basalIncrement).put("rate", profile.getBasalTimeFromMidnight(secondfrommidnight)));
            };
            json.put("basalprofile", basals);
            int isfvalue = (int) profile.getIsfMgdl();
            json.put("isfProfile",new JSONObject().put("sensitivities",new JSONArray().put(new JSONObject().put("i",0).put("start","00:00:00").put("sensitivity",isfvalue).put("offset",0).put("x",0).put("endoffset",1440))));
            // json.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("carb_ratio", 0d))));
            json.put("carb_ratio", profile.getIc());
            json.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")));
            json.put("autosens_min", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_min, "0.7")));
            json.put("units",sp.getString(R.string.key_units, "mg/dl"));
            json.put("timezone",TimeZone.getDefault().getID());
            if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
                json.put("curve","ultra-rapid");
            else if (insulinInterface.getId() == InsulinInterface.OREF_RAPID_ACTING)
                json.put("curve","rapid-acting");
            else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK) {
                json.put("curve", "bilinear");
                json.put("insulinpeaktime",sp.getInt(MainApp.gs(R.string.key_insulin_oref_peak),75));
            }

        } catch (JSONException e) {}

        return json;
    }


    /*********************************************************************************************************************************************
     *  All code below is for helping data analysis if I see unconsistencies between AAPS data and NS data downloaded by oref0-autotune
     *  (call in AutotunePlugin and line below could be removed in final version)
     *********************************************************************************************************************************************/

    /*
    public JSONArray nsTreatmenttoJSON() {
        JSONArray json = new JSONArray();
        int idxT=0;
        int idxP=0;
        Collections.sort(pumpHistory, (o1, o2) -> (int) (o2.date  - o1.date) );
        Collections.sort(treatments, (o1, o2) -> (int) (o2.date  - o1.date) );
        for(int i=0; i < (pumpHistory.size()+treatments.size()-1);i++) {
            Treatment tr = new Treatment();
            TemporaryBasal tb = new TemporaryBasal();
            if(idxT<treatments.size())
                tr = treatments.get(idxT);
            if(idxP<pumpHistory.size())
                tb = pumpHistory.get(idxP);
            if(idxP==pumpHistory.size() || tr.date > tb.date) {
                if(tr.isValid) {
                    json.put(treatmentjson(tr));
                }
                idxT++;
            } else if (idxT==treatments.size() || tr.date <= tb.date ) {
                if(tb.isValid) {
                    json.put(tempBasaljson(tb));
                }
                idxP++;
            }
        }

        return json;
    }
    */
    /*
    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray pumpHistorytoJSON(long starttime, long endtime)  {
        JSONArray json = new JSONArray();
        try {
            for (CareportalEvent cp:pumpHistory ) {
                JSONObject cPjson = new JSONObject();

                if(cp.date >= starttime && cp.date <= endtime && cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType",cp.eventType);
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    JSONObject object = new JSONObject(cp.json);
                    Iterator it = object.keys();
                    while (it.hasNext()) {
                        String key = (String)it.next();
                        cPjson.put(key, object.get(key));
                    }
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }
    */
    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray pumpExtBolusHistorytoJSON()  {
        JSONArray json = new JSONArray();
        try {
            for (ExtendedBolus cp:pumpExtBolusHistory ) {
                JSONObject cPjson = new JSONObject();

                if(cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType","Extended Bolus");
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOString(new Date(cp.date),"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC")));
                    cPjson.put("insulin",cp.insulin);
                    cPjson.put("insulinrate",cp.absoluteRate());
                    cPjson.put("realDuration",cp.getRealDuration());
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }


    public JSONArray treatmentstoJSON()  {
        JSONArray json = new JSONArray();
        for (Treatment cp:treatments ) {
            if(cp.isValid) {
                json.put(treatmentjson(cp));
            }
        }
        return json;
    }

    public JSONArray pumpTempBasalHistorytoJSON()  {
        JSONArray json = new JSONArray();
        for (TemporaryBasal tp:pumpTempBasalHistory ) {
            if(tp.isValid) {
                json.put(tempBasaljson(tp));
            }
        }
        return json;
    }


    private JSONObject treatmentjson(Treatment cp) {
        JSONObject cPjson = new JSONObject();
        try {
            String eventType = "";
            if(cp.insulin > 0 && cp.carbs > 0)
                eventType = "Bolus Wizard";
            else if (cp.carbs > 0)
                eventType = "Carb Correction";
            else
                eventType = "Correction Bolus";
            cPjson.put("_id", cp._id);
            cPjson.put("eventType",eventType);
            cPjson.put("insulin",cp.insulin > 0 ? cp.insulin : JSONObject.NULL);
            cPjson.put("carbs",cp.carbs > 0 ? cp.carbs : JSONObject.NULL );
            cPjson.put("created_at",DateUtil.toISOString(cp.date));
            cPjson.put("date",cp.date);
            cPjson.put("isSMB",cp.isSMB);
            cPjson.put("isMealBolus",cp.mealBolus);
        } catch (JSONException e) {}
        return cPjson;
    }

    private JSONObject tempBasaljson(TemporaryBasal tp) {
        JSONObject cPjson = new JSONObject();
        try {
            cPjson.put("_id", tp._id);
            cPjson.put("eventType", "Temp Basal");
            cPjson.put("date", tp.date);
            if (!tp.isEndingEvent())
                cPjson.put("duration", tp.getRealDuration());
            cPjson.put("absolute", tp.absoluteRate);
            cPjson.put("rate", tp.absoluteRate);
            cPjson.put("created_at", DateUtil.toISOString(tp.date));
            cPjson.put("enteredBy","openaps://AndroidAPS");
            cPjson.put("percent", tp.percentRate);
            //cPjson.put("durationInMinutes", tp.durationInMinutes);
            cPjson.put("isEnding", tp.isEndingEvent());
            cPjson.put("isFakeExtended", tp.isFakeExtended);
            cPjson.put("insulin",null);
            cPjson.put("carbs",null);
        } catch (JSONException e) {}
        return cPjson;
    }
}
