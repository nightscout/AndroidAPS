package info.nightscout.androidaps.interaction.utils;

import android.os.PowerManager;

import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;

/**
 * Created for xDrip by jamorham on 07/03/2018
 * Adapted for AAPS by dlvoy on 2019-11-11
 *
 * Tasks which are fired from events can be scheduled here and only execute when they become idle
 * and are not being rescheduled within their wait window.
 *
 */

@Singleton
public class Inevitable {

    @Inject WearUtil wearUtil;
    @Inject AAPSLogger aapsLogger;
    @Inject DateUtil dateUtil;

    @Inject Inevitable() {}

    private static final int MAX_QUEUE_TIME = (int) Constants.MINUTE_IN_MS * 6;
    private static final boolean debug = BuildConfig.DEBUG;

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    public void task(final String id, long idle_for, Runnable runnable) {
        if (idle_for > MAX_QUEUE_TIME) {
            throw new RuntimeException(id + " Requested time: " + idle_for + " beyond max queue time");
        }
        final Task task = tasks.get(id);
        if (task != null) {
            // if it already exists then extend the time
            task.extendTime(idle_for);

            if (debug)
                aapsLogger.debug(LTag.WEAR, "Extending time for: " + id + " to " + dateUtil.dateAndTimeAndSecondsString(task.when));
        } else {
            // otherwise create new task
            if (runnable == null) return; // extension only if already exists
            tasks.put(id, new Task(id, idle_for, runnable));

            if (debug) {
                aapsLogger.debug(LTag.WEAR,
                        "Creating task: " + id + " due: " + dateUtil.dateAndTimeAndSecondsString(tasks.get(id).when));
            }

            // create a thread to wait and execute in background
            final Thread t = new Thread(() -> {
                final PowerManager.WakeLock wl = wearUtil.getWakeLock(id, MAX_QUEUE_TIME + 5000);
                try {
                    boolean running = true;
                    // wait for task to be due or killed
                    while (running) {
                        wearUtil.threadSleep(500);
                        final Task thisTask = tasks.get(id);
                        running = thisTask != null && !thisTask.poll();
                    }
                } finally {
                    wearUtil.releaseWakeLock(wl);
                }
            });
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
    }

    public void kill(final String id) {
        tasks.remove(id);
    }

    private class Task {
        private long when;
        private final Runnable what;
        private final String id;

        Task(String id, long offset, Runnable what) {
            this.what = what;
            this.id = id;
            extendTime(offset);
        }

        public void extendTime(long offset) {
            this.when = wearUtil.timestamp() + offset;
        }

        public boolean poll() {
            final long till = wearUtil.msTill(when);
            if (till < 1) {
                if (debug) aapsLogger.debug(LTag.WEAR, "Executing task! " + this.id);
                tasks.remove(this.id); // early remove to allow overlapping scheduling
                what.run();
                return true;
            } else if (till > MAX_QUEUE_TIME) {
                aapsLogger.debug(LTag.WEAR, "Task: " + this.id + " In queue too long: " + till);
                tasks.remove(this.id);
                return true;
            }
            return false;
        }

    }

}
