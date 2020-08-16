package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.j256.ormlite.dao.CloseableIterator;

import org.json.JSONException;
import org.json.JSONObject;

import java.sql.SQLException;

import javax.inject.Inject;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.UploadQueueInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientResend;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 21.02.2016.
 */
public class UploadQueue implements UploadQueueInterface {
    private final AAPSLogger aapsLogger;
    private final DatabaseHelperInterface databaseHelper;
    private final Context context;
    private final SP sp;
    private final RxBusWrapper rxBus;

    @Inject
    public UploadQueue(
            AAPSLogger aapsLogger,
            DatabaseHelperInterface databaseHelper,
            Context context,
            SP sp,
            RxBusWrapper rxBus
    ) {
        this.aapsLogger = aapsLogger;
        this.databaseHelper = databaseHelper;
        this.context = context;
        this.sp = sp;
        this.rxBus = rxBus;
    }

    public String status() {
        return "QUEUE: " + databaseHelper.size(DatabaseHelper.DATABASE_DBREQUESTS);
    }

    public long size() {
        return databaseHelper.size(DatabaseHelper.DATABASE_DBREQUESTS);
    }

    private void startService() {
        if (NSClientService.handler == null) {
            context.startService(new Intent(context, NSClientService.class));
            SystemClock.sleep(2000);
        }
    }

    public void add(final DbRequest dbr) {
        if (sp.getBoolean(R.string.key_ns_noupload, false)) return;
        aapsLogger.debug(LTag.NSCLIENT, "Adding to queue: " + dbr.log());
        try {
            databaseHelper.create(dbr);
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }
        rxBus.send(new EventNSClientResend("newdata"));
    }

    void clearQueue() {
        startService();
        if (NSClientService.handler != null) {
            NSClientService.handler.post(() -> {
                aapsLogger.debug(LTag.NSCLIENT, "ClearQueue");
                databaseHelper.deleteAllDbRequests();
                aapsLogger.debug(LTag.NSCLIENT, status());
            });
        }
    }

    public void removeID(final JSONObject record) {
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
                    if (databaseHelper.deleteDbRequest(id) == 1) {
                        aapsLogger.debug(LTag.NSCLIENT, "Removed item from UploadQueue. " + status());
                    }
                } catch (JSONException e) {
                    aapsLogger.error("Unhandled exception", e);
                }
            });
        }
    }

    public void removeID(final String action, final String _id) {
        if (_id == null || _id.equals(""))
            return;
        startService();
        if (NSClientService.handler != null) {
            NSClientService.handler.post(() -> {
                databaseHelper.deleteDbRequestbyMongoId(action, _id);
                aapsLogger.debug(LTag.NSCLIENT, "Removing " + _id + " from UploadQueue. " + status());
            });
        }
    }

    String textList() {
        String result = "";
        CloseableIterator<DbRequest> iterator;
        try {
            iterator = databaseHelper.getDbRequestInterator();
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
            aapsLogger.error("Unhandled exception", e);
        }
        return result;
    }

}
