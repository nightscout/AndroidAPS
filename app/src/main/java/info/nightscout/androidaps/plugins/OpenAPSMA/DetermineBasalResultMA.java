package info.nightscout.androidaps.plugins.OpenAPSMA;

import android.os.Parcel;
import android.os.Parcelable;

import com.eclipsesource.v8.V8Object;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.Loop.APSResult;

public class DetermineBasalResultMA extends APSResult {

    public JSONObject json = new JSONObject();
    public double eventualBG;
    public double snoozeBG;
    public String mealAssist;
    public IobTotal iob;

    public DetermineBasalResultMA(V8Object result, JSONObject j) {
        json = j;
        if (result.contains("error")) {
            reason = result.getString("error");
            changeRequested = false;
            rate = -1;
            duration = -1;
            mealAssist = "";
        } else {
            reason = result.getString("reason");
            eventualBG = result.getDouble("eventualBG");
            snoozeBG = result.getDouble("snoozeBG");
            if (result.contains("rate")) {
                rate = result.getDouble("rate");
                if (rate < 0d) rate = 0d;
                changeRequested = true;
            } else {
                rate = -1;
                changeRequested = false;
            }
            if (result.contains("duration")) {
                duration = result.getInteger("duration");
                changeRequested = changeRequested;
            } else {
                duration = -1;
                changeRequested = false;
            }
            if (result.contains("mealAssist")) {
                mealAssist = result.getString("mealAssist");
            } else mealAssist = "";
        }
        result.release();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(json.toString());
        dest.writeDouble(eventualBG);
        dest.writeDouble(snoozeBG);
        dest.writeString(mealAssist);
    }

    public final Parcelable.Creator<DetermineBasalResultMA> CREATOR = new Parcelable.Creator<DetermineBasalResultMA>() {
        public DetermineBasalResultMA createFromParcel(Parcel in) {
            return new DetermineBasalResultMA(in);
        }

        public DetermineBasalResultMA[] newArray(int size) {
            return new DetermineBasalResultMA[size];
        }
    };

    private DetermineBasalResultMA(Parcel in) {
        super(in);
        try {
            json = new JSONObject(in.readString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        eventualBG = in.readDouble();
        snoozeBG = in.readDouble();
        mealAssist = in.readString();
    }

    public DetermineBasalResultMA() {
    }

    @Override
    public DetermineBasalResultMA clone() {
        DetermineBasalResultMA newResult = new DetermineBasalResultMA();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;

        try {
            newResult.json = new JSONObject(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        newResult.eventualBG = eventualBG;
        newResult.snoozeBG = snoozeBG;
        newResult.mealAssist = new String(mealAssist);
        return newResult;
    }

    @Override
    public JSONObject json() {
        try {
            JSONObject ret = new JSONObject(this.json.toString());
            return ret;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

}
