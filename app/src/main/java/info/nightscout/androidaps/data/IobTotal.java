package info.nightscout.androidaps.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;

public class IobTotal {
    public Double iob;
    public Double activity;
    public Double bolussnooze;
    public Double basaliob;
    public Double netbasalinsulin;
    public Double hightempinsulin;

    public Double netInsulin = 0d; // for calculations from temp basals only
    public Double netRatio = 0d; // for calculations from temp basals only

    long time;

    public IobTotal(long time) {
        this.iob = 0d;
        this.activity = 0d;
        this.bolussnooze = 0d;
        this.basaliob = 0d;
        this.netbasalinsulin = 0d;
        this.hightempinsulin = 0d;
        this.time = time;
    }

    public IobTotal plus(IobTotal other) {
        iob += other.iob;
        activity += other.activity;
        bolussnooze += other.bolussnooze;
        basaliob += other.basaliob;
        netbasalinsulin += other.netbasalinsulin;
        hightempinsulin += other.hightempinsulin;
        netInsulin += other.netInsulin;
        netRatio += other.netRatio;
        return this;
    }

    public static IobTotal combine(IobTotal bolusIOB, IobTotal basalIob) {
        IobTotal result = new IobTotal(bolusIOB.time);
        result.iob = bolusIOB.iob + basalIob.basaliob;
        result.activity = bolusIOB.activity + basalIob.activity;
        result.bolussnooze = bolusIOB.bolussnooze;
        result.basaliob = basalIob.basaliob;
        result.netbasalinsulin = basalIob.netbasalinsulin;
        result.hightempinsulin = basalIob.hightempinsulin;
        return result;
    }

    public IobTotal round() {
        this.iob = Round.roundTo(this.iob, 0.001);
        this.activity = Round.roundTo(this.activity, 0.0001);
        this.bolussnooze = Round.roundTo(this.bolussnooze, 0.0001);
        this.basaliob = Round.roundTo(this.basaliob, 0.001);
        this.netbasalinsulin = Round.roundTo(this.netbasalinsulin, 0.001);
        this.hightempinsulin = Round.roundTo(this.hightempinsulin, 0.001);
        return this;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("basaliob", basaliob);
            json.put("activity", activity);
            json.put("time", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public JSONObject determineBasalJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("basaliob", basaliob);
            json.put("bolussnooze", bolussnooze);
            json.put("activity", activity);
            json.put("time", DateUtil.toISOString(new Date(time)));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static IobTotal calulateFromTreatmentsAndTemps() {
        ConfigBuilderPlugin.getActiveTreatments().updateTotalIOB();
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getLastCalculation().round();
        ConfigBuilderPlugin.getActiveTempBasals().updateTotalIOB();
        IobTotal basalIob = ConfigBuilderPlugin.getActiveTempBasals().getLastCalculation().round();
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        return  iobTotal;
    }

    public static IobTotal calulateFromTreatmentsAndTemps(long time) {
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getCalculationToTime(time).round();
        IobTotal basalIob = ConfigBuilderPlugin.getActiveTempBasals().getCalculationToTime(time).round();
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        return  iobTotal;
    }

    public static IobTotal[] calculateIobArrayInDia() {
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        // predict IOB out to DIA plus 30m
        long time = new Date().getTime();
        int len = (int) ((profile.getDia() *60 + 30) / 5);
        IobTotal[] array = new IobTotal[len];
        int pos = 0;
        for (int i = 0; i < len; i++){
            long t = time + i * 5 * 60000;
            IobTotal iob = calulateFromTreatmentsAndTemps(t);
            array[pos] = iob;
            pos++;
        }
        return array;
    }

    public static JSONArray convertToJSONArray(IobTotal[] iobArray) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < iobArray.length; i ++) {
            array.put(iobArray[i].determineBasalJson());
        }
        return array;
    }
}
