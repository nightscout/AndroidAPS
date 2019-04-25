package info.nightscout.androidaps.plugins.pump.danaRKorean.services;

import android.bluetooth.BluetoothDevice;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStop;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetSingleBasalProfile;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetTime;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingBasal;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingGlucose;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingMeal;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.pump.danaR.services.AbstractDanaRExecutionService;
import info.nightscout.androidaps.plugins.pump.danaRKorean.SerialIOThread;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MsgCheckValue_k;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MsgSettingBasal_k;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MsgStatusBasic_k;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

public class DanaRKoreanExecutionService extends AbstractDanaRExecutionService {

    public DanaRKoreanExecutionService() {
        mBinder = new LocalBinder();

        registerBus();
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    public class LocalBinder extends Binder {
        public DanaRKoreanExecutionService getServiceInstance() {
            return DanaRKoreanExecutionService.this;
        }
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
        if (L.isEnabled(L.PUMP))
            log.debug("EventAppExit received");

        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("Application exit");

        MainApp.instance().getApplicationContext().unregisterReceiver(receiver);

        stopSelf();
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange pch) {
        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("EventPreferenceChange");
    }

    public void connect() {
        if (mConnectionInProgress)
            return;

        new Thread(() -> {
            mHandshakeInProgress = false;
            mConnectionInProgress = true;
            getBTSocketForSelectedPump();
            if (mRfcommSocket == null || mBTDevice == null) {
                mConnectionInProgress = false;
                return; // Device not found
            }

            try {
                mRfcommSocket.connect();
            } catch (IOException e) {
                //log.error("Unhandled exception", e);
                if (e.getMessage().contains("socket closed")) {
                    log.error("Unhandled exception", e);
                }
            }

            if (isConnected()) {
                if (mSerialIOThread != null) {
                    mSerialIOThread.disconnect("Recreate SerialIOThread");
                }
                mSerialIOThread = new SerialIOThread(mRfcommSocket);
                mHandshakeInProgress = true;
                MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.HANDSHAKING, 0));
            }

            mConnectionInProgress = false;
        }).start();
    }

    public void getPumpStatus() {
        DanaRPump danaRPump = DanaRPump.getInstance();
        try {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpstatus)));
            //MsgStatus_k statusMsg = new MsgStatus_k();
            MsgStatusBasic_k statusBasicMsg = new MsgStatusBasic_k();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();
            MsgCheckValue_k checkValue = new MsgCheckValue_k();

            if (danaRPump.isNewPump) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
            }

            //mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));

            long now = System.currentTimeMillis();
            danaRPump.lastConnection = now;

            Profile profile = ProfileFunctions.getInstance().getProfile();
            PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
            if (profile != null && Math.abs(danaRPump.currentBasal - profile.getBasal()) >= pump.getPumpDescription().basalStep) {
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                if (!pump.isThisProfileSet(profile) && !ConfigBuilderPlugin.getPlugin().getCommandQueue().isRunning(Command.CommandType.BASALPROFILE)) {
                    MainApp.bus().post(new EventProfileNeedsUpdate());
                }
            }

            if (danaRPump.lastSettingsRead + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgSettingMeal());
                mSerialIOThread.sendMessage(new MsgSettingBasal_k());
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues());
                mSerialIOThread.sendMessage(new MsgSettingGlucose());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios());
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                long timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
                if (L.isEnabled(L.PUMP))
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    waitForWholeMinute(); // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cutted off anyway)
                    mSerialIOThread.sendMessage(new MsgSetTime(new Date(DateUtil.now() + T.secs(10).msecs())));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                    timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
                    if (L.isEnabled(L.PUMP))
                        log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.lastSettingsRead = now;
            }

            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits);
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.gs(R.string.approachingdailylimit), Notification.URGENT);
                    MainApp.bus().post(new EventNewNotification(reportFail));
                    NSUpload.uploadError(MainApp.gs(R.string.approachingdailylimit) + ": " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits + "U");
                    lastApproachingDailyLimit = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public boolean tempBasal(int percent, int durationInHours) {
        DanaRPump danaRPump = DanaRPump.getInstance();
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            SystemClock.sleep(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop());
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult loadEvents() {
        return null;
    }

    public boolean bolus(double amount, int carbs, long carbtime, final Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        mBolusingTreatment = t;
        MsgBolusStart start = new MsgBolusStart(amount);
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(carbtime, carbs));
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
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - progress.lastReceive) > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    stop.stopped = true;
                    stop.forced = true;
                    if (L.isEnabled(L.PUMP))
                        log.debug("Communication stopped");
                }
            }
            SystemClock.sleep(300);

            mBolusingTreatment = null;
            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("bolusOK", null);
        }

        return !start.failed;
    }

    public boolean carbsEntry(int amount) {
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(System.currentTimeMillis(), amount);
        mSerialIOThread.sendMessage(msg);
        return true;
    }

    @Override
    public boolean highTempBasal(int percent) {
        return false;
    }

    @Override
    public boolean tempBasalShortDuration(int percent, int durationInMinutes) {
        return false;
    }

    public boolean updateBasalsInPump(final Profile profile) {
        DanaRPump danaRPump = DanaRPump.getInstance();
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.getInstance().buildDanaRProfileRecord(profile);
        MsgSetSingleBasalProfile msgSet = new MsgSetSingleBasalProfile(basal);
        mSerialIOThread.sendMessage(msgSet);
        danaRPump.lastSettingsRead = 0; // force read full settings
        getPumpStatus();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return null;
    }

}
