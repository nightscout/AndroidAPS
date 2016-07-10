package info.nightscout.androidaps.plugins.DanaR;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.j256.ormlite.dao.Dao;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.AlertService;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.DanaR.comm.*;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusProgress;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRNewStatus;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 07.07.2016.
 */
public class DanaConnection {
    private static Logger log = LoggerFactory.getLogger(DanaConnection.class);

    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
    public String devName = SP.getString("danar_bt_name", "");

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    private final Bus mBus;
    private SerialEngine mSerialEngine;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private BluetoothSocket mRfcommSocket;
    private BluetoothDevice mDevice;
    PowerManager.WakeLock mWakeLock;
    private Treatment bolusingTreatment = null;

    private DanaRFragment danaRFragment;
    private DanaRPump danaRPump;

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledDisconnection = null;


    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");


    public DanaConnection(Bus bus) {
        danaRFragment = (DanaRFragment) MainActivity.getSpecificPlugin(DanaRFragment.class);
        danaRFragment.setDanaConnection(this);
        danaRPump = danaRFragment.getDanaRPump();

        mHandlerThread = new HandlerThread(DanaConnection.class.getSimpleName());
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper());

        getSelectedPump();
        this.mBus = bus;
        createRfCommSocket();

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DanaConnection");
    }

    private void createRfCommSocket() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : devices) {
                String dName = device.getName();
                if (devName.equals(dName)) {
                    mDevice = device;

                    try {
                        mRfcommSocket = mDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (IOException e) {
                        log.error("Error creating socket: ", e);
                    }
                    break;
                }
            }
            registerBTdisconnectionBroadcastReceiver();
        } else {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.nobtadapter));
        }
        if (mDevice == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.devicenotfound));
        }
    }

    private void registerBTdisconnectionBroadcastReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    log.debug("Device has disconnected " + device.getName());//Device has disconnected
                    if (mDevice.getName().equals(device.getName())) {
                        if (mRfcommSocket != null) {
                            try {
                                mInputStream.close();
                            } catch (Exception e) {
                                log.debug(e.getMessage());
                            }
                            try {
                                mOutputStream.close();
                            } catch (Exception e) {
                                log.debug(e.getMessage());
                            }
                            try {
                                mRfcommSocket.close();
                            } catch (Exception e) {
                                log.debug(e.getMessage());
                            }
                        }
                        mBus.post(new EventDanaRConnectionStatus(false, false, 0));
                    }
                }
            }
        };
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    public synchronized void connectIfNotConnected(String callerName) {
        mWakeLock.acquire();
        long startTime = System.currentTimeMillis();
        short connectionAttemptCount = 0;
        if (!(isConnected())) {
            long timeToConnectTimeSoFar = 0;
            while (!(isConnected())) {
                timeToConnectTimeSoFar = (System.currentTimeMillis() - startTime) / 1000;
                mBus.post(new EventDanaRConnectionStatus(true, false, connectionAttemptCount));
                connectBT();
                if (isConnected()) {
                    mBus.post(new EventDanaRConnectionStatus(false, true, 0));
                    break;
                }
                if (Config.logDanaBTComm)
                    log.debug("connectIfNotConnected waiting " + timeToConnectTimeSoFar + "s attempts:" + connectionAttemptCount + " caller:" + callerName);
                connectionAttemptCount++;

                if (timeToConnectTimeSoFar / 60 > 15 || connectionAttemptCount > 180) {
                    Intent alarmServiceIntent = new Intent(MainApp.instance().getApplicationContext(), AlertService.class);
                    alarmServiceIntent.putExtra("alarmText", MainApp.sResources.getString(R.string.connectionerror)); //TODO: hardcoded string
                    MainApp.instance().getApplicationContext().startService(alarmServiceIntent);
                }
                waitMsec(1000);
            }
            if (Config.logDanaBTComm)
                log.debug("connectIfNotConnected took " + timeToConnectTimeSoFar + "s attempts:" + connectionAttemptCount);
            pingStatus();
        } else {
            mBus.post(new EventDanaRConnectionStatus(false, true, 0));
        }
        mWakeLock.release();
    }

    private synchronized void connectBT() {
        if (mDevice == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.devicenotfound));
            return;
        }

        if (mRfcommSocket == null) {
            try {
                mRfcommSocket = mDevice.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                log.error("Error creating socket: ", e);
            }
            if (mRfcommSocket == null) {
                log.warn("connectBT() mRfcommSocket is null");
                return;
            }
        }
        if (!mRfcommSocket.isConnected()) {
            try {
                mRfcommSocket.connect();
                if (Config.logDanaBTComm)
                    log.debug("Connected");

                mOutputStream = mRfcommSocket.getOutputStream();
                mInputStream = mRfcommSocket.getInputStream();
                if (mSerialEngine != null) {
                    mSerialEngine.stopLoop();
                }
                mSerialEngine = new SerialEngine(this, mInputStream, mOutputStream, mRfcommSocket);
                mBus.post(new EventDanaRConnectionStatus(false, true, 0));

            } catch (IOException e) {
                log.warn("connectBT() ConnectionStatusEvent attempt failed: " + e.getMessage());
                mRfcommSocket = null;
            }
        }
    }

    public void scheduleDisconnection() {

        class DisconnectRunnable implements Runnable {
            public void run() {
                disconnect("scheduleDisconnection");
                scheduledDisconnection = null;
            }
        }

        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple disconnections
        if (scheduledDisconnection != null)
            scheduledDisconnection.cancel(false);
        Runnable task = new DisconnectRunnable();
        final int sec = 3;
        scheduledDisconnection = worker.schedule(task, sec, TimeUnit.SECONDS);
        //if (Config.logDanaBTComm)
        //    log.debug("Disconnection scheduled in " + sec + "secs");
    }

    public void disconnect(String from) {
        if (mRfcommSocket.isConnected()) {
            if (Config.logDanaBTComm)
                log.debug("Disconnecting " + from);
            try {
                mInputStream.close();
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
            try {
                mOutputStream.close();
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
            try {
                mRfcommSocket.close();
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
        } else {
            if (Config.logDanaBTComm)
                log.debug("Already disconnected " + from);
        }
    }

    public boolean isConnected() {
        return mRfcommSocket != null && mRfcommSocket.isConnected();
    }

    private void pingStatus() {
        try {
            MsgStatus statusMsg = new MsgStatus();
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();


            mSerialEngine.sendMessage(tempStatusMsg); // do this before statusBasic because here is temp duration
            mSerialEngine.sendMessage(exStatusMsg);
            mSerialEngine.sendMessage(statusMsg);
            mSerialEngine.sendMessage(statusBasicMsg);

            if (danaRPump.isNewPump) {
                mSerialEngine.sendMessage(new MsgSettingShippingInfo());
                mSerialEngine.sendMessage(new MsgCheckValue());
            }

            if (!statusMsg.received) {
                mSerialEngine.sendMessage(statusMsg);
            }
            if (!statusBasicMsg.received) {
                mSerialEngine.sendMessage(statusBasicMsg);
            }
            if (!tempStatusMsg.received) {
                // Load of status of current basal rate failed, give one more try
                mSerialEngine.sendMessage(tempStatusMsg);
            }
            if (!exStatusMsg.received) {
                // Load of status of current extended bolus failed, give one more try
                mSerialEngine.sendMessage(exStatusMsg);
            }

            // Check we have really current status of pump
            if (!statusMsg.received || !statusBasicMsg.received || !tempStatusMsg.received || !exStatusMsg.received) {
                waitMsec(10 * 1000);
                log.debug("pingStatus failed");
                connectIfNotConnected("pingStatus fail");
                pingStatus();
                return;
            }

            Date now = new Date();
            if (danaRPump.lastSettingsRead.getTime() + 60 * 60 * 1000L < now.getTime()) {
                mSerialEngine.sendMessage(new MsgSettingShippingInfo());
                mSerialEngine.sendMessage(new MsgSettingActiveProfile());
                //0x3203
                mSerialEngine.sendMessage(new MsgSettingBasal());
                //0x3201
                mSerialEngine.sendMessage(new MsgSettingMaxValues());
                mSerialEngine.sendMessage(new MsgSettingGlucose());
                mSerialEngine.sendMessage(new MsgSettingPumpTime());
                mSerialEngine.sendMessage(new MsgSettingActiveProfile());
                mSerialEngine.sendMessage(new MsgSettingProfileRatios());
                danaRPump.lastSettingsRead = now;
            }

            danaRPump.lastConnection = now;
            mBus.post(new EventDanaRNewStatus());
        } catch (Exception e) {
            log.error("err", e);
        }
    }

    public void tempBasal(int percent, int durationInHours) {
        mSerialEngine.sendMessage(new MsgSetTempBasalStart(percent, durationInHours));
        mSerialEngine.sendMessage(new MsgStatusTempBasal());
    }

    public void tempBasalStop() {
        mSerialEngine.sendMessage(new MsgSetTempBasalStop());
        mSerialEngine.sendMessage(new MsgStatusTempBasal());
    }

    public void extendedBolus(double insulin, int durationInHalfHours) {
        mSerialEngine.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialEngine.sendMessage(new MsgStatusBolusExtended());
    }

    public void extendedBolusStop() {
        mSerialEngine.sendMessage(new MsgSetExtendedBolusStop());
        mSerialEngine.sendMessage(new MsgStatusBolusExtended());
    }

    public void stop() {
        try {
            mInputStream.close();
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
        try {
            mOutputStream.close();
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
        try {
            mRfcommSocket.close();
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
        if (mSerialEngine != null) mSerialEngine.stopLoop();
    }

    public void bolus(Double amount, Treatment t) {
        bolusingTreatment = t;
        MsgBolusStart start = new MsgBolusStart(amount);
        MsgBolusProgress progress = new MsgBolusProgress(MainApp.bus(), amount, t);
        MsgBolusStop stop = new MsgBolusStop(MainApp.bus(), amount, t);

        mSerialEngine.sendMessage(start);
        while (!stop.stopped && !start.failed) {
            waitMsec(100);
        }
        bolusingTreatment = null;
        pingStatus();
    }

    public void bolusStop() {
        Treatment lastBolusingTreatment = bolusingTreatment;
        if (Config.logDanaBTComm)
            log.debug("bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        MsgBolusStop stop = new MsgBolusStop();
        stop.forced = true;
        mSerialEngine.sendMessage(stop);
        while (!stop.stopped) {
            mSerialEngine.sendMessage(stop);
            waitMsec(200);
        }
        // and update ns status to last amount
        waitMsec(60000);
        EventDanaRBolusProgress be = EventDanaRBolusProgress.getInstance();
        be.sStatus = "";
        mBus.post(be);
    }

    public void carbsEntry(int amount) {
        Calendar time = Calendar.getInstance();
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(time, amount);
        mSerialEngine.sendMessage(msg);
        //pingStatus();
    }

    public void updateBasalsInPump(final NSProfile profile) {
        double[] basal = buildDanaRProfileRecord(profile);
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
        mSerialEngine.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
        mSerialEngine.sendMessage(msgActivate);
        pingStatus();
    }

    public double[] buildDanaRProfileRecord(NSProfile nsProfile) {
        double[] record = new double[24];
        for (Integer hour = 0; hour < 24; hour++) {
            double value = nsProfile.getBasal(hour * 60 * 60);
            if (Config.logDanaMessageDetail)
                log.debug("NS basal value for " + hour + ":00 is " + value);
            record[hour] = value;
        }
        return record;
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange pch) {
        getSelectedPump();
        createRfCommSocket();
    }

    private void getSelectedPump() {
        devName = SP.getString("danar_bt_name", "");
    }

    public void waitMsec(long msecs) {
        try {
            Thread.sleep(msecs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
