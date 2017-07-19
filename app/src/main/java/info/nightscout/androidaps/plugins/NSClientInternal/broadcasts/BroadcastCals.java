package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.utils.SP;

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastCals {
    private static Logger log = LoggerFactory.getLogger(BroadcastCals.class);

    public static void handleNewCal(JSONArray cals, Context context, boolean isDelta) {

        if(!SP.getBoolean("nsclient_localbroadcasts", true)) return;

        Bundle bundle = new Bundle();
        bundle.putString("cals", cals.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_CAL);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }
}
