package info.nightscout.androidaps.plugins.PumpInsight;

import com.squareup.otto.Subscribe;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpCallback;

import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.msSince;
import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.tsl;

/**
 * Created by jamorham on 25/01/2018.
 *
 * Asynchronous adapter
 *
 */

public class InsightPumpAsyncAdapter {

    private final ConcurrentHashMap<UUID, EventInsightPumpCallback> commandResults = new ConcurrentHashMap<>();

    InsightPumpAsyncAdapter() {
        MainApp.bus().register(this);
    }

    // just log during debugging
    private static void log(String msg) {
        android.util.Log.e("INSIGHTPUMPASYNC", msg);
    }

    @Subscribe
    public void onStatusEvent(final EventInsightPumpCallback ev) {
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
    Cstatus busyWaitForCommandResult(final UUID uuid, long wait_time) {
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
    }

    // commend field preparation for results
    String getCommandComment(final UUID uuid) {
        if (commandResults.containsKey(uuid)) {
            if (commandResults.get(uuid).success) {
                return "OK";
            } else {
                return commandResults.get(uuid).message;
            }
        } else {
            return "Unknown reference";
        }
    }

}
