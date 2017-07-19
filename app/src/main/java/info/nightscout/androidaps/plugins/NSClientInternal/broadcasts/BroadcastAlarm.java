package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.utils.SP;

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastAlarm {
    private static Logger log = LoggerFactory.getLogger(BroadcastAlarm.class);

    public static void handleAlarm(JSONObject alarm, Context context) {

        if(!SP.getBoolean("nsclient_localbroadcasts", true)) return;

        Bundle bundle = new Bundle();
        bundle.putString("data", alarm.toString());
        Intent intent = new Intent(Intents.ACTION_ALARM);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }
}
