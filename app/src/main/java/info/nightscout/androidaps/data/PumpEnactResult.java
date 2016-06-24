package info.nightscout.androidaps.data;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.Round;

public class PumpEnactResult extends Object implements Parcelable {
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

    public String log() {
        return "Success: " + success + " Enacted: " + enacted + " Comment: " + comment + " Duration: " + duration + " Absolute: " + absolute + " Percent: " + percent + " IsPercent: " + isPercent;
    }

    public String toString() {
        String ret = "Success: " + success;
        if (enacted) {
            if (isTempCancel) {
                ret += "\nEnacted: " + enacted + "\nComment: " + comment + "\n" +
                        "Temp cancel";
            } else if (isPercent) {
                ret += "\nEnacted: " + enacted + "\nComment: " + comment + "\nDuration: " + duration + " min\nPercent: " + percent + "%";
            } else {
                ret += "\nEnacted: " + enacted + "\nComment: " + comment + "\nDuration: " + duration + " min\nAbsolute: " + absolute + " U/h";
            }
        } else {
            ret += "\nComment: " + comment;
        }
        return ret;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(success ? 1 : 0);
        dest.writeInt(enacted ? 1 : 0);
        dest.writeInt(isPercent ? 1 : 0);
        dest.writeString(comment);
        dest.writeInt(duration);
        dest.writeDouble(absolute);
        dest.writeInt(percent);
    }

    public final Parcelable.Creator<PumpEnactResult> CREATOR = new Parcelable.Creator<PumpEnactResult>() {
        public PumpEnactResult createFromParcel(Parcel in) {
            return new PumpEnactResult(in);
        }

        public PumpEnactResult[] newArray(int size) {
            return new PumpEnactResult[size];
        }
    };

    protected PumpEnactResult(Parcel in) {
        success = in.readInt() == 1 ? true : false;
        enacted = in.readInt() == 1 ? true : false;
        isPercent = in.readInt() == 1 ? true : false;
        duration = in.readInt();
        comment = in.readString();
        absolute = in.readDouble();
        percent = in.readInt();

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
