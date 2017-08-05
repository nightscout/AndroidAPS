package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.utils.SP;

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastClearAlarm {
    private static Logger log = LoggerFactory.getLogger(BroadcastClearAlarm.class);

    public static void handleClearAlarm(JSONObject clearalarm, Context context) {
        Bundle bundle = new Bundle();
        bundle.putString("data", clearalarm.toString());
        Intent intent = new Intent(Intents.ACTION_CLEAR_ALARM);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            bundle.putString("data", clearalarm.toString());
            intent = new Intent(Intents.ACTION_CLEAR_ALARM);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }
}
