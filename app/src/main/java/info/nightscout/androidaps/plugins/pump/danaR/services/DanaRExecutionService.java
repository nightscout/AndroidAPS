package info.nightscout.androidaps.plugins.pump.danaR.services;

import android.bluetooth.BluetoothDevice;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.SystemClock;

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
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.SerialIOThread;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageHashTableR;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStop;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgCheckValue;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetActivateBasalProfile;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetBasalProfile;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetTime;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSetUserOptions;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingActiveProfile;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingBasal;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingGlucose;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingMeal;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingProfileRatiosAll;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgSettingUserOptions;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgStatus;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgStatusBasic;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DanaRExecutionService extends AbstractDanaRExecutionService {
    private CompositeDisposable disposable = new CompositeDisposable();

    public DanaRExecutionService() {
        mBinder = new LocalBinder();

        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
     }

    @Override
    public void onCreate() {
        super.onCreate();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (mSerialIOThread != null)
                        mSerialIOThread.disconnect("EventPreferenceChange");
                }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (L.isEnabled(L.PUMP))
                        log.debug("EventAppExit received");

                    if (mSerialIOThread != null)
                        mSerialIOThread.disconnect("Application exit");
                    MainApp.instance().getApplicationContext().unregisterReceiver(receiver);
                    stopSelf();
                }, FabricPrivacy::logException)
        );
    }

    @Override
    public void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public DanaRExecutionService getServiceInstance() {
            return DanaRExecutionService.this;
        }
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
                mSerialIOThread = new SerialIOThread(mRfcommSocket, MessageHashTableR.INSTANCE);
                mHandshakeInProgress = true;
                RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, 0));
            }

            mConnectionInProgress = false;
        }).start();
    }

    public void getPumpStatus() {
        DanaRPump danaRPump = DanaRPump.getInstance();
        try {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpstatus)));
            MsgStatus statusMsg = new MsgStatus();
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();
            MsgCheckValue checkValue = new MsgCheckValue();

            if (danaRPump.isNewPump) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
            }

            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));

            long now = System.currentTimeMillis();
            danaRPump.lastConnection = now;

            Profile profile = ProfileFunctions.getInstance().getProfile();
            PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
            if (profile != null && Math.abs(danaRPump.currentBasal - profile.getBasal()) >= pump.getPumpDescription().basalStep) {
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                if (!pump.isThisProfileSet(profile) && !ConfigBuilderPlugin.getPlugin().getCommandQueue().isRunning(Command.CommandType.BASALPROFILE)) {
                    RxBus.INSTANCE.send(new EventProfileNeedsUpdate());
                }
            }

            if (danaRPump.lastSettingsRead + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
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
                mSerialIOThread.sendMessage(new MsgSettingUserOptions());
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                if (danaRPump.pumpTime == 0) {
                    // initial handshake was not successfull
                    // deinitialize pump
                    danaRPump.lastConnection = 0;
                    danaRPump.lastSettingsRead = 0;
                    RxBus.INSTANCE.send(new EventDanaRNewStatus());
                    RxBus.INSTANCE.send(new EventInitializationChanged());
                    return;
                }
                long timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
                if (L.isEnabled(L.PUMP))
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    mSerialIOThread.sendMessage(new MsgSetTime(new Date()));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                    timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
                    if (L.isEnabled(L.PUMP))
                        log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.lastSettingsRead = now;
            }

            RxBus.INSTANCE.send(new EventDanaRNewStatus());
            RxBus.INSTANCE.send(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits);
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.gs(R.string.approachingdailylimit), Notification.URGENT);
                    RxBus.INSTANCE.send(new EventNewNotification(reportFail));
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
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            SystemClock.sleep(500);
        }
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop());
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult loadEvents() {
        return null;
    }

    public boolean bolus(double amount, int carbs, long carbtime, final Treatment t) {
        DanaRPump danaRPump = DanaRPump.getInstance();
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        mBolusingTreatment = t;
        int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (preferencesSpeed == 0)
            start = new MsgBolusStart(amount);
        else
            start = new MsgBolusStartWithSpeed(amount, preferencesSpeed);
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(carbtime, carbs));
        }

        if (amount > 0) {
            MsgBolusProgress progress = new MsgBolusProgress(amount, t); // initialize static variables
            long bolusStart = System.currentTimeMillis();

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

            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
            bolusingEvent.setT(t);
            bolusingEvent.setPercent(99);

            mBolusingTreatment = null;

            int speed = 12;
            switch (preferencesSpeed) {
                case 0:
                    speed = 12;
                    break;
                case 1:
                    speed = 30;
                    break;
                case 2:
                    speed = 60;
                    break;
            }
            // try to find real amount if bolusing was interrupted or comm failed
            if (t.insulin != amount) {
                disconnect("bolusingInterrupted");
                long bolusDurationInMSec = (long) (amount * speed * 1000);
                long expectedEnd = bolusStart + bolusDurationInMSec + 3000;

                while (System.currentTimeMillis() < expectedEnd) {
                    long waitTime = expectedEnd - System.currentTimeMillis();
                    bolusingEvent.setStatus(String.format(MainApp.gs(R.string.waitingforestimatedbolusend), waitTime / 1000));
                    RxBus.INSTANCE.send(bolusingEvent);
                    SystemClock.sleep(1000);
                }

                final Object o = new Object();
                synchronized (o) {
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().independentConnect("bolusingInterrupted", new Callback() {
                        @Override
                        public void run() {
                            if (danaRPump.lastBolusTime > System.currentTimeMillis() - 60 * 1000L) { // last bolus max 1 min old
                                t.insulin = danaRPump.lastBolusAmount;
                                if (L.isEnabled(L.PUMP))
                                    log.debug("Used bolus amount from history: " + danaRPump.lastBolusAmount);
                            } else {
                                if (L.isEnabled(L.PUMP))
                                    log.debug("Bolus amount in history too old: " + DateUtil.dateAndTimeFullString(danaRPump.lastBolusTime));
                            }
                            synchronized (o) {
                                o.notify();
                            }
                        }
                    });
                    try {
                        o.wait();
                    } catch (InterruptedException e) {
                        log.error("Unhandled exception", e);
                    }
                }
            } else {
                ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("bolusOK", null);
            }
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
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.getInstance().buildDanaRProfileRecord(profile);
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
        mSerialIOThread.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
        mSerialIOThread.sendMessage(msgActivate);
        danaRPump.lastSettingsRead = 0; // force read full settings
        getPumpStatus();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public PumpEnactResult setUserOptions() {
        if (!isConnected())
            return new PumpEnactResult().success(false);
        SystemClock.sleep(300);
        MsgSetUserOptions msg = new MsgSetUserOptions();
        mSerialIOThread.sendMessage(msg);
        SystemClock.sleep(200);
        return new PumpEnactResult().success(!msg.failed);
    }
}
