package info.nightscout.androidaps.plugins.DanaR.Services;

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
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaR.DanaRPump;
import info.nightscout.androidaps.plugins.DanaR.SerialIOThread;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgBolusStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgCheckValue;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetActivateBasalProfile;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetBasalProfile;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingActiveProfile;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingBasal;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingGlucose;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingProfileRatiosAll;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgStatus;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgStatusBasic;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.plugins.DanaR.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusProgress;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRNewStatus;
import info.nightscout.client.data.NSProfile;
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

    private DanaRPump danaRPump;
    private Treatment bolusingTreatment = null;

    private static Object connectionInProgress = new Object();

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
        DanaRFragment danaRFragment = (DanaRFragment) MainActivity.getSpecificPlugin(DanaRFragment.class);
        if (danaRFragment != null)
            danaRPump = danaRFragment.getDanaRPump();
        else
            log.error("danaRFragment is null");
        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DanaConnection");
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
        // TODO: mDanaConnection.stop();

        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("Application exit");

        MainApp.instance().getApplicationContext().unregisterReceiver(receiver);

        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("EventAppExit finished");
    }

    private boolean isConnected() {
        return mRfcommSocket != null && mRfcommSocket.isConnected();
    }

    public void connect(String from) {
        if (isConnected()) {
            if (Config.logDanaBTComm)
                log.debug("already connected from:" + from);
            return;
        }
        final long maxConnectionTime = 5 * 60 * 1000L; // 5 min
        synchronized (connectionInProgress) {
            mWakeLock.acquire();
            getBTSocketForSelectedPump();
            if (mRfcommSocket == null || mBTDevice == null)
                return; // Device not found
            long startTime = new Date().getTime();
            while (!isConnected() && startTime + maxConnectionTime >= new Date().getTime()) {
                long secondsElapsed = (new Date().getTime() - startTime) / 1000L;
                MainApp.bus().post(new EventDanaRConnectionStatus(EventDanaRConnectionStatus.CONNECTING, (int) secondsElapsed));
                if (Config.logDanaBTComm)
                    log.debug("connect waiting " + secondsElapsed + "sec from:" + from);
                try {
                    mRfcommSocket.connect();
                } catch (IOException e) {
                }
                waitMsec(1000);
            }
            if (isConnected()) {
                if (mSerialIOThread != null) {
                    mSerialIOThread.disconnect("Recreate SerialIOThread");
                }
                mSerialIOThread = new SerialIOThread(mRfcommSocket);
                MainApp.bus().post(new EventDanaRConnectionStatus(EventDanaRConnectionStatus.CONNECTED, 0));
                getPumpStatus();
            } else {
                log.error("Pump connection timed out");
            }
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

    private void getPumpStatus() {
        try {
            MsgStatus statusMsg = new MsgStatus();
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();


            mSerialIOThread.sendMessage(tempStatusMsg); // do this before statusBasic because here is temp duration
            mSerialIOThread.sendMessage(exStatusMsg);
            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);

            if (danaRPump.isNewPump) {
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgCheckValue());
            }

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
                connect("getPumpStatus fail");
                return;
            }

            Date now = new Date();
            if (danaRPump.lastSettingsRead.getTime() + 60 * 60 * 1000L < now.getTime()) {
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile());
                //0x3203
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues());
                mSerialIOThread.sendMessage(new MsgSettingGlucose());
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatiosAll());
                danaRPump.lastSettingsRead = now;
            }

            danaRPump.lastConnection = now;
            MainApp.bus().post(new EventDanaRNewStatus());
        } catch (Exception e) {
            log.error("err", e);
        }
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

    public boolean bolus(Double amount, Treatment t) {
        connect("bolus");
        if (!isConnected()) return false;
        // TODO: progress dialog
        bolusingTreatment = t;
        MsgBolusStart start = new MsgBolusStart(amount);
        MsgBolusProgress progress = new MsgBolusProgress(MainApp.bus(), amount, t);
        MsgBolusStop stop = new MsgBolusStop(MainApp.bus(), amount, t);

        mSerialIOThread.sendMessage(start);
        while (!stop.stopped && !start.failed) {
            waitMsec(100);
        }
        bolusingTreatment = null;
        getPumpStatus();
        return true;
    }

    public void bolusStop() {
        Treatment lastBolusingTreatment = bolusingTreatment;
        if (Config.logDanaBTComm)
            log.debug("bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        MsgBolusStop stop = new MsgBolusStop();
        stop.forced = true;
        mSerialIOThread.sendMessage(stop);
        while (!stop.stopped) {
            mSerialIOThread.sendMessage(stop);
            waitMsec(200);
        }
        // and update ns status to last amount
        waitMsec(60000);
        EventDanaRBolusProgress be = EventDanaRBolusProgress.getInstance();
        be.sStatus = "";
        MainApp.bus().post(be);
    }

    public boolean carbsEntry(int amount) {
        connect("carbsEntry");
        if (!isConnected()) return false;
        Calendar time = Calendar.getInstance();
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(time, amount);
        mSerialIOThread.sendMessage(msg);
        return true;
    }

    public boolean updateBasalsInPump(final NSProfile profile) {
        connect("updateBasalsInPump");
        if (!isConnected()) return false;
        double[] basal = buildDanaRProfileRecord(profile);
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
        mSerialIOThread.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
        mSerialIOThread.sendMessage(msgActivate);
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
