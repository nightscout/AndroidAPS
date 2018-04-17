package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSTreatment;
import info.nightscout.utils.SP;

/**
 * Created by mike on 20.02.2016.
 */
public class BroadcastFood {
    private static Logger log = LoggerFactory.getLogger(BroadcastFood.class);

    public static void handleNewFood(JSONArray foods, Context context, boolean isDelta) {

        List<JSONArray> splitted = BroadcastTreatment.splitArray(foods);
        for (JSONArray part : splitted) {
            Bundle bundle = new Bundle();
            bundle.putString("foods", part.toString());
            bundle.putBoolean("delta", isDelta);
            Intent intent = new Intent(Intents.ACTION_NEW_FOOD);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);
        }

        if (SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            for (JSONArray part : splitted) {
                Bundle bundle = new Bundle();
                bundle.putString("foods", part.toString());
                bundle.putBoolean("delta", isDelta);
                Intent intent = new Intent(Intents.ACTION_NEW_FOOD);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
            }
        }
    }

    public static void handleChangedFood(JSONArray foods, Context context, boolean isDelta) {

        List<JSONArray> splitted = BroadcastTreatment.splitArray(foods);
        for (JSONArray part : splitted) {
            Bundle bundle = new Bundle();
            bundle.putString("foods", part.toString());
            bundle.putBoolean("delta", isDelta);
            Intent intent = new Intent(Intents.ACTION_CHANGED_FOOD);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);
        }

        if (SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            for (JSONArray part : splitted) {
                Bundle bundle = new Bundle();
                bundle.putString("foods", part.toString());
                bundle.putBoolean("delta", isDelta);
                Intent intent = new Intent(Intents.ACTION_CHANGED_FOOD);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
            }
        }
    }

    public static void handleRemovedFood(JSONArray foods, Context context, boolean isDelta) {

        Bundle bundle = new Bundle();
        bundle.putString("foods", foods.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_REMOVED_FOOD);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);


        if (SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            bundle.putString("foods", foods.toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_REMOVED_FOOD);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }


}
