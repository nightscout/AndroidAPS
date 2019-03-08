package info.nightscout.androidaps.plugins.general.nsclient.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastAnnouncement {
    public static void handleAnnouncement(JSONObject announcement, Context context) {
        Bundle bundle = new Bundle();
        bundle.putString("data", announcement.toString());
        Intent intent = new Intent(Intents.ACTION_ANNOUNCEMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, false)) {
            bundle = new Bundle();
            bundle.putString("data", announcement.toString());
            intent = new Intent(Intents.ACTION_ANNOUNCEMENT);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }
}
