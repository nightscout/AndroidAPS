package info.nightscout.androidaps.data;

import android.os.Parcel;
import android.os.Parcelable;

public class Result extends Object implements Parcelable{
    public boolean success = false;
    public boolean enacted = false;
    public String comment = "";
    public Integer duration = -1;
    public Double absolute = -1d;
    public Integer percent = -1;

    public Double bolusDelivered = 0d;

    public String log() {
        return "Success: " + success + " Enacted: " + enacted + " Comment: " + comment + " Duration: " + duration + " Absolute: " + absolute + " Percent: " + percent;
    }

    public String toString() {
        return "Success: " + success + "\nEnacted: " + enacted + "\nComment: " + comment + "\nDuration: " + duration + "\nAbsolute: " + absolute;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(success ? 1 : 0);
        dest.writeInt(enacted ? 1 : 0);
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
        duration = in.readInt();
        comment = in.readString();
        absolute = in.readDouble();
        percent = in.readInt();

    }

    public Result() {}

}
