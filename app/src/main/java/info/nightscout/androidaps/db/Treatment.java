package info.nightscout.androidaps.db;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.MainApp;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

@DatabaseTable(tableName = "Treatments")
public class Treatment {
    private static Logger log = LoggerFactory.getLogger(Treatment.class);

    public long getTimeIndex() {
        return (long) Math.ceil(created_at.getTime() / 60000d);
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;

    @DatabaseField
    public String _id;

    @DatabaseField
    public Date created_at;

    @DatabaseField
    public Double insulin;

    @DatabaseField
    public Double carbs;

    public void copyFrom(Treatment t) {
        this._id = t._id;
        this.created_at = t.created_at;
        this.insulin = t.insulin;
        this.carbs = t.carbs;
    }

    public Iob iobCalc(Date time, Double dia) {
        Iob result = new Iob();

        Double scaleFactor = 3.0 / dia;
        Double peak = 75d;
        Double end = 180d;

        if (this.insulin != 0d) {
            Long bolusTime = this.created_at.getTime();
            Double minAgo = scaleFactor * (time.getTime() - bolusTime) / 1000d / 60d;

            if (minAgo < peak) {
                Double x1 = minAgo / 5d + 1;
                result.iobContrib = this.insulin * (1 - 0.001852 * x1 * x1 + 0.001852 * x1);
                // units: BG (mg/dL)  = (BG/U) *    U insulin     * scalar
                result.activityContrib = this.insulin * (2 / dia / 60 / peak) * minAgo;

            } else if (minAgo < end) {
                Double x2 = (minAgo - 75) / 5;
                result.iobContrib = this.insulin * (0.001323 * x2 * x2 - 0.054233 * x2 + 0.55556);
                result.activityContrib = this.insulin * (2 / dia / 60 - (minAgo - peak) * 2 / dia / 60 / (60 * dia - peak));
            }
        }
        return result;
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - created_at.getTime();
    }

    public String log() {
        return "Treatment{" +
                "timeIndex: " + timeIndex +
                ", _id: " + _id +
                ", insulin: " + insulin +
                ", carbs: " + carbs +
                ", created_at: " +
                "}";
    }

    public void sendToNSClient() {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Meal Bolus");
            if (insulin != 0d) data.put("insulin", insulin);
            if (carbs != 0d) data.put("carbs", carbs.intValue());
            data.put("created_at", DateUtil.toISOString(created_at));
            data.put("timeIndex", timeIndex);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            log.error("DBADD No receivers");
        } else  if (Config.logNSUpload)
            log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
    }

    public void updateToNSClient() {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbUpdate");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Meal Bolus");
            data.put("insulin", insulin);
            data.put("carbs", carbs.intValue());
            data.put("created_at", DateUtil.toISOString(created_at));
            data.put("timeIndex", timeIndex);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        bundle.putString("_id", _id);
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            log.error("DBUPDATE No receivers");
        } else  if (Config.logNSUpload)
            log.debug("DBUPDATE dbUpdate " + q.size() + " receivers " + _id + " " + data.toString());
    }
}
