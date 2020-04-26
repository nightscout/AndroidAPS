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
    public long lastKnownBolusTime;
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
    public long deliverAt = 0;             // SMB should be delivered within 1 min from this time
    public String notes = null;

    public DetailedBolusInfo copy() {
        DetailedBolusInfo n = new DetailedBolusInfo();
        n.date = date;
        n.eventType = eventType;
        n.insulin = insulin;
        n.carbs = carbs;
        n.source = source;
        n.isValid = isValid;
        n.glucose = glucose;
        n.glucoseType = glucoseType;
        n.carbTime = carbTime;
        n.boluscalc = boluscalc;
        n.context = context;
        n.pumpId = pumpId;
        n.isSMB = isSMB;
        n.deliverAt = deliverAt;
        n.notes = notes;
        return n;
    }

    @Override
    public String toString() {
        return new Date(date).toLocaleString() +
                " date: " + date +
                " insulin: " + insulin +
                " carbs: " + carbs +
                " isValid: " + isValid +
                " carbTime: " + carbTime +
                " isSMB: " + isSMB +
                " deliverAt: " + new Date(deliverAt).toLocaleString();
    }
}
