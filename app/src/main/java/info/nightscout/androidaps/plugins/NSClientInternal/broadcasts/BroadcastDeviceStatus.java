package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Services.Intents;


public class BroadcastDeviceStatus {
    private static Logger log = LoggerFactory.getLogger(BroadcastDeviceStatus.class);

    public void handleNewDeviceStatus(JSONObject status, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("devicestatus", status.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_DEVICESTATUS);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("DEVICESTATUS " + x.size() + " receivers");
    }
    public void handleNewDeviceStatus(JSONArray statuses, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("devicestatuses", statuses.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_DEVICESTATUS);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("DEVICESTATUS " + x.size() + " receivers");
    }
}
