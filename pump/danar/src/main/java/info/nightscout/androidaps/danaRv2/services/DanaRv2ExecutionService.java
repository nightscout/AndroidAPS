package info.nightscout.androidaps.danaRv2.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Binder;
import android.os.SystemClock;

import java.io.IOException;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.danaRv2.comm.MessageHashTableRv2;
import info.nightscout.androidaps.danaRv2.comm.MsgCheckValue_v2;
import info.nightscout.androidaps.danaRv2.comm.MsgHistoryEventsV2;
import info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2;
import info.nightscout.androidaps.danaRv2.comm.MsgSetHistoryEntry_v2;
import info.nightscout.androidaps.danar.R;
import info.nightscout.androidaps.danar.SerialIOThread;
import info.nightscout.androidaps.danar.comm.MessageBase;
import info.nightscout.androidaps.danar.comm.MsgBolusStart;
import info.nightscout.androidaps.danar.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.danar.comm.MsgSetActivateBasalProfile;
import info.nightscout.androidaps.danar.comm.MsgSetBasalProfile;
import info.nightscout.androidaps.danar.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.danar.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.danar.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.danar.comm.MsgSetTime;
import info.nightscout.androidaps.danar.comm.MsgSetUserOptions;
import info.nightscout.androidaps.danar.comm.MsgSettingActiveProfile;
import info.nightscout.androidaps.danar.comm.MsgSettingBasal;
import info.nightscout.androidaps.danar.comm.MsgSettingGlucose;
import info.nightscout.androidaps.danar.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.danar.comm.MsgSettingMeal;
import info.nightscout.androidaps.danar.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.danar.comm.MsgSettingProfileRatiosAll;
import info.nightscout.androidaps.danar.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.danar.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.danar.comm.MsgSettingUserOptions;
import info.nightscout.androidaps.danar.comm.MsgStatus;
import info.nightscout.androidaps.danar.comm.MsgStatusBasic;
import info.nightscout.androidaps.danar.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.danar.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.danar.services.AbstractDanaRExecutionService;
import info.nightscout.interfaces.Constants;
import info.nightscout.interfaces.notifications.Notification;
import info.nightscout.interfaces.plugin.ActivePlugin;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.profile.ProfileFunction;
import info.nightscout.interfaces.pump.BolusProgressData;
import info.nightscout.interfaces.pump.Pump;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.interfaces.queue.Callback;
import info.nightscout.interfaces.queue.Command;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.interfaces.ui.UiInteraction;
import info.nightscout.pump.dana.DanaPump;
import info.nightscout.pump.dana.events.EventDanaRNewStatus;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.events.EventInitializationChanged;
import info.nightscout.rx.events.EventOverviewBolusProgress;
import info.nightscout.rx.events.EventProfileSwitchChanged;
import info.nightscout.rx.events.EventPumpStatusChanged;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.utils.DateUtil;
import info.nightscout.shared.utils.T;

public class DanaRv2ExecutionService extends AbstractDanaRExecutionService {
    @Inject HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject RxBus rxBus;
    @Inject ResourceHelper rh;
    @Inject DanaPump danaPump;
    @Inject DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject DanaRv2Plugin danaRv2Plugin;
    @Inject ActivePlugin activePlugin;
    @Inject CommandQueue commandQueue;
    @Inject Context context;
    @Inject MessageHashTableRv2 messageHashTableRv2;
    @Inject ProfileFunction profileFunction;
    @Inject PumpSync pumpSync;
    @Inject SP sp;
    @Inject DateUtil dateUtil;
    @Inject UiInteraction uiInteraction;

    public DanaRv2ExecutionService() {
    }

    public class LocalBinder extends Binder {
        public DanaRv2ExecutionService getServiceInstance() {
            return DanaRv2ExecutionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new LocalBinder();
    }

    @SuppressLint("MissingPermission") public void connect() {
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
                    aapsLogger.error("Unhandled exception", e);
                }
            }

