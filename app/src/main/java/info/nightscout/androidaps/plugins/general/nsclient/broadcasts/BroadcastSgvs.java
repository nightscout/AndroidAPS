package info.nightscout.androidaps.plugins.general.nsclient.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 22.02.2016.
 */
public class BroadcastSgvs {
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

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, false)) {
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
