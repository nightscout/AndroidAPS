package info.nightscout.androidaps.plugins;

import android.content.Context;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 09.06.2016.
 */
public class APSResult {
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
        return context.getString(R.string.rate) + " " +  formatNumber2decimalplaces.format(rate) + " U/h\n" +
               context.getString(R.string.duration)  + " " + formatNumber0decimalplaces.format(duration) + " min\n" +
               context.getString(R.string.reason)  + " " + reason;
        else
            return MainApp.instance().getApplicationContext().getString(R.string.nochangerequested);
    }
}
