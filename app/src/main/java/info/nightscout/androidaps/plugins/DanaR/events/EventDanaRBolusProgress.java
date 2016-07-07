package info.nightscout.androidaps.plugins.DanaR.events;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.client.data.DbLogger;

public class EventDanaRBolusProgress {
    private static Logger log = LoggerFactory.getLogger(EventDanaRBolusProgress.class);
    public String sStatus = "";
    public Treatment t = null;
    private static EventDanaRBolusProgress eventDanaRBolusProgress = null;

     public EventDanaRBolusProgress() {
    }

    public static EventDanaRBolusProgress getInstance() {
        if(eventDanaRBolusProgress == null) {
            eventDanaRBolusProgress = new EventDanaRBolusProgress();
        }
        return eventDanaRBolusProgress;
    }

    public void sendToNSClient() {
        if (t == null || t._id == null || t._id.equals("")) return;
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbUpdate");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("status", sStatus);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        bundle.putString("_id", t._id);
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString(), EventDanaRBolusProgress.class);
    }

}
