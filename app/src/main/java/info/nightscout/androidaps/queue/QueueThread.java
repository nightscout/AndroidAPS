package info.nightscout.androidaps.queue;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 09.11.2017.
 */

public class QueueThread extends Thread {
    private final CommandQueue queue;
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ActivePluginProvider activePlugin;
    private final ResourceHelper resourceHelper;
    private final SP sp;

    private boolean connectLogged = false;
    boolean waitingForDisconnect = false;

    private PowerManager.WakeLock mWakeLock;

    QueueThread(CommandQueue queue, Context context, AAPSLogger aapsLogger, RxBusWrapper rxBus, ActivePluginProvider activePlugin, ResourceHelper resourceHelper, SP sp) {
        super();

        this.queue = queue;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.resourceHelper = resourceHelper;
        this.sp = sp;

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null)
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:QueueThread");
    }

    @Override
    public final void run() {
        if (mWakeLock != null)
            mWakeLock.acquire(T.mins(10).msecs());
        rxBus.send(new EventQueueChanged());
        long lastCommandTime;
        long connectionStartTime = lastCommandTime = System.currentTimeMillis();

        try {
            while (true) {
                long secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
                PumpInterface pump = activePlugin.getActivePump();
                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    rxBus.send(new EventDismissBolusProgressIfRunning(null));
                    rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.connectiontimedout)));
                    aapsLogger.debug(LTag.PUMPQUEUE, "timed out");
                    pump.stopConnecting();

                    //BLUETOOTH-WATCHDOG
                    boolean watchdog = sp.getBoolean(R.string.key_btwatchdog, false);
                    long last_watchdog = sp.getLong(R.string.key_btwatchdog_lastbark, 0L);
                    watchdog = watchdog && System.currentTimeMillis() - last_watchdog > (Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000);
                    if (watchdog) {
                        aapsLogger.debug(LTag.PUMPQUEUE, "BT watchdog - toggeling the phonest bluetooth");
                        //write time
                        sp.putLong(R.string.key_btwatchdog_lastbark, System.currentTimeMillis());
                        //toggle BT
                        pump.stopConnecting();
                        pump.disconnect("watchdog");
                        SystemClock.sleep(1000);
                        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        if (bluetoothAdapter != null) {
                            bluetoothAdapter.disable();
                            SystemClock.sleep(1000);
                            bluetoothAdapter.enable();
                            SystemClock.sleep(1000);
                        }
                        //start over again once after watchdog barked
                        //Notification notification = new Notification(Notification.OLD_NSCLIENT, "Watchdog", Notification.URGENT);
                        //rxBus.send(new EventNewNotification(notification));
                        connectionStartTime = lastCommandTime = System.currentTimeMillis();
                        pump.connect("watchdog");
                    } else {
                        queue.clear();
                        aapsLogger.debug(LTag.PUMPQUEUE, "no connection possible");
                        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
                        pump.disconnect("Queue empty");
                        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED));
                        return;
                    }
                }

                if (pump.isHandshakeInProgress()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "handshaking " + secondsElapsed);
                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, (int) secondsElapsed));
                    SystemClock.sleep(100);
                    continue;
                }

                if (pump.isConnecting()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connecting " + secondsElapsed);
                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, (int) secondsElapsed));
                    SystemClock.sleep(1000);
                    continue;
                }

                if (!pump.isConnected()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connect");
                    rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, (int) secondsElapsed));
                    pump.connect("Connection needed");
                    SystemClock.sleep(1000);
                    continue;
                }

                if (queue.performing() == null) {
                    if (!connectLogged) {
                        connectLogged = true;
                        aapsLogger.debug(LTag.PUMPQUEUE, "connection time " + secondsElapsed + "s");
                    }
                    // Pickup 1st command and set performing variable
                    if (queue.size() > 0) {
                        queue.pickup();
                        if (queue.performing() != null) {
                            aapsLogger.debug(LTag.PUMPQUEUE, "performing " + queue.performing().status());
                            rxBus.send(new EventQueueChanged());
                            queue.performing().execute();
                            queue.resetPerforming();
                            rxBus.send(new EventQueueChanged());
                            lastCommandTime = System.currentTimeMillis();
                            SystemClock.sleep(100);
                            continue;
                        }
                    }
                }

                if (queue.size() == 0 && queue.performing() == null) {
                    long secondsFromLastCommand = (System.currentTimeMillis() - lastCommandTime) / 1000;
                    if (secondsFromLastCommand >= 5) {
                        waitingForDisconnect = true;
                        aapsLogger.debug(LTag.PUMPQUEUE, "queue empty. disconnect");
                        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
                        pump.disconnect("Queue empty");
                        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED));
                        aapsLogger.debug(LTag.PUMPQUEUE, "disconnected");
                        return;
                    } else {
                        aapsLogger.debug(LTag.PUMPQUEUE, "waiting for disconnect");
                        SystemClock.sleep(1000);
                    }
                }
            }
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld())
                mWakeLock.release();
            aapsLogger.debug(LTag.PUMPQUEUE, "thread end");
        }
    }
}
