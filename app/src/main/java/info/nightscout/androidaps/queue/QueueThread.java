package info.nightscout.androidaps.queue;

import android.bluetooth.BluetoothAdapter;
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
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.utils.SP;

/**
 * Created by mike on 09.11.2017.
 */

public class QueueThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(QueueThread.class);

    private CommandQueue queue;

    private long lastCommandTime = 0;
    private boolean connectLogged = false;
    public boolean waitingForDisconnect = false;

    private PowerManager.WakeLock mWakeLock;

    public QueueThread(CommandQueue queue) {
        super();

        this.queue = queue;
        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QueueThread");
    }

    @Override
    public final void run() {
        mWakeLock.acquire();
        MainApp.bus().post(new EventQueueChanged());
        long connectionStartTime = lastCommandTime = System.currentTimeMillis();

        try {
            while (true) {
                PumpInterface pump = ConfigBuilderPlugin.getActivePump();
                if (pump == null) {
                    log.debug("QUEUE: pump == null");
                    MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.pumpNotInitialized)));
                    SystemClock.sleep(1000);
                    continue;
                }
                long secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;

                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    MainApp.bus().post(new EventDismissBolusprogressIfRunning(null));
                    MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.connectiontimedout)));
                    log.debug("QUEUE: timed out");
                    pump.stopConnecting();

                    //BLUETOOTH-WATCHDOG
                    boolean watchdog = SP.getBoolean(R.string.key_btwatchdog, false);
                    long last_watchdog = SP.getLong(R.string.key_btwatchdog_lastbark, 0l);
                    watchdog = watchdog && System.currentTimeMillis() - last_watchdog > (Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000);
                    if(watchdog) {
                        log.debug("BT watchdog - toggeling the phonest bluetooth");
                        //write time
                        SP.putLong(R.string.key_btwatchdog_lastbark, System.currentTimeMillis());
                        //toggle BT
                        pump.stopConnecting();
                        pump.disconnect("watchdog");
                        SystemClock.sleep(1000);
                        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        mBluetoothAdapter.disable();
                        SystemClock.sleep(1000);
                        mBluetoothAdapter.enable();
                        SystemClock.sleep(1000);
                        //start over again once after watchdog barked
                        //Notification notification = new Notification(Notification.OLD_NSCLIENT, "Watchdog", Notification.URGENT);
                        //MainApp.bus().post(new EventNewNotification(notification));
                        connectionStartTime = lastCommandTime = System.currentTimeMillis();
                        pump.connect("watchdog");
                    } else {
                        queue.clear();
                        log.debug("QUEUE: no connection possible");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
                        pump.disconnect("Queue empty");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                        return;
                    }
                }

                if (pump.isConnecting()) {
                    log.debug("QUEUE: connecting " + secondsElapsed);
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                    SystemClock.sleep(1000);
                    continue;
                }


                if (!pump.isConnected()) {
                    log.debug("QUEUE: connect");
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                    pump.connect("Connection needed");
                    SystemClock.sleep(1000);
                    continue;
                }

                if (queue.performing() == null) {
                    if (!connectLogged) {
                        connectLogged = true;
                        log.debug("QUEUE: connection time " + secondsElapsed + "s");
                    }
                    // Pickup 1st command and set performing variable
                    if (queue.size() > 0) {
                        queue.pickup();
                        log.debug("QUEUE: performing " + queue.performing().status());
                        MainApp.bus().post(new EventQueueChanged());
                        queue.performing().execute();
                        queue.resetPerforming();
                        MainApp.bus().post(new EventQueueChanged());
                        lastCommandTime = System.currentTimeMillis();
                        SystemClock.sleep(100);
                        continue;
                    }
                }

                if (queue.size() == 0 && queue.performing() == null) {
                    long secondsFromLastCommand = (System.currentTimeMillis() - lastCommandTime) / 1000;
                    if (secondsFromLastCommand >= 5) {
                        waitingForDisconnect = true;
                        log.debug("QUEUE: queue empty. disconnect");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
                        pump.disconnect("Queue empty");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                        log.debug("QUEUE: disconnected");
                        return;
                    } else {
                        log.debug("QUEUE: waiting for disconnect");
                        SystemClock.sleep(1000);
                    }
                }
            }
        } finally {
            mWakeLock.release();
        }
    }


}
