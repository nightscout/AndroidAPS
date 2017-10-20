package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.utils.SP;

/**
 * Created by mike on 22.02.2016.
 */
public class BroadcastSgvs {
    private static Logger log = LoggerFactory.getLogger(BroadcastSgvs.class);

    public static void handleNewSgv(JSONObject sgv, Context context, boolean isDelta) {

        Bundle bundle = new Bundle();
        bundle.putString("sgv", sgv.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_SGV);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            bundle.putString("sgv", sgv.toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_NEW_SGV);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }

    public static void handleNewSgv(JSONArray sgvs, Context context, boolean isDelta) {

        List<JSONArray> splitted = BroadcastTreatment.splitArray(sgvs);
        for (JSONArray part : splitted) {
            Bundle bundle = new Bundle();
            bundle.putString("sgvs", part.toString());
            bundle.putBoolean("delta", isDelta);
            Intent intent = new Intent(Intents.ACTION_NEW_SGV);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);
        }

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            for (JSONArray part : splitted) {
                Bundle bundle = new Bundle();
                bundle.putString("sgvs", part.toString());
                bundle.putBoolean("delta", isDelta);
                Intent intent = new Intent(Intents.ACTION_NEW_SGV);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
            }
        }
    }

}
