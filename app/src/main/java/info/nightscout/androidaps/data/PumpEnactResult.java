package info.nightscout.androidaps.data;

import android.text.Html;
import android.text.Spanned;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;

public class PumpEnactResult extends Object {
    public boolean success = false;    // request was processed successfully (but possible no change was needed)
    public boolean enacted = false;    // request was processed successfully and change has been made
    public String comment = "";

    // Result of basal change
    public Integer duration = -1;      // duration set [minutes]
    public Double absolute = -1d;      // absolute rate [U/h] , isPercent = false
    public Integer percent = -1;       // percent of current basal [%] (100% = current basal), isPercent = true
    public boolean isPercent = false;  // if true percent is used, otherwise absolute
    public boolean isTempCancel = false; // if true we are caceling temp basal
    // Result of treatment delivery
    public Double bolusDelivered = 0d; // real value of delivered insulin
    public Integer carbsDelivered = 0; // real value of delivered carbs

    public boolean queued = false;

     public String log() {
        return "Success: " + success + " Enacted: " + enacted + " Comment: " + comment + " Duration: " + duration + " Absolute: " + absolute + " Percent: " + percent + " IsPercent: " + isPercent + " Queued: " + queued;
    }

    public String toString() {
        String ret = MainApp.sResources.getString(R.string.success) + ": " + success;
        if (enacted) {
            if (isTempCancel) {
                ret += "\n" + MainApp.sResources.getString(R.string.enacted) + ": " + enacted;
                ret += "\n" + MainApp.sResources.getString(R.string.comment) + ": " + comment + "\n" +
                        MainApp.sResources.getString(R.string.canceltemp);
            } else if (isPercent) {
                ret += "\n" + MainApp.sResources.getString(R.string.enacted) + ": " + enacted;
                ret += "\n" + MainApp.sResources.getString(R.string.comment) + ": " + comment;
                ret += "\n" + MainApp.sResources.getString(R.string.duration) + ": " + duration + " min";
                ret += "\n" + MainApp.sResources.getString(R.string.percent) + ": " + percent + "%";
            } else {
                ret += "\n" + MainApp.sResources.getString(R.string.enacted) + ": " + enacted;
                ret += "\n" + MainApp.sResources.getString(R.string.comment) + ": " + comment;
                ret += "\n" + MainApp.sResources.getString(R.string.duration) + ": " + duration + " min";
                ret += "\n" + MainApp.sResources.getString(R.string.absolute) + ": " + absolute + " U/h";
            }
        } else {
            ret += "\n" + MainApp.sResources.getString(R.string.comment) + ": " + comment;
        }
        return ret;
    }

    public Spanned toSpanned() {
        String ret = MainApp.sResources.getString(R.string.success) + ": " + success;
        if (queued) {
            ret = MainApp.sResources.getString(R.string.waitingforpumpresult);
        } else if (enacted) {
            if (isTempCancel) {
                ret += "<br><b>" + MainApp.sResources.getString(R.string.enacted) + "</b>: " + enacted;
                ret += "<br><b>" + MainApp.sResources.getString(R.string.comment) + "</b>: " + comment +
                        "<br>" + MainApp.sResources.getString(R.string.canceltemp);
            } else if (isPercent) {
                ret += "<br><b>" + MainApp.sResources.getString(R.string.enacted) + "</b>: " + enacted;
                ret += "<br><b>" + MainApp.sResources.getString(R.string.comment) + "</b>: " + comment;
                ret += "<br><b>" + MainApp.sResources.getString(R.string.duration) + "</b>: " + duration + " min";
                ret += "<br><b>" + MainApp.sResources.getString(R.string.percent) + "</b>: " + percent + "%";
            } else {
                ret += "<br><b>" + MainApp.sResources.getString(R.string.enacted) + "</b>: " + enacted;
                ret += "<br><b>" + MainApp.sResources.getString(R.string.comment) + "</b>: " + comment;
                ret += "<br><b>" + MainApp.sResources.getString(R.string.duration) + "</b>: " + duration + " min";
                ret += "<br><b>" + MainApp.sResources.getString(R.string.absolute) + "</b>: " + DecimalFormatter.to2Decimal(absolute) + " U/h";
            }
        } else {
            ret += "<br><b>" + MainApp.sResources.getString(R.string.comment) + "</b>: " + comment;
        }
        return Html.fromHtml(ret);
    }

    public PumpEnactResult() {
    }

    public JSONObject json() {
        JSONObject result = new JSONObject();
        try {
            if (isTempCancel) {
                result.put("rate", 0);
                result.put("duration", 0);
            } else if (isPercent) {
                // Nightscout is expecting absolute value
                Double abs = Round.roundTo(MainApp.getConfigBuilder().getActiveProfile().getProfile().getBasal(NSProfile.secondsFromMidnight()) * percent / 100, 0.01);
                result.put("rate", abs);
                result.put("duration", duration);
            } else {
                result.put("rate", absolute);
                result.put("duration", duration);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }
}
