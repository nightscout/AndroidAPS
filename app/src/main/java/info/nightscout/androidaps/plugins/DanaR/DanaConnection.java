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

        this.mHandler = new Handler(mHandlerThread.getLooper());

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

        if (isConnected()) {
            mBus.post(new EventDanaRConnectionStatus(false, true, 0));
            pingStatus();
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
        scheduledDisconnection = worker.schedule(task, 3, TimeUnit.SECONDS);
    }

    public void disconnect(String from) {
        if (mRfcommSocket.isConnected()) {
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
            if (Config.logDanaBTComm)
                log.debug("Disconnecting " + from);
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


            mSerialEngine.sendMessage(statusMsg);
            mSerialEngine.sendMessage(statusBasicMsg);
            mSerialEngine.sendMessage(tempStatusMsg);
            mSerialEngine.sendMessage(exStatusMsg);

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
/* TODO
            statusEvent.timeLastSync = statusEvent.time;

            if (statusEvent.tempBasalInProgress) {
                try {

                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();

                    TempBasal tempBasal = new TempBasal();
                    tempBasal.duration = statusEvent.tempBasalTotalSec / 60;
                    tempBasal.percent = statusEvent.tempBasalRatio;
                    tempBasal.timeStart = statusEvent.tempBasalStart;
                    tempBasal.timeEnd = null;
                    tempBasal.baseRatio = (int) (statusEvent.currentBasal * 100);
                    tempBasal.tempRatio = (int) (statusEvent.currentBasal * 100 * statusEvent.tempBasalRatio / 100d);
                    tempBasal.isExtended = false;
//                   log.debug("TempBasal in progress record "+tempBasal);
                    daoTempBasals.createOrUpdate(tempBasal);
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                try {
                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();
                    TempBasal tempBasalLast = getTempBasalLast(daoTempBasals, false);
                    if (tempBasalLast != null) {
//                        log.debug("tempBasalLast " + tempBasalLast);
                        if (tempBasalLast.timeEnd == null || tempBasalLast.timeEnd.getTime() > new Date().getTime()) {
                            tempBasalLast.timeEnd = new Date();
                            if (tempBasalLast.timeEnd.getTime() > tempBasalLast.getPlannedTimeEnd().getTime()) {
                                tempBasalLast.timeEnd = tempBasalLast.getPlannedTimeEnd();
                            }
//                            log.debug("tempBasalLast updated to " + tempBasalLast);
                            daoTempBasals.update(tempBasalLast);
                        }
                    }
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }

            if (statusEvent.isExtendedInProgress) {
                try {

                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();

                    TempBasal tempBasal = new TempBasal();
                    tempBasal.duration = statusEvent.extendedBolusMinutes;
                    tempBasal.percent = (int) ((statusEvent.extendedBolusAbsoluteRate + statusEvent.currentBasal) / statusEvent.currentBasal * 100);
                    tempBasal.timeStart = statusEvent.extendedBolusStart;
                    tempBasal.timeEnd = null;
                    tempBasal.baseRatio = (int) (statusEvent.currentBasal * 100);
                    tempBasal.tempRatio = (int) (statusEvent.extendedBolusAbsoluteRate * 100 + statusEvent.currentBasal * 100);
                    tempBasal.isExtended = true;
//                    log.debug("TempBasal Extended in progress record "+tempBasal);
                    daoTempBasals.createOrUpdate(tempBasal);
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                try {
                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();
                    TempBasal tempBasalLast = getTempBasalLast(daoTempBasals, true);
                    if (tempBasalLast != null) {
                        log.debug("tempBasalLast Extended " + tempBasalLast);
                        if (tempBasalLast.timeEnd == null || tempBasalLast.timeEnd.getTime() > new Date().getTime()) {
                            tempBasalLast.timeEnd = new Date();
                            if (tempBasalLast.timeEnd.getTime() > tempBasalLast.getPlannedTimeEnd().getTime()) {
                                tempBasalLast.timeEnd = tempBasalLast.getPlannedTimeEnd();
                            }
//                            log.debug("tempBasalLast Extended updated to " + tempBasalLast);
                            daoTempBasals.update(tempBasalLast);
                        }
                    }
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }

            synchronized (this) {
                this.notify();
            }
            mBus.post(statusEvent);
*/
        } catch (Exception e) {
            log.error("err", e);
        }
    }

    public void tempBasal(int percent, int durationInHours) {
        MsgSetTempBasalStart msg = new MsgSetTempBasalStart(percent, durationInHours);
        mSerialEngine.sendMessage(msg);
        pingStatus();
    }

    public void tempBasalStop() {
        MsgSetTempBasalStop msg = new MsgSetTempBasalStop();
        mSerialEngine.sendMessage(msg);
        pingStatus();
    }

    public void extendedBolus(double rate, int durationInHalfHours) {
        MsgSetExtendedBolusStart msgStartExt = new MsgSetExtendedBolusStart(rate / 2 * durationInHalfHours, (byte) (durationInHalfHours & 0xFF));
        mSerialEngine.sendMessage(msgStartExt);
        pingStatus();
    }

    public void extendedBolusStop() {
        MsgSetExtendedBolusStop msg = new MsgSetExtendedBolusStop();
        mSerialEngine.sendMessage(msg);
        pingStatus();
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
        connectIfNotConnected("updateBasalsInPump");
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
