package info.nightscout.androidaps.plugins.NSClientInternal.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastTreatment;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

public class DBAccessReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(DBAccessReceiver.class);


    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                DBAccessReceiver.class.getSimpleName());
        wakeLock.acquire();
        try {
            Bundle bundles = intent.getExtras();
            if (bundles == null) return;
            if (!bundles.containsKey("action")) return;

            String collection = null;
            String _id = null;
            JSONObject data = null;
            String action = bundles.getString("action");
            try {
                collection = bundles.getString("collection");
            } catch (Exception e) {
            }
            try {
                _id = bundles.getString("_id");
            } catch (Exception e) {
            }
            try {
                data = new JSONObject(bundles.getString("data"));
            } catch (Exception e) {
            }

            if (data == null && !action.equals("dbRemove") || _id == null && action.equals("dbRemove")) {
                log.debug("DBACCESS no data inside record");
                return;
            }

            if (action.equals("dbRemove")) {
                data = new JSONObject();
            }
            // mark by id
            Long nsclientid = System.currentTimeMillis();
            try {
                data.put("NSCLIENT_ID", nsclientid);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }

            if (!isAllowedCollection(collection)) {
                log.debug("DBACCESS wrong collection specified");
                return;
            }

            if (action.equals("dbRemove")) {
                if (shouldUpload()) {
                    DbRequest dbr = new DbRequest(action, collection, nsclientid.toString(), _id);
                    UploadQueue.add(dbr);
                }
            } else  if (action.equals("dbUpdate")) {
                if (shouldUpload()) {
                    DbRequest dbr = new DbRequest(action, collection, nsclientid.toString(), _id, data);
                    UploadQueue.add(dbr);
                }
            } else {
                DbRequest dbr = new DbRequest(action, collection, nsclientid.toString(), data);
                // this is not used as mongo _id but only for searching in UploadQueue database
                // if record has to be removed from queue before upload
                dbr._id = nsclientid.toString();

                if (shouldUpload()) {
                    UploadQueue.add(dbr);
                }
                if (collection.equals("treatments")) {
                    generateTreatmentOfflineBroadcast(dbr);
                }
            }

        } finally {
            wakeLock.release();
        }

    }

    public boolean shouldUpload() {
        NSClientPlugin nsClientPlugin = MainApp.getSpecificPlugin(NSClientPlugin.class);
        return nsClientPlugin.isEnabled(PluginType.GENERAL) && !SP.getBoolean(R.string.key_ns_noupload, false);
    }

    public void generateTreatmentOfflineBroadcast(DbRequest request) {
        if (request.action.equals("dbAdd")) {
            try {
                JSONObject data = new JSONObject(request.data);
                data.put("mills", DateUtil.fromISODateString(data.getString("created_at")).getTime());
                data.put("_id", data.get("NSCLIENT_ID")); // this is only fake id
                BroadcastTreatment.handleNewTreatment(data, false, true);
            } catch (Exception e) {
                log.error("Unhadled exception", e);
            }
        }
    }

    private boolean isAllowedCollection(String collection) {
        // "treatments" || "entries" || "devicestatus" || "profile" || "food"
        if (collection.equals("treatments")) return true;
        if (collection.equals("entries")) return true;
        if (collection.equals("devicestatus")) return true;
        if (collection.equals("profile")) return true;
        if (collection.equals("food")) return true;
        return false;
    }
}
