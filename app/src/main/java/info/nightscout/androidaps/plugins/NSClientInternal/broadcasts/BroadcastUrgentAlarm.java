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

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastUrgentAlarm {
    private static Logger log = LoggerFactory.getLogger(BroadcastUrgentAlarm.class);

    public static void handleUrgentAlarm(JSONObject urgentalarm, Context context) {
        Bundle bundle = new Bundle();
        bundle.putString("data", urgentalarm.toString());
        Intent intent = new Intent(Intents.ACTION_URGENT_ALARM);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("URGENTALARM " + x.size() + " receivers");
    }
}
