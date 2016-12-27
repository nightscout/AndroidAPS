package info.nightscout.androidaps.plugins.DanaRKorean.Services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgBolusStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryAlarm;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryBasalHour;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryBolus;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryCarbo;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryDailyInsulin;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryDone;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryError;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryGlucose;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistoryRefill;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgHistorySuspend;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgPCCommStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgPCCommStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetSingleBasalProfile;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetTime;
import info.nightscout.androidaps.plugins.DanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusStart;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;
import info.nightscout.androidaps.plugins.DanaRKorean.SerialIOThread;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgCheckValue;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingBasal;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingGlucose;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingMeal;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgStatusBasic;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.plugins.DanaRKorean.comm.MsgStatusTempBasal;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class ExecutionService extends Service {
    private static Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
    private String devName;

    private SerialIOThread mSerialIOThread;
    private BluetoothSocket mRfcommSocket;
    private BluetoothDevice mBTDevice;

    private PowerManager.WakeLock mWakeLock;
    private IBinder mBinder = new LocalBinder();

    private DanaRKoreanPump danaRKoreanPump;
    private Treatment bolusingTreatment = null;

    private static Boolean connectionInProgress = false;
    private static final Object connectionLock = new Object();

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device has disconnected " + device.getName());//Device has disconnected
                if (mBTDevice != null && mBTDevice.getName().equals(device.getName())) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect("BT disconnection broadcast");
                    }
                    MainApp.bus().post(new EventDanaRConnectionStatus(EventDanaRConnectionStatus.DISCONNECTED, 0));
                }
            }
        }
    };

    public ExecutionService() {
        registerBus();
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        danaRKoreanPump = DanaRKoreanPlugin.getDanaRPump();

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExecutionService");
    }

    public class LocalBinder extends Binder {
        public ExecutionService getServiceInstance() {
            return ExecutionService.this;
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
        if (danaRKoreanPump.password != -1 && danaRKoreanPump.password != SafeParse.stringToInt(SP.getString("danar_password", "-1"))) {
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
            long startTime = new Date().getTime();
            while (!isConnected() && startTime + maxConnectionTime >= new Date().getTime()) {
                long secondsElapsed = (new Date().getTime() - startTime) / 1000L;
                MainApp.bus().post(new EventDanaRConnectionStatus(EventDanaRConnectionStatus.CONNECTING, (int) secondsElapsed));
                if (Config.logDanaBTComm)
                    log.debug("connect waiting " + secondsElapsed + "sec from: " + from);
                try {
                    mRfcommSocket.connect();
                } catch (IOException e) {
                    //e.printStackTrace();
                    if (e.getMessage().contains("socket closed")) {
                        e.printStackTrace();
                        break;
                    }
                }
                waitMsec(1000);

                if (isConnected()) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect("Recreate SerialIOThread");
                    }
                    mSerialIOThread = new SerialIOThread(mRfcommSocket);
                    MainApp.bus().post(new EventDanaRConnectionStatus(EventDanaRConnectionStatus.CONNECTED, 0));
                    if (!getPumpStatus()) {
                        mSerialIOThread.disconnect("getPumpStatus failed");
                        waitMsec(3000);
                    }
                }
            }
            if (!isConnected()) {
                MainApp.bus().post(new EventDanaRConnectionStatus(EventDanaRConnectionStatus.DISCONNECTED, 0));
                log.error("Pump connection timed out");
            }
            connectionInProgress = false;
            mWakeLock.release();
        }
    }

    private void getBTSocketForSelectedPump() {
        devName = SP.getString("danar_bt_name", "");
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
            //MsgStatus statusMsg = new MsgStatus();
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();


            mSerialIOThread.sendMessage(new MsgSettingShippingInfo()); // TODO: show it somewhere
            mSerialIOThread.sendMessage(tempStatusMsg); // do this before statusBasic because here is temp duration
            mSerialIOThread.sendMessage(exStatusMsg);
            //mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);

            mSerialIOThread.sendMessage(new MsgCheckValue());

//            if (!statusMsg.received) {
//                mSerialIOThread.sendMessage(statusMsg);
//            }
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
            if (/*!statusMsg.received || */!statusBasicMsg.received || !tempStatusMsg.received || !exStatusMsg.received) {
                waitMsec(10 * 1000);
                log.debug("getPumpStatus failed");
                return false;
            }

            Date now = new Date();
            if (danaRKoreanPump.lastSettingsRead.getTime() + 60 * 60 * 1000L < now.getTime() || !((DanaRKoreanPlugin)MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).isInitialized()) {
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgSettingMeal());
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues());
                mSerialIOThread.sendMessage(new MsgSettingGlucose());
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios());
                mSerialIOThread.sendMessage(new MsgSetTime(new Date()));
                danaRKoreanPump.lastSettingsRead = now;
            }

            danaRKoreanPump.lastConnection = now;
            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean tempBasal(int percent, int durationInHours) {
        connect("tempBasal");
        if (!isConnected()) return false;
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        return true;
    }

    public boolean tempBasalStop() {
        connect("tempBasalStop");
        if (!isConnected()) return false;
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        connect("extendedBolus");
        if (!isConnected()) return false;
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        return true;
    }

    public boolean extendedBolusStop() {
        connect("extendedBolusStop");
        if (!isConnected()) return false;
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop());
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        return true;
    }

    public boolean bolus(Double amount, int carbs, Treatment t) {
        bolusingTreatment = t;
        MsgBolusStart start = new MsgBolusStart(amount);
        MsgBolusProgress progress = new MsgBolusProgress(amount, t); // initialize static variables
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        connect("bolus");
        if (!isConnected()) return false;

        if (carbs > 0) {
            Calendar time = Calendar.getInstance();
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(time, carbs));
        }
        MainApp.bus().post(new EventDanaRBolusStart());

        if (!stop.stopped) {
            mSerialIOThread.sendMessage(start);
        } else {
            t.insulin = 0d;
            return false;
        }
        while (!stop.stopped && !start.failed) {
            waitMsec(100);
            if (progress.lastReceive != 0 && (new Date().getTime() - progress.lastReceive) > 5 * 1000L) { // if i didn't receive status for more than 5 sec expecting broken comm
                stop.stopped = true;
                stop.forced = true;
                log.debug("Communication stopped");
            }
        }
        waitMsec(300);
        bolusingTreatment = null;
        getPumpStatus();
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

    public boolean carbsEntry(int amount) {
        connect("carbsEntry");
        if (!isConnected()) return false;
        Calendar time = Calendar.getInstance();
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(time, amount);
        mSerialIOThread.sendMessage(msg);
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

    public boolean updateBasalsInPump(final NSProfile profile) {
        connect("updateBasalsInPump");
        if (!isConnected()) return false;
        double[] basal = buildDanaRProfileRecord(profile);
        MsgSetSingleBasalProfile msgSet = new MsgSetSingleBasalProfile(basal);
        mSerialIOThread.sendMessage(msgSet);
        danaRKoreanPump.lastSettingsRead = new Date(0); // force read full settings
        getPumpStatus();
        return true;
    }

    private double[] buildDanaRProfileRecord(NSProfile nsProfile) {
        double[] record = new double[24];
        for (Integer hour = 0; hour < 24; hour++) {
            double value = nsProfile.getBasal(hour * 60 * 60);
            if (Config.logDanaMessageDetail)
                log.debug("NS basal value for " + hour + ":00 is " + value);
            record[hour] = value;
        }
        return record;
    }

    private void waitMsec(long msecs) {
        try {
            Thread.sleep(msecs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
