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

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastCals {
    private static Logger log = LoggerFactory.getLogger(BroadcastCals.class);

    public void handleNewCal(JSONArray cals, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("cals", cals.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_CAL);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("CAL " + x.size() + " receivers");
    }
}
