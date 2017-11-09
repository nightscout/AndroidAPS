package info.nightscout.androidaps.queue;

import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 09.11.2017.
 */

public class QueueThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(QueueThread.class);

    CommandQueue queue;
    boolean keepRunning = false;


    private long connectionStartTime = 0;

    public QueueThread(CommandQueue queue) {
        super(QueueThread.class.toString());

        this.queue = queue;
        keepRunning = true;
    }

    @Override
    public final void run() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();

        while (keepRunning) {
            if (pump.isConnecting()) {
                long secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
                MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                SystemClock.sleep(1000);
            }

            if (!pump.isConnected()) {
                MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING));
                pump.connect("Not connected");
                connectionStartTime = System.currentTimeMillis();
                SystemClock.sleep(1000);
            }

            if (queue.performing() == null) {
                // Pickup 1st command and set performing variable
                if (queue.size() > 0) {
                    queue.pickup();
                    queue.performing().execute();
                    queue.resetPerforming();
                }

            }

            if (queue.size() == 0 && queue.performing() == null) {
                pump.disconnect("Queue empty");
                keepRunning = false;
            }
        }
    }


}
