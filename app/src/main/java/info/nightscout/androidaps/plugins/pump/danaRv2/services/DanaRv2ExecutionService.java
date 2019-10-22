package info.nightscout.androidaps.plugins.pump.danaRv2.services;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
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
import info.nightscout.androidaps.plugins.general.overview.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.SerialIOThread;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStop;
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
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.pump.danaR.services.AbstractDanaRExecutionService;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MessageHashTableRv2;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MsgCheckValue_v2;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MsgHistoryEvents_v2;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MsgSetAPSTempBasalStart_v2;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MsgSetHistoryEntry_v2;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MsgStatusBolusExtended_v2;
import info.nightscout.androidaps.plugins.pump.danaRv2.comm.MsgStatusTempBasal_v2;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DanaRv2ExecutionService extends AbstractDanaRExecutionService {
    private CompositeDisposable disposable = new CompositeDisposable();

    private long lastHistoryFetched = 0;

    public DanaRv2ExecutionService() {
        mBinder = new LocalBinder();

        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    public class LocalBinder extends Binder {
        public DanaRv2ExecutionService getServiceInstance() {
            return DanaRv2ExecutionService.this;
        }
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
                mSerialIOThread = new SerialIOThread(mRfcommSocket, MessageHashTableRv2.INSTANCE);
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
            MsgStatusTempBasal_v2 tempStatusMsg = new MsgStatusTempBasal_v2();
            MsgStatusBolusExtended_v2 exStatusMsg = new MsgStatusBolusExtended_v2();
            MsgCheckValue_v2 checkValue = new MsgCheckValue_v2();

            if (danaRPump.isNewPump) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
            }

            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));
            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);

            danaRPump.lastConnection = System.currentTimeMillis();

            Profile profile = ProfileFunctions.getInstance().getProfile();
            PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
            if (profile != null && Math.abs(danaRPump.currentBasal - profile.getBasal()) >= pump.getPumpDescription().basalStep) {
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                if (!pump.isThisProfileSet(profile) && !ConfigBuilderPlugin.getPlugin().getCommandQueue().isRunning(Command.CommandType.BASALPROFILE)) {
                    RxBus.INSTANCE.send(new EventProfileNeedsUpdate());
                }
            }

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
            if (Math.abs(timeDiff) > 3) {
                if (Math.abs(timeDiff) > 60 * 60 * 1.5) {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Pump time difference: " + timeDiff + " seconds - large difference");
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.error);
                    i.putExtra("status", MainApp.gs(R.string.largetimediff));
                    i.putExtra("title", MainApp.gs(R.string.largetimedifftitle));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainApp.instance().startActivity(i);

                    //deinitialize pump
                    danaRPump.lastConnection = 0;
                    RxBus.INSTANCE.send(new EventDanaRNewStatus());
                    RxBus.INSTANCE.send(new EventInitializationChanged());
                    return;
                } else {
                    waitForWholeMinute(); // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cutted off anyway)
                    mSerialIOThread.sendMessage(new MsgSetTime(new Date(DateUtil.now() + T.secs(10).msecs())));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                    timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
                    if (L.isEnabled(L.PUMP))
                        log.debug("Pump time difference: " + timeDiff + " seconds");
                }
            }

            long now = System.currentTimeMillis();
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
                mSerialIOThread.sendMessage(new MsgSettingUserOptions());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatiosAll());
                danaRPump.lastSettingsRead = now;
            }

            loadEvents();

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
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean highTempBasal(int percent) {
        DanaRPump danaRPump = DanaRPump.getInstance();
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress) {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            SystemClock.sleep(500);
        }
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetAPSTempBasalStart_v2(percent));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalShortDuration(int percent, int durationInMinutes) {
        DanaRPump danaRPump = DanaRPump.getInstance();
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            log.error("Wrong duration param");
            return false;
        }

        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress) {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            SystemClock.sleep(500);
        }
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetAPSTempBasalStart_v2(percent, durationInMinutes == 15, durationInMinutes == 30));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
        mSerialIOThread.sendMessage(new MsgStatusTempBasal_v2());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended_v2());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop());
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended_v2());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean bolus(final double amount, int carbs, long carbtime, final Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.startingbolus)));
        mBolusingTreatment = t;
        final int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (preferencesSpeed == 0)
            start = new MsgBolusStart(amount);
        else
            start = new MsgBolusStartWithSpeed(amount, preferencesSpeed);
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        if (carbs > 0) {
            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbtime, carbs);
            mSerialIOThread.sendMessage(msg);
            MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(DanaRPump.CARBS, carbtime, carbs, 0);
            mSerialIOThread.sendMessage(msgSetHistoryEntry_v2);
            lastHistoryFetched = Math.min(lastHistoryFetched, carbtime - T.mins(1).msecs());
        }

        final long bolusStart = System.currentTimeMillis();
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
                    log.error("Communication stopped");
                }
            }
        }

        final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
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
        long bolusDurationInMSec = (long) (amount * speed * 1000);
        long expectedEnd = bolusStart + bolusDurationInMSec + 2000;
        while (System.currentTimeMillis() < expectedEnd) {
            long waitTime = expectedEnd - System.currentTimeMillis();
            bolusingEvent.setStatus(String.format(MainApp.gs(R.string.waitingforestimatedbolusend), waitTime / 1000));
            RxBus.INSTANCE.send(bolusingEvent);
            SystemClock.sleep(1000);
        }
        // do not call loadEvents() directly, reconnection may be needed
        ConfigBuilderPlugin.getPlugin().getCommandQueue().loadEvents(new Callback() {
            @Override
            public void run() {
                // load last bolus status
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));
                mSerialIOThread.sendMessage(new MsgStatus());
                bolusingEvent.setPercent(100);
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.disconnecting)));
            }
        });
        return !start.failed;
    }

    public void bolusStop() {
        if (L.isEnabled(L.PUMP))
            log.debug("bolusStop >>>>> @ " + (mBolusingTreatment == null ? "" : mBolusingTreatment.insulin));
        MsgBolusStop stop = new MsgBolusStop();
        stop.forced = true;
        if (isConnected()) {
            mSerialIOThread.sendMessage(stop);
            while (!stop.stopped) {
                mSerialIOThread.sendMessage(stop);
                SystemClock.sleep(200);
            }
        } else {
            stop.stopped = true;
        }
    }

    public boolean carbsEntry(int amount, long time) {
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(time, amount);
        mSerialIOThread.sendMessage(msg);
        MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(DanaRPump.CARBS, time, amount, 0);
        mSerialIOThread.sendMessage(msgSetHistoryEntry_v2);
        lastHistoryFetched = Math.min(lastHistoryFetched, time - T.mins(1).msecs());
        return true;
    }

    public PumpEnactResult loadEvents() {
        DanaRPump danaRPump = DanaRPump.getInstance();

        if (!DanaRv2Plugin.getPlugin().isInitialized()) {
            PumpEnactResult result = new PumpEnactResult().success(false);
            result.comment = "pump not initialized";
            return result;
        }


        if (!isConnected())
            return new PumpEnactResult().success(false);
        SystemClock.sleep(300);
        MsgHistoryEvents_v2 msg = new MsgHistoryEvents_v2(lastHistoryFetched);
        if (L.isEnabled(L.PUMP))
            log.debug("Loading event history from: " + DateUtil.dateAndTimeFullString(lastHistoryFetched));

        mSerialIOThread.sendMessage(msg);
        while (!msg.done && mRfcommSocket.isConnected()) {
            SystemClock.sleep(100);
        }
        SystemClock.sleep(200);
        if (MsgHistoryEvents_v2.lastEventTimeLoaded != 0)
            lastHistoryFetched = MsgHistoryEvents_v2.lastEventTimeLoaded - T.mins(1).msecs();
        else
            lastHistoryFetched = 0;
        danaRPump.lastConnection = System.currentTimeMillis();
        return new PumpEnactResult().success(true);
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
