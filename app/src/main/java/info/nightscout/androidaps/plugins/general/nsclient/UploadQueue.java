package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.j256.ormlite.dao.CloseableIterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 21.02.2016.
 */
public class UploadQueue {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    public static String status() {
        return "QUEUE: " + MainApp.getDbHelper().size(DatabaseHelper.DATABASE_DBREQUESTS);
    }

    public static long size() {
        return MainApp.getDbHelper().size(DatabaseHelper.DATABASE_DBREQUESTS);
    }

    private static void startService() {
        if (NSClientService.handler == null) {
            Context context = MainApp.instance();
            context.startService(new Intent(context, NSClientService.class));
            SystemClock.sleep(2000);
        }
    }

    public static void add(final DbRequest dbr) {
        if (SP.getBoolean(R.string.key_ns_noupload, false)) return;
        startService();
        if (L.isEnabled(L.NSCLIENT))
            log.debug("Adding to queue: " + dbr.log());
        try {
            MainApp.getDbHelper().create(dbr);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        NSClientPlugin plugin = NSClientPlugin.getPlugin();
        if (plugin != null) {
            plugin.resend("newdata");
        }
    }

    static void clearQueue() {
        startService();
        if (NSClientService.handler != null) {
            NSClientService.handler.post(() -> {
                if (L.isEnabled(L.NSCLIENT))
                    log.debug("ClearQueue");
                MainApp.getDbHelper().deleteAllDbRequests();
                if (L.isEnabled(L.NSCLIENT))
                    log.debug(status());
            });
        }
    }

    public static void removeID(final JSONObject record) {
        startService();
        if (NSClientService.handler != null) {
            NSClientService.handler.post(() -> {
                try {
                    String id;
                    if (record.has("NSCLIENT_ID")) {
                        id = record.getString("NSCLIENT_ID");
                    } else {
                        return;
                    }
                    if (MainApp.getDbHelper().deleteDbRequest(id) == 1) {
                        if (L.isEnabled(L.NSCLIENT))
                            log.debug("Removed item from UploadQueue. " + UploadQueue.status());
                    }
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            });
        }
    }

    public static void removeID(final String action, final String _id) {
        if (_id == null || _id.equals(""))
            return;
        startService();
        if (NSClientService.handler != null) {
            NSClientService.handler.post(() -> {
                MainApp.getDbHelper().deleteDbRequestbyMongoId(action, _id);
                if (L.isEnabled(L.NSCLIENT))
                    log.debug("Removing " + _id + " from UploadQueue. " + UploadQueue.status());
            });
        }
    }

    String textList() {
        String result = "";
        CloseableIterator<DbRequest> iterator;
        try {
            iterator = MainApp.getDbHelper().getDbRequestInterator();
            try {
                while (iterator.hasNext()) {
                    DbRequest dbr = iterator.next();
                    result += "<br>";
                    result += dbr.action.toUpperCase() + " ";
                    result += dbr.collection + ": ";
                    result += dbr.data;
                }
            } finally {
                iterator.close();
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return result;
    }

}
