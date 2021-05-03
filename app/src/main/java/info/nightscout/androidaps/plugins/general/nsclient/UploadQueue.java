package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

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

    @Override
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

}
