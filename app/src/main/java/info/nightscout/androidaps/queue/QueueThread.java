package info.nightscout.androidaps.queue;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.queue.events.EventQueueChanged;

/**
 * Created by mike on 09.11.2017.
 */

public class QueueThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(QueueThread.class);

    CommandQueue queue;

    private long connectionStartTime = 0;

    private PowerManager.WakeLock mWakeLock;

    public QueueThread(CommandQueue queue) {
        super(QueueThread.class.toString());

        this.queue = queue;
        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "QueueThread");
    }

    @Override
    public final void run() {
        mWakeLock.acquire();
        MainApp.bus().post(new EventQueueChanged());
        connectionStartTime = System.currentTimeMillis();
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();

        try {
            while (true) {
                log.debug("Looping ...");
                long secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
                if (pump.isConnecting()) {
                    log.debug("State: connecting");
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                    SystemClock.sleep(1000);
                    continue;
                }

                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    log.debug("State: timed out");
                    MainApp.bus().post(new EventDismissBolusprogressIfRunning(new PumpEnactResult()));
                    MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.connectiontimedout)));
                    pump.stopConnecting();
                    queue.clear();
                    return;
                }

                if (!pump.isConnected()) {
                    log.debug("State: connect");
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                    pump.connect("Connection needed");
                    SystemClock.sleep(1000);
                    continue;
                }

                if (queue.performing() == null) {
                    // Pickup 1st command and set performing variable
                    if (queue.size() > 0) {
                        log.debug("State: performing");
                        queue.pickup();
                        MainApp.bus().post(new EventQueueChanged());
                        queue.performing().execute();
                        queue.resetPerforming();
                        MainApp.bus().post(new EventQueueChanged());
                        SystemClock.sleep(100);
                        continue;
                    }
                }

                if (queue.size() == 0 && queue.performing() == null) {
                    log.debug("State: queue empty. disconnect");
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
                    pump.disconnect("Queue empty");
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                    return;
                }
            }
        } finally {
            mWakeLock.release();
        }
    }


}
