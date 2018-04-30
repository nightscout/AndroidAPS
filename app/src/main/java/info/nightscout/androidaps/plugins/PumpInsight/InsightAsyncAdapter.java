package info.nightscout.androidaps.plugins.PumpInsight;

import android.os.PowerManager;

import com.squareup.otto.Subscribe;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightCallback;

import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.getWakeLock;
import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.msSince;
import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.releaseWakeLock;
import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.tsl;

/**
 * Created by jamorham on 25/01/2018.
 *
 * Asynchronous adapter
 *
 */

public class InsightAsyncAdapter {

    private final ConcurrentHashMap<UUID, EventInsightCallback> commandResults = new ConcurrentHashMap<>();

    InsightAsyncAdapter() {
        MainApp.bus().register(this);
    }

    // just log during debugging
    private static void log(String msg) {
        android.util.Log.e("INSIGHTPUMPASYNC", msg);
    }

    @Subscribe
    public void onStatusEvent(final EventInsightCallback ev) {
        log("Received callback event: " + ev.toString());
        commandResults.put(ev.request_uuid, ev);
    }

    // poll command result
    private Cstatus checkCommandResult(UUID uuid) {
        if (uuid == null) return Cstatus.FAILURE;
        if (commandResults.containsKey(uuid)) {
            if (commandResults.get(uuid).success) {
                return Cstatus.SUCCESS;
            } else {
                return Cstatus.FAILURE;
            }
        } else {
            return Cstatus.PENDING;
        }
    }

    // blocking call to wait for result callback
    private Cstatus busyWaitForCommandInternal(final UUID uuid, long wait_time) {
        final PowerManager.WakeLock wl = getWakeLock("insight-wait-cmd", 60000);
        try {
            log("busy wait for command " + uuid);
            if (uuid == null) return Cstatus.FAILURE;
            final long start_time = tsl();
            Cstatus status = checkCommandResult(uuid);
            while ((status == Cstatus.PENDING) && msSince(start_time) < wait_time) {
                //log("command result waiting");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    log("Got interrupted exception! " + e);
                }
                status = checkCommandResult(uuid);
            }
            if (status == Cstatus.PENDING) {
                return Cstatus.TIMEOUT;
            } else {
                return status;
            }
        } finally {
            releaseWakeLock(wl);
        }
    }

    // wait for and then package result, cleanup and return
    Mstatus busyWaitForCommandResult(final UUID uuid, long wait_time) {
        final Mstatus mstatus = new Mstatus();
        mstatus.cstatus = busyWaitForCommandInternal(uuid, wait_time);
        mstatus.event = commandResults.get(uuid);
        commandResults.remove(uuid);
        return mstatus;
    }


}
