package info.nightscout.androidaps.plugins.TuneProfile.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Date;
import java.util.TimeZone;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;

public class Opts {
    public static List<Treatment> treatments;
    public static Profile profile;
    public static Profile pumpprofile;
    public List<BgReading> glucose;
    public List<TemporaryBasal> pumpHistory;
    public List<ExtendedBolus> pumpExtBolusHistory;
    public List<TemporaryBasal> pumpTempBasalHistory;
    public long start;
    public long end;
    public boolean categorize_uam_as_basal;
    public boolean tune_insulin_curve;

    // on each loop glucose containts only one day BG Value
    public JSONArray glucosetoJSON()  {
        JSONArray glucoseJson = new JSONArray();
        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));
        ConfigBuilderPlugin.getPlugin().getActiveBgSource();
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

    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray extBolustoJSON()  {
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

    public JSONArray pumpHistorytoJSON()  {
        return tempBasaltoJSON(pumpHistory);
    }

    public JSONArray pumpTempBasalHistorytoJSON()  {
        return tempBasaltoJSON(pumpTempBasalHistory);
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

    private JSONArray tempBasaltoJSON(List<TemporaryBasal> listTempBasals)  {
        JSONArray json = new JSONArray();
        for (TemporaryBasal tp:listTempBasals ) {
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



    public JSONObject profiletoOrefJSON()  {
        // Create a json profile with oref0 format
        // Include min_5m_carbimpact, insulin type, single value for carb_ratio and isf
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject convertedProfile = new JSONObject();
        int basalIncrement = 60 ;
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();

        try {
            json.put("min_5m_carbimpact",SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
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
            json.put("autosens_max", SafeParse.stringToDouble(SP.getString(R.string.key_openapsama_autosens_max, "1.2")));
            json.put("autosens_min", SafeParse.stringToDouble(SP.getString(R.string.key_openapsama_autosens_min, "0.7")));
            if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
                json.put("curve","ultra-rapid");
            else if (insulinInterface.getId() == InsulinInterface.OREF_RAPID_ACTING)
                json.put("curve","rapid-acting");
            else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK) {
                json.put("curve", "bilinear");
                json.put("insulinpeaktime",SP.getInt(MainApp.gs(R.string.key_insulin_oref_peak),75));
            }

        } catch (JSONException e) {}

        return json;
    }

    public synchronized Double getProfileBasal(Integer hour){
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }

    public synchronized Double getPumpProfileBasal(Integer hour){
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }
}
