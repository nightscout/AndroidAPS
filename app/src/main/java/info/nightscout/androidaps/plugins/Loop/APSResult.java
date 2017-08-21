package info.nightscout.androidaps.plugins.Loop;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.text.Spanned;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 09.06.2016.
 */
public class APSResult {
    public String reason;
    public double rate;
    public int duration;
    public boolean changeRequested = false;
    public IobTotal iob;
    public double smb = 0d; // super micro bolus in units
    public long deliverAt = 0;

    @Override
    public String toString() {
        final ConfigBuilderPlugin configBuilder = MainApp.getConfigBuilder();
        if (changeRequested) {
            if (rate == 0 && duration == 0)
                return MainApp.sResources.getString(R.string.canceltemp);
            else
                return MainApp.sResources.getString(R.string.rate) + ": " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate/configBuilder.getBaseBasalRate() *100) + "%)\n" +
                        MainApp.sResources.getString(R.string.duration) + ": " + DecimalFormatter.to0Decimal(duration) + " min\n" +
                        (smb != 0 ? ("SMB" + ": " + DecimalFormatter.to2Decimal(smb) + " U") : "") +
                        MainApp.sResources.getString(R.string.reason) + ": " + reason;
        } else
            return MainApp.sResources.getString(R.string.nochangerequested);
    }

    public Spanned toSpanned() {
        final ConfigBuilderPlugin configBuilder = MainApp.getConfigBuilder();
        if (changeRequested) {
            String ret = "";
            if (rate == 0 && duration == 0) ret = MainApp.sResources.getString(R.string.canceltemp);
            else
                ret = "<b>" + MainApp.sResources.getString(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                        "(" + DecimalFormatter.to2Decimal(rate/configBuilder.getBaseBasalRate() *100) + "%) <br>" +
                        "<b>" + MainApp.sResources.getString(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration) + " min<br>" +
                        (smb != 0 ? ("<b>" + "SMB" + "</b>: " + DecimalFormatter.to2Decimal(smb) + " U") : "") +
                        "<b>" + MainApp.sResources.getString(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;");
            return Html.fromHtml(ret);
        } else
            return Html.fromHtml(MainApp.sResources.getString(R.string.nochangerequested));
    }

    public APSResult() {
    }

    public APSResult clone() {
        APSResult newResult = new APSResult();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        newResult.iob = iob;
        return newResult;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            if (changeRequested) {
                json.put("rate", rate);
                json.put("duration", duration);
                json.put("reason", reason);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}
