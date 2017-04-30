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
public class BroadcastMbgs {
    private static Logger log = LoggerFactory.getLogger(BroadcastMbgs.class);

    public void handleNewMbg(JSONArray mbgs, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("mbgs", mbgs.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_MBG);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("MBG " + x.size() + " receivers");
    }
}
