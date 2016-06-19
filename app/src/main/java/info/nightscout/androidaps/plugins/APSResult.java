package info.nightscout.androidaps.plugins;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 09.06.2016.
 */
public class APSResult implements Parcelable {
    public String reason;
    public double rate;
    public int duration;
    public boolean changeRequested = false;

    @Override
    public String toString() {
        Context context = MainApp.instance().getApplicationContext();

        DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
        DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

        if (changeRequested)
            return context.getString(R.string.rate) + " " + formatNumber2decimalplaces.format(rate) + " U/h\n" +
                    context.getString(R.string.duration) + " " + formatNumber0decimalplaces.format(duration) + " min\n" +
                    context.getString(R.string.reason) + " " + reason;
        else
            return MainApp.instance().getApplicationContext().getString(R.string.nochangerequested);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(reason);
        dest.writeDouble(rate);
        dest.writeInt(duration);
        dest.writeInt(changeRequested ? 1 : 0);
    }

    public final Parcelable.Creator<APSResult> CREATOR = new Parcelable.Creator<APSResult>() {
        public APSResult createFromParcel(Parcel in) {
            return new APSResult(in);
        }

        public APSResult[] newArray(int size) {
            return new APSResult[size];
        }
    };

    protected APSResult(Parcel in) {
        reason = in.readString();
        rate = in.readDouble();
        duration = in.readInt();
        changeRequested = in.readInt() == 1;
    }

    public APSResult() {}

    public APSResult clone() {
        APSResult newResult = new APSResult();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        return newResult;
    }

}
