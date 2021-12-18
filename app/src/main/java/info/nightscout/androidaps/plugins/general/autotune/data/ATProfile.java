package info.nightscout.androidaps.plugins.general.autotune.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.Config;
import info.nightscout.androidaps.interfaces.Insulin;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.*;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.shared.SafeParse;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;

public class ATProfile {
    public Profile profile;
    public String profilename;
    private Profile.ProfileValue pv;
    public double basal[] = new double[24];
    public int basalUntuned[] = new int[24];
    public double ic;
    private Profile.ProfileValue[] srcic = null;
    public double isf;
    private Profile.ProfileValue[] srcisf = null;
    public double dia;
    public Profile.ValidityCheck isValid;
    public long from;
    @Inject ActivePlugin activePlugin;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject ProfileFunction profileFunction;
    @Inject DateUtil dateUtil;
    @Inject HardLimits hardLimits;
    @Inject Config config;
    @Inject RxBus rxBus;
    @Inject ResourceHelper rh;
    @Inject protected HasAndroidInjector injector;

//Todo add Autotune Injector
    public ATProfile(Profile profile) {
        injector.androidInjector().inject(this);

        this.profile=profile;
        if (profile != null )
            isValid = profile.isValid("Autotune", activePlugin.getActivePump(), config, rh, rxBus,
                    hardLimits, false);
        if (isValid.isValid()) {
            //initialize tuned value with current profile values
            basal = getBasal();
            if (srcic == null)
                srcic = profile.getIcsValues();
            if (srcisf == null)
                srcisf = profile.getIsfsMgdlValues();
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
        return profile.getIcsValues().length;
    }

    public int getIsfSize() {
        return profile.getIsfsMgdlValues().length;
    }

    public double getAvgISF() {
        return profile.getIsfsMgdlValues().length==1?profile.getIsfsMgdlValues()[0].getValue():
                Round.roundTo(averageProfileValue(profile.getIsfsMgdlValues()),0.01);
    }

    public double getAvgIC() {
        return profile.getIcsValues().length==1?profile.getIcsValues()[0].getValue():
                Round.roundTo(averageProfileValue(profile.getIcsValues()),0.01);
    }

    public double[] getBasal() {
        double basal[] = new double[24];
        for (int i = 0; i < 24; i++) {
            basal[i] = getBasal(i);
        }
        return basal;
    }

    public double getBasal(Integer hour){
        if(!isValid.isValid())
            return 0d;
        int secondfrommidnight = hour * 60 * 60;
        return profile.getBasalTimeFromMidnight(secondfrommidnight);
    }

    public void setBasal(long time, double value) {
        int hour = (int) ((time - MidnightTime.INSTANCE.calc(time))/60/60/1000);
        basal[hour] = value;
    }

    public static double averageProfileValue(Profile.ProfileValue[] pf) {
        double avgValue = 0;
        int secondPerDay=24*60*60;
        if (pf == null)
            return avgValue;
        for(int i = 0; i< pf.length;i++) {
            avgValue+=pf[i].getValue()*((i==pf.length -1 ? secondPerDay : pf[i+1].getTimeAsSeconds()) -pf[i].getTimeAsSeconds());
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
        Insulin insulinInterface = activePlugin.getActiveInsulin();

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
            double isfvalue = Profile.Companion.fromMgdlToUnits(profile.getIsfMgdl(),profile.getUnits());
            json.put("isfProfile",new JSONObject().put("sensitivities",new JSONArray().put(new JSONObject().put("i",0).put("start","00:00:00").put("sensitivity",isfvalue).put("offset",0).put("x",0).put("endoffset",1440))));
            // json.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("carb_ratio", 0d))));
            json.put("carb_ratio", profile.getIc());
            json.put("autosens_max", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_max, "1.2")));
            json.put("autosens_min", SafeParse.stringToDouble(sp.getString(R.string.key_openapsama_autosens_min, "0.7")));
            json.put("units",profileFunction.getUnits());
            json.put("timezone", TimeZone.getDefault().getID());
            if (insulinInterface.getId() == Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING)
                json.put("curve","ultra-rapid");
            else if (insulinInterface.getId() == Insulin.InsulinType.OREF_RAPID_ACTING)
                json.put("curve","rapid-acting");
            else if (insulinInterface.getId() == Insulin.InsulinType.OREF_LYUMJEV) {
                json.put("curve", "ultra-rapid");
                json.put("useCustomPeakTime", true);
                json.put("insulinPeakTime", 45);
            } else if (insulinInterface.getId() == Insulin.InsulinType.OREF_FREE_PEAK) {
                int peaktime = sp.getInt(resourceHelper.gs(R.string.key_insulin_oref_peak),75);
                json.put("curve", peaktime > 30 ? "rapid-acting" : "ultra-rapid");
                json.put("useCustomPeakTime",true);
                json.put("insulinPeakTime",peaktime);
            }
            jsonString= json.toString(2).replace("\\/", "/");

        } catch (JSONException e) {}

        return jsonString;
    }

    //json profile
    public JSONObject getData() { return getData(false); }

    //json profile
    public JSONObject getData(Boolean src) {
        JSONObject json = profile.toPureNsJson(dateUtil);
        try{
            json.put("dia",dia);
            if (src) {
                json.put("sens", getArray(srcisf));
                json.put("carbratio", getArray(srcic));
            } else {
                json.put("sens", new JSONArray().put(new JSONObject().put("time", "00:00").put(
                        "timeAsSeconds", 0).put("value", Profile.Companion.fromMgdlToUnits(isf,
                        profile.getUnits()))));
                json.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", ic)));
            }
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

    private JSONArray getArray(Profile.ProfileValue[] pf) {
        JSONArray json = new JSONArray();
        if (pf == null)
            return json;
        try{
            for(int i = 0; i< pf.length;i++) {
                int h = pf[i].getTimeAsSeconds()/60/60;
                String time;
                time = (h<10 ? "0"+ h : h)  + ":00";
                json.put(new JSONObject().put("time", time).put("timeAsSeconds",
                        pf[i].getTimeAsSeconds()).put("value",
                        Profile.Companion.fromMgdlToUnits(pf[i].getValue(), profile.getUnits())));
            }
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
            profileStore = new ProfileStore(injector, json, dateUtil);
        } catch (JSONException e) {}
        return profileStore;
    }

}
