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

import info.nightscout.client.broadcasts.Intents;
import info.nightscout.androidaps.MainApp;
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
/*
    public Iob iobCalc(Date time, Double dia) {
        Double diaratio = 3.0 / dia;
        Double peak = 75d;
        Double end = 180d;
        //var sens = profile_data.sens;

        Iob results = new Iob();

        if (insulin != 0) {
            long bolusTime = created_at.getTime();
            Double minAgo = diaratio * (time.getTime() - bolusTime) / 1000 / 60;
            Double iobContrib = 0d;
            Double activityContrib = 0d;

            if (minAgo < peak) {
                Double x = (minAgo/5 + 1);
                iobContrib = insulin * (1 - 0.001852 * x * x + 0.001852 * x);
                //activityContrib=sens*treatment.insulin*(2/dia/60/peak)*minAgo;
                activityContrib = insulin * (2 / dia / 60 / peak) * minAgo;
            } else if (minAgo < end) {
                Double y = (minAgo-peak)/5;
                iobContrib = insulin * (0.001323 * y * y - .054233 * y + .55556);
                //activityContrib=sens*treatment.insulin*(2/dia/60-(minAgo-peak)*2/dia/60/(60*dia-peak));
                activityContrib = insulin * (2 / dia / 60 - (minAgo - peak) * 2 / dia / 60 / (60 * dia - peak));
            }

            results.iobContrib = iobContrib;
            results.activityContrib = activityContrib;
        }

        return results;
    }

    public Iob calcIobOpenAPS() {
        IobCalc calc = new IobCalc(created_at,insulin,new Date());
        calc.setBolusDiaTimesTwo();
        Iob iob = calc.invoke();

        return iob;
    }
    public Iob calcIob() {
        IobCalc calc = new IobCalc(created_at,insulin,new Date());
        Iob iob = calc.invoke();

        return iob;
    }
*/

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
        } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
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
        } else log.debug("DBUPDATE dbUpdate " + q.size() + " receivers " + _id + " " + data.toString());
    }


}
