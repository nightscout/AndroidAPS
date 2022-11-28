package info.nightscout.androidaps.danaRKorean.services;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.SystemClock;

import java.io.IOException;

import javax.inject.Inject;

import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.danaRKorean.comm.MessageHashTableRKorean;
import info.nightscout.androidaps.danaRKorean.comm.MsgCheckValue_k;
import info.nightscout.androidaps.danaRKorean.comm.MsgSettingBasal_k;
import info.nightscout.androidaps.danaRKorean.comm.MsgStatusBasic_k;
import info.nightscout.androidaps.danar.DanaRPlugin;
import info.nightscout.androidaps.danar.R;
import info.nightscout.androidaps.danar.SerialIOThread;
import info.nightscout.androidaps.danar.comm.MsgBolusStart;
import info.nightscout.androidaps.danar.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.danar.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.danar.comm.MsgSetSingleBasalProfile;
import info.nightscout.androidaps.danar.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.danar.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.danar.comm.MsgSetTime;
import info.nightscout.androidaps.danar.comm.MsgSettingBasal;
import info.nightscout.androidaps.danar.comm.MsgSettingGlucose;
import info.nightscout.androidaps.danar.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.danar.comm.MsgSettingMeal;
import info.nightscout.androidaps.danar.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.danar.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.danar.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.danar.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.danar.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.danar.services.AbstractDanaRExecutionService;
import info.nightscout.interfaces.Constants;
import info.nightscout.interfaces.constraints.Constraints;
import info.nightscout.interfaces.notifications.Notification;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.profile.ProfileFunction;
import info.nightscout.interfaces.pump.BolusProgressData;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.defs.PumpType;
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
import info.nightscout.shared.utils.DateUtil;
import info.nightscout.shared.utils.T;

public class DanaRKoreanExecutionService extends AbstractDanaRExecutionService {
    @Inject AAPSLogger aapsLogger;
    @Inject RxBus rxBus;
    @Inject ResourceHelper rh;
    @Inject Constraints constraintChecker;
    @Inject DanaPump danaPump;
    @Inject DanaRPlugin danaRPlugin;
    @Inject DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject CommandQueue commandQueue;
    @Inject MessageHashTableRKorean messageHashTableRKorean;
    @Inject UiInteraction uiInteraction;
    @Inject ProfileFunction profileFunction;
    @Inject PumpSync pumpSync;
    @Inject DateUtil dateUtil;


    public DanaRKoreanExecutionService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new LocalBinder();
    }

    public class LocalBinder extends Binder {
        public DanaRKoreanExecutionService getServiceInstance() {
            return DanaRKoreanExecutionService.this;
        }
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
                mSerialIOThread = new SerialIOThread(aapsLogger, mRfcommSocket, messageHashTableRKorean, danaPump);
                mHandshakeInProgress = true;
                rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, 0));
            }

            mConnectionInProgress = false;
        }).start();
    }

    public void getPumpStatus() {
        try {
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)));
            //MsgStatus_k statusMsg = new MsgStatus_k();
            MsgStatusBasic_k statusBasicMsg = new MsgStatusBasic_k(injector);
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal(injector);
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended(injector);
            MsgCheckValue_k checkValue = new MsgCheckValue_k(injector);

            if (danaPump.isNewPump()) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.isReceived()) {
                    return;
                }
            }

            //mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)));

            long now = System.currentTimeMillis();
            danaPump.setLastConnection(now);

            Profile profile = profileFunction.getProfile();
            if (profile != null && Math.abs(danaPump.getCurrentBasal() - profile.getBasal()) >= danaRKoreanPlugin.getPumpDescription().getBasalStep()) {
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal(injector));
                if (!danaRKoreanPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(new EventProfileSwitchChanged());
                }
            }

            if (danaPump.getLastSettingsRead() + 60 * 60 * 1000L < now || !danaRKoreanPlugin.isInitialized()) {
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo(injector));
                mSerialIOThread.sendMessage(new MsgSettingMeal(injector));
                mSerialIOThread.sendMessage(new MsgSettingBasal_k(injector));
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues(injector));
                mSerialIOThread.sendMessage(new MsgSettingGlucose(injector));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios(injector));
                rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime(injector));
                if (danaPump.getPumpTime() == 0) {
                    // initial handshake was not successful
                    // deinitialize pump
                    danaPump.reset();
                    rxBus.send(new EventDanaRNewStatus());
                    rxBus.send(new EventInitializationChanged());
                    return;
                }
                long timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    waitForWholeMinute(); // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cut off anyway)
                    mSerialIOThread.sendMessage(new MsgSetTime(injector, dateUtil.now() + T.Companion.secs(10).msecs()));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime(injector));
                    timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
                }
                danaPump.setLastSettingsRead(now);
            }

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

            doSanityCheck();
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
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(injector, insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop(injector));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult loadEvents() {
        return null;
    }

    public boolean bolus(double amount, int carbs, long carbTimeStamp, final EventOverviewBolusProgress.Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressData.INSTANCE.getStopPressed()) return false;

        danaPump.setBolusingTreatment(t);
        danaPump.setBolusDone(false);
        MsgBolusStart start = new MsgBolusStart(injector, amount);
        danaPump.setBolusStopped(false);
        danaPump.setBolusStopForced(false);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(injector, carbTimeStamp, carbs));
        }

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
                    aapsLogger.debug(LTag.PUMP, "Communication stopped");
                }
            }
            SystemClock.sleep(300);

            danaPump.setBolusingTreatment(null);
            commandQueue.readStatus(rh.gs(R.string.bolus_ok), null);
        }

        return !start.getFailed();
    }

    public boolean carbsEntry(int amount) {
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(injector, System.currentTimeMillis(), amount);
        mSerialIOThread.sendMessage(msg);
        return true;
    }

    @Override
    public boolean highTempBasal(int percent, int durationInMinutes) {
        return false;
    }

    @Override
    public boolean tempBasalShortDuration(int percent, int durationInMinutes) {
        return false;
    }

    public boolean updateBasalsInPump(final Profile profile) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)));
        Double[] basal = danaPump.buildDanaRProfileRecord(profile);
        MsgSetSingleBasalProfile msgSet = new MsgSetSingleBasalProfile(injector, basal);
        mSerialIOThread.sendMessage(msgSet);
        danaPump.setLastSettingsRead(0); // force read full settings
        getPumpStatus();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return null;
    }

}
