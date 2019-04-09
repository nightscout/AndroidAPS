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
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 09.11.2017.
 */

public class QueueThread extends Thread {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private CommandQueue queue;

    private boolean connectLogged = false;
    boolean waitingForDisconnect = false;

    private PowerManager.WakeLock mWakeLock;

    QueueThread(CommandQueue queue) {
        super();

        this.queue = queue;
        Context context = MainApp.instance().getApplicationContext();
        if (context != null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null)
                mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:QueueThread");
        }
    }

    @Override
    public final void run() {
        if (mWakeLock != null)
            mWakeLock.acquire(T.mins(10).msecs());
        MainApp.bus().post(new EventQueueChanged());
        long lastCommandTime;
        long connectionStartTime = lastCommandTime = System.currentTimeMillis();

        try {
            while (true) {
                PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
                if (pump == null) {
                    if (L.isEnabled(L.PUMPQUEUE))
                        log.debug("pump == null");
                    MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.pumpNotInitialized)));
                    SystemClock.sleep(1000);
                    continue;
                }
                long secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;

                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    MainApp.bus().post(new EventDismissBolusprogressIfRunning(null));
                    MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.connectiontimedout)));
                    if (L.isEnabled(L.PUMPQUEUE))
                        log.debug("timed out");
                    pump.stopConnecting();

                    //BLUETOOTH-WATCHDOG
                    boolean watchdog = SP.getBoolean(R.string.key_btwatchdog, false);
                    long last_watchdog = SP.getLong(R.string.key_btwatchdog_lastbark, 0L);
                    watchdog = watchdog && System.currentTimeMillis() - last_watchdog > (Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000);
                    if (watchdog) {
                        if (L.isEnabled(L.PUMPQUEUE))
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
                        if (L.isEnabled(L.PUMPQUEUE))
                            log.debug("no connection possible");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
                        pump.disconnect("Queue empty");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                        return;
                    }
                }

                if (pump.isHandshakeInProgress()) {
                    if (L.isEnabled(L.PUMPQUEUE))
                        log.debug("handshaking " + secondsElapsed);
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.HANDSHAKING, (int) secondsElapsed));
                    SystemClock.sleep(100);
                    continue;
                }

                if (pump.isConnecting()) {
                    if (L.isEnabled(L.PUMPQUEUE))
                        log.debug("connecting " + secondsElapsed);
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                    SystemClock.sleep(1000);
                    continue;
                }

                if (!pump.isConnected()) {
                    if (L.isEnabled(L.PUMPQUEUE))
                        log.debug("connect");
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                    pump.connect("Connection needed");
                    SystemClock.sleep(1000);
                    continue;
                }

                if (queue.performing() == null) {
                    if (!connectLogged) {
                        connectLogged = true;
                        if (L.isEnabled(L.PUMPQUEUE))
                            log.debug("connection time " + secondsElapsed + "s");
                    }
                    // Pickup 1st command and set performing variable
                    if (queue.size() > 0) {
                        queue.pickup();
                        if (L.isEnabled(L.PUMPQUEUE))
                            log.debug("performing " + queue.performing().status());
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
                        if (L.isEnabled(L.PUMPQUEUE))
                            log.debug("queue empty. disconnect");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
                        pump.disconnect("Queue empty");
                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                        if (L.isEnabled(L.PUMPQUEUE))
                            log.debug("disconnected");
                        return;
                    } else {
                        if (L.isEnabled(L.PUMPQUEUE))
                            log.debug("waiting for disconnect");
                        SystemClock.sleep(1000);
                    }
                }
            }
        } finally {
            if (mWakeLock != null)
                mWakeLock.release();
            if (L.isEnabled(L.PUMPQUEUE))
                log.debug("thread end");
        }
    }
}
