package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSTreatment;

/**
 * Created by mike on 20.02.2016.
 */
public class BroadcastTreatment {
    private static Logger log = LoggerFactory.getLogger(BroadcastTreatment.class);

    public void handleNewTreatment(NSTreatment treatment, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatment", treatment.getData().toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("TREAT_ADD " + treatment.getEventType() + " " + x.size() + " receivers");
    }

    public void handleNewTreatment(JSONArray treatments, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatments", treatments.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("TREAT_ADD " + treatments.length() + " " + x.size() + " receivers");
    }

    public void handleChangedTreatment(JSONObject treatment, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatment", treatment.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_CHANGED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        try {
            log.debug("TREAT_CHANGE " + treatment.getString("_id") + " " + x.size() + " receivers");
        } catch (JSONException e) {}
    }

   public void handleChangedTreatment(JSONArray treatments, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatments", treatments.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_CHANGED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("TREAT_CHANGE " + treatments.length() + " " + x.size() + " receivers");
    }

    public void handleRemovedTreatment(JSONObject treatment, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatment", treatment.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_REMOVED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        try {
            log.debug("TREAT_REMOVE " + treatment.getString("_id") + " " + x.size() + " receivers");
        } catch (JSONException e) {}
    }

    public void handleRemovedTreatment(JSONArray treatments, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatments", treatments.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_REMOVED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("TREAT_REMOVE " + treatments.length() + " treatments " + x.size() + " receivers");
    }

}
