package info.nightscout.androidaps.plugins.PumpDanaRv2.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.*;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.SerialIOThread;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgHistoryEvents_v2;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgSetAPSTempBasalStart_v2;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgSetHistoryEntry_v2;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgCheckValue_v2;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgStatusBolusExtended_v2;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgStatusTempBasal_v2;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class DanaRv2ExecutionService extends Service {
    private static Logger log = LoggerFactory.getLogger(DanaRv2ExecutionService.class);

    private String devName;

    private SerialIOThread mSerialIOThread;
    private BluetoothSocket mRfcommSocket;
    private BluetoothDevice mBTDevice;

    private PowerManager.WakeLock mWakeLock;
    private IBinder mBinder = new LocalBinder();

    private DanaRPump danaRPump;
    private Treatment bolusingTreatment = null;

    private static Boolean connectionInProgress = false;
    private static final Object connectionLock = new Object();

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private long lastHistoryFetched = 0;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected " + device.getName());//Device was disconnected
                if (mBTDevice != null && mBTDevice.getName() != null && mBTDevice.getName().equals(device.getName())) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect("BT disconnection broadcast");
                    }
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                }
            }
        }
    };

    public DanaRv2ExecutionService() {
        registerBus();
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        danaRPump = DanaRPump.getInstance();

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DanaRv2ExecutionService");
    }

    public class LocalBinder extends Binder {
        public DanaRv2ExecutionService getServiceInstance() {
            return DanaRv2ExecutionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (Config.logFunctionCalls)
            log.debug("EventAppExit received");

        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("Application exit");

        MainApp.instance().getApplicationContext().unregisterReceiver(receiver);

        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("EventAppExit finished");
    }

    public boolean isConnected() {
        return mRfcommSocket != null && mRfcommSocket.isConnected();
    }

    public boolean isConnecting() {
        return connectionInProgress;
    }

    public void disconnect(String from) {
        if (mSerialIOThread != null)
            mSerialIOThread.disconnect(from);
    }

    public void connect(String from) {
        if (danaRPump.password != -1 && danaRPump.password != SP.getInt(R.string.key_danar_password, -1)) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.wrongpumppassword), R.raw.error);
            return;
        }
        while (isConnected() || isConnecting()) {
            if (Config.logDanaBTComm)
                log.debug("already connected/connecting from: " + from);
            waitMsec(3000);
        }
        final long maxConnectionTime = 5 * 60 * 1000L; // 5 min
        synchronized (connectionLock) {
            //log.debug("entering connection while loop");
            connectionInProgress = true;
            mWakeLock.acquire();
            getBTSocketForSelectedPump();
            if (mRfcommSocket == null || mBTDevice == null)
                return; // Device not found
            long startTime = System.currentTimeMillis();
            while (!isConnected() && startTime + maxConnectionTime >= System.currentTimeMillis()) {
                long secondsElapsed = (System.currentTimeMillis() - startTime) / 1000L;
                MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING, (int) secondsElapsed));
                if (Config.logDanaBTComm)
                    log.debug("connect waiting " + secondsElapsed + "sec from: " + from);
                try {
                    mRfcommSocket.connect();
                } catch (IOException e) {
                    //log.error("Unhandled exception", e);
                    if (e.getMessage().contains("socket closed")) {
                        log.error("Unhandled exception", e);
                        break;
                    }
                }
                waitMsec(1000);

                if (isConnected()) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect("Recreate SerialIOThread");
                    }
                    mSerialIOThread = new SerialIOThread(mRfcommSocket);
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTED, 0));
                    if (!getPumpStatus()) {
                        mSerialIOThread.disconnect("getPumpStatus failed");
                        waitMsec(3000);
                        if (!MainApp.getSpecificPlugin(DanaRv2Plugin.class).isEnabled(PluginBase.PUMP))
                            return;
                        getBTSocketForSelectedPump();
                        startTime = System.currentTimeMillis();
                    }
                }
            }
            if (!isConnected()) {
                MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                log.error("Pump connection timed out");
            }
            connectionInProgress = false;
            mWakeLock.release();
        }
    }

    private void getBTSocketForSelectedPump() {
        devName = SP.getString(MainApp.sResources.getString(R.string.key_danar_bt_name), "");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : bondedDevices) {
                if (devName.equals(device.getName())) {
                    mBTDevice = device;
                    try {
                        mRfcommSocket = mBTDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (IOException e) {
                        log.error("Error creating socket: ", e);
                    }
                    break;
                }
            }
        } else {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.nobtadapter));
        }
        if (mBTDevice == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.devicenotfound));
        }
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange pch) {
        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("EventPreferenceChange");
    }

    private boolean getPumpStatus() {
        try {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpstatus)));
            MsgStatus statusMsg = new MsgStatus();
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
            MsgStatusTempBasal_v2 tempStatusMsg = new MsgStatusTempBasal_v2();
            MsgStatusBolusExtended_v2 exStatusMsg = new MsgStatusBolusExtended_v2();
            MsgCheckValue_v2 checkValue = new MsgCheckValue_v2();

            if (danaRPump.isNewPump) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return false;
                }
            }

            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingbolusstatus)));

            if (!statusMsg.received) {
                mSerialIOThread.sendMessage(statusMsg);
            }
            if (!statusBasicMsg.received) {
                mSerialIOThread.sendMessage(statusBasicMsg);
            }
            if (!tempStatusMsg.received) {
                // Load of status of current basal rate failed, give one more try
                mSerialIOThread.sendMessage(tempStatusMsg);
            }
            if (!exStatusMsg.received) {
                // Load of status of current extended bolus failed, give one more try
                mSerialIOThread.sendMessage(exStatusMsg);
            }

            // Check we have really current status of pump
            if (!statusMsg.received || !statusBasicMsg.received || !tempStatusMsg.received || !exStatusMsg.received) {
                waitMsec(10 * 1000);
                log.debug("getPumpStatus failed");
                return false;
            }

            Date now = new Date();
            if (danaRPump.lastSettingsRead.getTime() + 60 * 60 * 1000L < now.getTime() || !MainApp.getSpecificPlugin(DanaRv2Plugin.class).isInitialized()) {
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile());
                mSerialIOThread.sendMessage(new MsgSettingMeal());
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues());
                mSerialIOThread.sendMessage(new MsgSettingGlucose());
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatiosAll());
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                long timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    mSerialIOThread.sendMessage(new MsgSetTime(new Date()));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                    timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.lastSettingsRead = now;
            }

            loadEvents();

            danaRPump.lastConnection = now;
            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                log.debug("Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits);
                Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.sResources.getString(R.string.approachingdailylimit), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(reportFail));
                NSUpload.uploadError(MainApp.sResources.getString(R.string.approachingdailylimit) + ": " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits + "U");
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return true;
    }

    public boolean tempBasal(int percent, int durationInHours) {
        connect("tempBasal");
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            waitMsec(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean highTempBasal(int percent) {
        connect("highTempBasal");
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            waitMsec(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetAPSTempBasalStart_v2(percent));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        connect("tempBasalStop");
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        connect("extendedBolus");
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended_v2());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        connect("extendedBolusStop");
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop());
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended_v2());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean bolus(double amount, int carbs, long carbtime, Treatment t) {
        bolusingTreatment = t;
        int speed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (speed == 0)
            start = new MsgBolusStart(amount);
        else
            start = new MsgBolusStartWithSpeed(amount, speed);
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        connect("bolus");
        if (!isConnected()) return false;

        if (carbs > 0) {
            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbtime, carbs);
            mSerialIOThread.sendMessage(msg);
            MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(DanaRPump.CARBS, carbtime, carbs, 0);
            mSerialIOThread.sendMessage(msgSetHistoryEntry_v2);
            lastHistoryFetched = carbtime - 60000;
        }
        if (amount > 0) {
            MsgBolusProgress progress = new MsgBolusProgress(amount, t); // initialize static variables

            if (!stop.stopped) {
                mSerialIOThread.sendMessage(start);
            } else {
                t.insulin = 0d;
                return false;
            }
            while (!stop.stopped && !start.failed) {
                waitMsec(100);
                if ((System.currentTimeMillis() - progress.lastReceive) > 5 * 1000L) { // if i didn't receive status for more than 5 sec expecting broken comm
                    stop.stopped = true;
                    stop.forced = true;
                    log.debug("Communication stopped");
                }
            }
        }
        bolusingTreatment = null;
        // run loading history in separate thread and allow bolus dialog to be closed
        new Thread(new Runnable() {
            @Override
            public void run() {
                waitMsec(4000);
                if (!(isConnected()))
                    connect("loadEvents");
                loadEvents();
            }
        }).start();
        return true;
    }

    public void bolusStop() {
        if (Config.logDanaBTComm)
            log.debug("bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        MsgBolusStop stop = new MsgBolusStop();
        stop.forced = true;
        if (isConnected()) {
            mSerialIOThread.sendMessage(stop);
            while (!stop.stopped) {
                mSerialIOThread.sendMessage(stop);
                waitMsec(200);
            }
        } else {
            stop.stopped = true;
        }
    }

    public boolean carbsEntry(int amount, long time) {
        connect("carbsEntry");
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(time, amount);
        mSerialIOThread.sendMessage(msg);
        MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(DanaRPump.CARBS, time, amount, 0);
        mSerialIOThread.sendMessage(msgSetHistoryEntry_v2);
        lastHistoryFetched = time - 1;
        return true;
    }

    public boolean loadHistory(byte type) {
        connect("loadHistory");
        if (!isConnected()) return false;
        MessageBase msg = null;
        switch (type) {
            case RecordTypes.RECORD_TYPE_ALARM:
                msg = new MsgHistoryAlarm();
                break;
            case RecordTypes.RECORD_TYPE_BASALHOUR:
                msg = new MsgHistoryBasalHour();
                break;
            case RecordTypes.RECORD_TYPE_BOLUS:
                msg = new MsgHistoryBolus();
                break;
            case RecordTypes.RECORD_TYPE_CARBO:
                msg = new MsgHistoryCarbo();
                break;
            case RecordTypes.RECORD_TYPE_DAILY:
                msg = new MsgHistoryDailyInsulin();
                break;
            case RecordTypes.RECORD_TYPE_ERROR:
                msg = new MsgHistoryError();
                break;
            case RecordTypes.RECORD_TYPE_GLUCOSE:
                msg = new MsgHistoryGlucose();
                break;
            case RecordTypes.RECORD_TYPE_REFILL:
                msg = new MsgHistoryRefill();
                break;
            case RecordTypes.RECORD_TYPE_SUSPEND:
                msg = new MsgHistorySuspend();
                break;
        }
        MsgHistoryDone done = new MsgHistoryDone();
        mSerialIOThread.sendMessage(new MsgPCCommStart());
        waitMsec(400);
        mSerialIOThread.sendMessage(msg);
        while (!done.received && mRfcommSocket.isConnected()) {
            waitMsec(100);
        }
        waitMsec(200);
        mSerialIOThread.sendMessage(new MsgPCCommStop());
        return true;
    }

    public boolean loadEvents() {
        if (!isConnected()) return false;
        waitMsec(300);
        MsgHistoryEvents_v2 msg;
        if (lastHistoryFetched == 0) {
            msg = new MsgHistoryEvents_v2();
            log.debug("Loading complete event history");
        } else {
            msg = new MsgHistoryEvents_v2(lastHistoryFetched);
            log.debug("Loading event history from: " + new Date(lastHistoryFetched).toLocaleString());
        }
        mSerialIOThread.sendMessage(msg);
        while (!msg.done && mRfcommSocket.isConnected()) {
            waitMsec(100);
        }
        waitMsec(200);
        lastHistoryFetched = MsgHistoryEvents_v2.lastEventTimeLoaded;
        return true;
    }

    public boolean updateBasalsInPump(final Profile profile) {
        connect("updateBasalsInPump");
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.buildDanaRProfileRecord(profile);
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
        mSerialIOThread.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
        mSerialIOThread.sendMessage(msgActivate);
        danaRPump.lastSettingsRead = new Date(0); // force read full settings
        getPumpStatus();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    private void waitMsec(long msecs) {
        SystemClock.sleep(msecs);
    }
}
