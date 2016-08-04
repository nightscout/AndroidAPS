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
    public String status = "";
    public Treatment t = null;
    public int percent = 0;
    private static EventDanaRBolusProgress eventDanaRBolusProgress = null;

     public EventDanaRBolusProgress() {
    }

    public static EventDanaRBolusProgress getInstance() {
        if(eventDanaRBolusProgress == null) {
            eventDanaRBolusProgress = new EventDanaRBolusProgress();
        }
        return eventDanaRBolusProgress;
    }

}
