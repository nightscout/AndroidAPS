package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.StaticInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.MidnightTime;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class ATProfile {
    public Profile profile;
    public String profilename;
    private Profile.ProfileValue pv;
    public double basal[] = new double[24];
    public int basalUntuned[] = new int[24];
    public double ic;
    public double isf;
    public double dia;
    public boolean isValid = false;
    public long from;
    @Inject ActivePluginProvider activePlugin;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject ProfileFunction profileFunction;
    @Inject DateUtil dateUtil;
    private final HasAndroidInjector injector;

//Todo add Autotune Injector
    public ATProfile(Profile profile) {
        injector = StaticInjector.Companion.getInstance();
        injector.androidInjector().inject(this);

        this.profile=profile;
        if (profile != null )
            isValid = profile.isValid("Autotune");
        if (isValid) {
            //initialize tuned value with current profile values
            basal = getBasal();
            ic = getAvgIC();
            isf = getAvgISF();
            dia = profile.getDia();
        }
    }

    public Profile getProfile() {
        return profile;
    }

    public void updateProfile() {
        profile = new Profile(injector, getData());
    }

    public Profile getTunedProfile() {
        return new Profile(injector, getData());
    }

    public int getIcSize() {
        return profile.getIcs().length;
    }

    public int getIsfSize() {
        return profile.getIsfsMgdl().length;
    }

    public double getAvgISF() {
        return profile.getIsfsMgdl().length==1?profile.getIsfsMgdl()[0].value:Round.roundTo(averageProfileValue(profile.getIsfsMgdl()),0.01);
    }

    public double getAvgIC() {
        return profile.getIcs().length==1?profile.getIcs()[0].value: Round.roundTo(averageProfileValue(profile.getIcs()),0.01);
    }

    public double[] getBasal() {
        double basal[] = new double[24];
        for (int i = 0; i < 24; i++) {
            basal[i] = getBasal(i);
        }
        return basal;
    }

    public double getBasal(Integer hour){
        if(!isValid)
            return 0d;
        int secondfrommidnight = hour * 60 * 60;
        return profile.getBasalTimeFromMidnight(secondfrommidnight);
    }

    public void setBasal(long time, double value) {
        int hour = (int) ((time - MidnightTime.calc(time))/60/60/1000);
        basal[hour] = value;
    }

    public static double averageProfileValue(Profile.ProfileValue[] pf) {
        double avgValue = 0;
        int secondPerDay=24*60*60;
        if (pf == null)
            return avgValue;
        for(int i = 0; i< pf.length;i++) {
            avgValue+=pf[i].value*((i==pf.length -1 ? secondPerDay : pf[i+1].timeAsSeconds) -pf[i].timeAsSeconds);
        }
        avgValue/=secondPerDay;
        return avgValue;
    }

    //Export json string with oref0 format used for autotune
    public String profiletoOrefJSON()  {
        // Create a json profile with oref0 format
        // Include min_5m_carbimpact, insulin type, single value for carb_ratio and isf
        String jsonString = "";
        JSONObject json = new JSONObject();
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
            double isfvalue = Profile.fromMgdlToUnits(profile.getIsfMgdl(),profile.getUnits());
            json.put("isfProfile",new JSONObject().put("sensitivities",new JSONArray().put(new JSONObject().put("i",0).put("start","00:00:00").put("sensitivity",isfvalue).put("offset",0).put("x",0).put("endoffset",1440))));
            // json.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("carb_ratio", 0d))));
            json.put("carb_ratio", profile.getIc());
            json.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")));
            json.put("autosens_min", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_min, "0.7")));
            json.put("units",profileFunction.getUnits());
            json.put("timezone", TimeZone.getDefault().getID());
            if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
                json.put("curve","ultra-rapid");
            else if (insulinInterface.getId() == InsulinInterface.OREF_RAPID_ACTING)
                json.put("curve","rapid-acting");
            else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK) {
                int peaktime = sp.getInt(resourceHelper.gs(R.string.key_insulin_oref_peak),75);
                json.put("curve", peaktime > 50 ? "rapid-acting" : "ultra-rapid");
                json.put("useCustomPeakTime",true);
                json.put("insulinPeakTime",peaktime);
            }
            jsonString= json.toString(2).replace("\\/", "/");

        } catch (JSONException e) {}

        return jsonString;
    }

    //json profile
    public JSONObject getData() {
        JSONObject json = profile.getData();
        try{
            json.put("dia",dia);
            json.put("sens",new JSONArray().put(new JSONObject().put("time","00:00").put("timeAsSeconds",0).put("value",Profile.fromMgdlToUnits(isf, profile.getUnits()))));
            json.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", ic)));
            JSONArray basals = new JSONArray();
            for (int h = 0; h < 24; h++) {
                int secondfrommidnight = h * 60 * 60;
                String time;
                time = (h<10 ? "0"+ h : h)  + ":00";
                //basals.put(new JSONObject().put("start", time).put("minutes", h * basalIncrement).put("rate", getProfileBasal(h)));
                basals.put(new JSONObject().put("time", time).put("timeAsSeconds", secondfrommidnight).put("value", basal[h]));
            };
            json.put("basal", basals);

        } catch (JSONException e) {}

        return json;
    }

    public ProfileStore getProfileStore() {
        ProfileStore profileStore=null;
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();

        try {
            store.put(resourceHelper.gs(R.string.autotune_tunedprofile_name), getData());
            json.put("defaultProfile", resourceHelper.gs(R.string.autotune_tunedprofile_name));
            json.put("store", store);
            json.put("startDate", dateUtil.toISOAsUTC(dateUtil.now()));
            profileStore = new ProfileStore(injector, json);
        } catch (JSONException e) {}
        return profileStore;
    }

}
