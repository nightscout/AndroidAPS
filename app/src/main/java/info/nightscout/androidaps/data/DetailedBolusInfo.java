package info.nightscout.androidaps.data;

import android.content.Context;

import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;

/**
 * Created by mike on 29.05.2017.
 */

public class DetailedBolusInfo {
    public long date = System.currentTimeMillis();
    public String eventType = CareportalEvent.MEALBOLUS;
    public double insulin = 0;
    public double carbs = 0;
    public int source = Source.NONE;
    public boolean isValid = true;
    public double glucose = 0;             // Bg value in current units
    public String glucoseType = "";        // NS values: Manual, Finger, Sensor
    public int carbTime = 0;               // time shift of carbs in minutes
    public JSONObject boluscalc = null;    // additional bolus wizard info
    public Context context = null;         // context for progress dialog
    public long pumpId = 0;                // id of record if comming from pump history (not a newly created treatment)
    public boolean isSMB = false;          // is a Super-MicroBolus

    public DetailedBolusInfo copy() {
        DetailedBolusInfo copy = new DetailedBolusInfo();
        copy.date = this.date;
        copy.eventType = this.eventType;
        copy.insulin = this.insulin;
        copy.carbs = this.carbs;
        copy.source = this.source;
        copy.isValid = this.isValid;
        copy.glucose = this.glucose;
        copy.glucoseType = this.glucoseType;
        copy.carbTime = this.carbTime;
        copy.boluscalc = this.boluscalc;
        copy.context = this.context;
        copy.pumpId = this.pumpId;
        copy.isSMB = this.isSMB;
        return copy;
    }

    @Override
    public String toString() {
        return new Date(date).toLocaleString() +
                " insulin: " + insulin +
                " carbs: " + carbs +
                " isValid: " + isValid +
                " carbTime: " + carbTime +
                " isSMB: " + isSMB;
    }
}
