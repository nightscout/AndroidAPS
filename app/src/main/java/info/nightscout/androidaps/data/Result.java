package info.nightscout.androidaps.data;

import android.os.Parcel;
import android.os.Parcelable;

public class Result extends Object implements Parcelable{
    public boolean success = false;    // request was processed successfully (but possible no change was needed)
    public boolean enacted = false;    // request was processed successfully and change has been made
    public String comment = "";

    // Result of basal change
    public Integer duration = -1;      // duration set [minutes]
    public Double absolute = -1d;      // absolute rate [U/h] , isPercent = false
    public Integer percent = -1;       // percent of current basal [%] (100% = current basal), isPercent = true
    public boolean isPercent = false;  // if true percent is used, otherwise absolute
    // Result of bolus delivery
    public Double bolusDelivered = 0d; // real value of delivered insulin

    public String log() {
        return "Success: " + success + " Enacted: " + enacted + " Comment: " + comment + " Duration: " + duration + " Absolute: " + absolute + " Percent: " + percent + " IsPercent: " + isPercent;
    }

    public String toString() {
        String ret = "Success: " + success;
        if (enacted) {
            if (isPercent) {
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

    public final Parcelable.Creator<Result> CREATOR = new Parcelable.Creator<Result>() {
        public Result createFromParcel(Parcel in) {
            return new Result(in);
        }

        public Result[] newArray(int size) {
            return new Result[size];
        }
    };

    protected Result(Parcel in) {
        success = in.readInt() == 1 ? true : false;
        enacted = in.readInt() == 1 ? true : false;
        isPercent = in.readInt() == 1 ? true : false;
        duration = in.readInt();
        comment = in.readString();
        absolute = in.readDouble();
        percent = in.readInt();

    }

    public Result() {}

}