            if (isConnected()) {
                if (mSerialIOThread != null) {
                    mSerialIOThread.disconnect("Recreate SerialIOThread");
                }
                mSerialIOThread = new SerialIOThread(aapsLogger, mRfcommSocket, messageHashTableRv2, danaPump);
                mHandshakeInProgress = true;
                rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, 0));
            }

            mConnectionInProgress = false;
        }).start();
    }

    public void getPumpStatus() {
        try {
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)));
            MsgStatus statusMsg = new MsgStatus(injector);
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic(injector);
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal(injector);
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended(injector);
            MsgCheckValue_v2 checkValue = new MsgCheckValue_v2(injector);

            if (danaPump.isNewPump()) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.isReceived()) {
                    return;
                }
            }

            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)));
            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);

            danaPump.setLastConnection(System.currentTimeMillis());

            Profile profile = profileFunction.getProfile();
            Pump pump = activePlugin.getActivePump();
            if (profile != null && Math.abs(danaPump.getCurrentBasal() - profile.getBasal()) >= pump.getPumpDescription().getBasalStep()) {
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal(injector));
                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(new EventProfileSwitchChanged());
                }
            }

            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)));
            mSerialIOThread.sendMessage(new MsgSettingPumpTime(injector));
            if (danaPump.getPumpTime() == 0) {
                // initial handshake was not successful
                // de-initialize pump
                danaPump.reset();
                rxBus.send(new EventDanaRNewStatus());
                rxBus.send(new EventInitializationChanged());
                return;
            }
            long timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
            aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
            if (Math.abs(timeDiff) > 3) {
                if (Math.abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds - large difference");
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), R.raw.error);

                    //deinitialize pump
                    danaPump.reset();
                    rxBus.send(new EventDanaRNewStatus());
                    rxBus.send(new EventInitializationChanged());
                    return;
                } else {
                    waitForWholeMinute(); // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cutted off anyway)
                    mSerialIOThread.sendMessage(new MsgSetTime(injector, dateUtil.now() + T.Companion.secs(10).msecs()));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime(injector));
                    timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
                }
            }

            long now = System.currentTimeMillis();
            if (danaPump.getLastSettingsRead() + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo(injector));
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile(injector));
                mSerialIOThread.sendMessage(new MsgSettingMeal(injector));
                mSerialIOThread.sendMessage(new MsgSettingBasal(injector));
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues(injector));
                mSerialIOThread.sendMessage(new MsgSettingGlucose(injector));
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile(injector));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios(injector));
                mSerialIOThread.sendMessage(new MsgSettingUserOptions(injector));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatiosAll(injector));
                danaPump.setLastSettingsRead(now);
            }

            loadEvents();

            rxBus.send(new EventDanaRNewStatus());
            rxBus.send(new EventInitializationChanged());
            if (danaPump.getDailyTotalUnits() > danaPump.getMaxDailyTotalUnits() * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMP, "Approaching daily limit: " + danaPump.getDailyTotalUnits() + "/" + danaPump.getMaxDailyTotalUnits());
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    uiInteraction.addNotification(Notification.APPROACHING_DAILY_LIMIT, rh.gs(R.string.approachingdailylimit), Notification.URGENT);
                    pumpSync.insertAnnouncement(rh.gs(R.string.approachingdailylimit) + ": " + danaPump.getDailyTotalUnits() + "/" + danaPump.getMaxDailyTotalUnits() + "U", null, PumpType.DANA_R_KOREAN, danaRKoreanPlugin.serialNumber());
                    lastApproachingDailyLimit = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public boolean tempBasal(int percent, int durationInHours) {
        if (!isConnected()) return false;
        if (danaPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(injector, percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean highTempBasal(int percent, int durationInMinutes) {
        if (!isConnected()) return false;
        if (danaPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetAPSTempBasalStart_v2(injector, percent, durationInMinutes == 15, durationInMinutes == 30));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalShortDuration(int percent, int durationInMinutes) {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error("Wrong duration param");
            return false;
        }

        if (!isConnected()) return false;
        if (danaPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetAPSTempBasalStart_v2(injector, percent, durationInMinutes == 15, durationInMinutes == 30));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(injector, insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop(injector));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean bolus(final double amount, int carbs, long carbtime, final EventOverviewBolusProgress.Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressData.INSTANCE.getStopPressed()) return false;

        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.startingbolus)));
        danaPump.setBolusingTreatment(t);
        danaPump.setBolusDone(false);
        final int preferencesSpeed = sp.getInt(R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (preferencesSpeed == 0)
            start = new MsgBolusStart(injector, amount);
        else
            start = new MsgBolusStartWithSpeed(injector, amount, preferencesSpeed);
        danaPump.setBolusStopped(false);
        danaPump.setBolusStopForced(false);

        if (carbs > 0) {
            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(injector, carbtime, carbs);
            mSerialIOThread.sendMessage(msg);
            MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(injector,
                    DanaPump.HistoryEntry.CARBS.getValue(), carbtime, carbs, 0);
            mSerialIOThread.sendMessage(msgSetHistoryEntry_v2);
            danaPump.lastHistoryFetched = Math.min(danaPump.lastHistoryFetched, carbtime - T.Companion.mins(1).msecs());
            if (!msgSetHistoryEntry_v2.isReceived() || msgSetHistoryEntry_v2.getFailed())
                uiInteraction.runAlarm(rh.gs(R.string.carbs_store_error), rh.gs(R.string.error), R.raw.boluserror);
        }

        final long bolusStart = System.currentTimeMillis();
        if (amount > 0) {
            danaPump.setBolusingTreatment(t);
            danaPump.setBolusAmountToBeDelivered(amount);

            if (!danaPump.getBolusStopped()) {
                mSerialIOThread.sendMessage(start);
            } else {
                t.setInsulin(0d);
                return false;
            }
            while (!danaPump.getBolusStopped() && !start.getFailed()) {
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - danaPump.getBolusProgressLastTimeStamp()) > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    danaPump.setBolusStopped(true);
                    danaPump.setBolusStopForced(true);
                    aapsLogger.error("Communication stopped");
                }
            }
        }

        final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
        bolusingEvent.setT(t);
        bolusingEvent.setPercent(99);

        danaPump.setBolusingTreatment(null);
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
            bolusingEvent.setStatus(String.format(rh.gs(R.string.waitingforestimatedbolusend), waitTime / 1000));
            rxBus.send(bolusingEvent);
            SystemClock.sleep(1000);
        }
        // do not call loadEvents() directly, reconnection may be needed
        commandQueue.loadEvents(new Callback() {
            @Override
            public void run() {
                // load last bolus status
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)));
                mSerialIOThread.sendMessage(new MsgStatus(injector));
                bolusingEvent.setPercent(100);
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.disconnecting)));
            }
        });
        return !start.getFailed();
    }

    public boolean carbsEntry(int amount, long time) {
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(injector, time, amount);
        mSerialIOThread.sendMessage(msg);
        MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(injector,
                DanaPump.HistoryEntry.CARBS.getValue(), time, amount, 0);
        mSerialIOThread.sendMessage(msgSetHistoryEntry_v2);
        danaPump.lastHistoryFetched = Math.min(danaPump.lastHistoryFetched, time - T.Companion.mins(1).msecs());
        return true;
    }

    public PumpEnactResult loadEvents() {
        if (!danaRv2Plugin.isInitialized()) {
            PumpEnactResult result = new PumpEnactResult(injector).success(false);
            result.comment("pump not initialized");
            return result;
        }


        if (!isConnected())
            return new PumpEnactResult(injector).success(false);
        SystemClock.sleep(300);
        MsgHistoryEventsV2 msg = new MsgHistoryEventsV2(injector, danaPump.lastHistoryFetched);
        aapsLogger.debug(LTag.PUMP, "Loading event history from: " + dateUtil.dateAndTimeString(danaPump.lastHistoryFetched));

        mSerialIOThread.sendMessage(msg);
        while (!danaPump.historyDoneReceived && mRfcommSocket.isConnected()) {
            SystemClock.sleep(100);
        }
        SystemClock.sleep(200);
        if (danaRv2Plugin.lastEventTimeLoaded != 0)
            danaPump.lastHistoryFetched = danaRv2Plugin.lastEventTimeLoaded - T.Companion.mins(1).msecs();
        else
            danaPump.lastHistoryFetched = 0;
        danaPump.setLastConnection(System.currentTimeMillis());
        return new PumpEnactResult(injector).success(true);
    }

    public boolean updateBasalsInPump(final Profile profile) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)));
        Double[] basal = danaPump.buildDanaRProfileRecord(profile);
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile(injector, (byte) 0, basal);
        mSerialIOThread.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile(injector, (byte) 0);
        mSerialIOThread.sendMessage(msgActivate);
        danaPump.setLastSettingsRead(0); // force read full settings
        getPumpStatus();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public PumpEnactResult setUserOptions() {
        if (!isConnected())
            return new PumpEnactResult(injector).success(false);
        SystemClock.sleep(300);
        MsgSetUserOptions msg = new MsgSetUserOptions(injector);
        mSerialIOThread.sendMessage(msg);
        SystemClock.sleep(200);
        return new PumpEnactResult(injector).success(!msg.getFailed());
    }

}
