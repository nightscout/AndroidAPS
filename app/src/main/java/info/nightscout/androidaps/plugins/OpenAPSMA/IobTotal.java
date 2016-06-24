package info.nightscout.androidaps.plugins.OpenAPSMA;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

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

    public IobTotal() {
        this.iob = 0d;
        this.activity = 0d;
        this.bolussnooze = 0d;
        this.basaliob = 0d;
        this.netbasalinsulin = 0d;
        this.hightempinsulin = 0d;
    }

    public IobTotal plus(IobTotal other) {
        iob += other.iob;
        activity = other.activity;
        bolussnooze = other.bolussnooze;
        basaliob = other.iob;
        netbasalinsulin = other.netbasalinsulin;
        hightempinsulin = other.hightempinsulin;
        netInsulin += other.netInsulin;
        netRatio += other.netRatio;
        return this;
    }

    public static IobTotal combine(IobTotal bolusIOB, IobTotal basalIob) {
        IobTotal result = new IobTotal();
        result.iob = bolusIOB.iob;
        result.activity = bolusIOB.activity;
        result.bolussnooze = bolusIOB.bolussnooze;
        result.basaliob = basalIob.iob;
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
            json.put("bolussnooze", bolussnooze);
            json.put("basaliob", iob);
            json.put("activity", activity);
            json.put("hightempinsulin", hightempinsulin);
            json.put("netbasalinsulin", netbasalinsulin);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}
