package info.nightscout.androidaps.danaRKorean.services;

import android.os.Binder;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Date;

import javax.inject.Inject;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.dana.DanaPump;
import info.nightscout.androidaps.dana.events.EventDanaRNewStatus;
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin;
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
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.danaRKorean.comm.MessageHashTableRKorean;
import info.nightscout.androidaps.danaRKorean.comm.MsgCheckValue_k;
import info.nightscout.androidaps.danaRKorean.comm.MsgSettingBasal_k;
import info.nightscout.androidaps.danaRKorean.comm.MsgStatusBasic_k;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class DanaRKoreanExecutionService extends AbstractDanaRExecutionService {
    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject ResourceHelper resourceHelper;
    @Inject ConstraintChecker constraintChecker;
    @Inject DanaPump danaPump;
    @Inject DanaRPlugin danaRPlugin;
    @Inject DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject CommandQueueProvider commandQueue;
    @Inject MessageHashTableRKorean messageHashTableRKorean;
    @Inject ActivePluginProvider activePlugin;
    @Inject ProfileFunction profileFunction;
    @Inject NSUpload nsUpload;
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
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpstatus)));
            //MsgStatus_k statusMsg = new MsgStatus_k();
            MsgStatusBasic_k statusBasicMsg = new MsgStatusBasic_k(aapsLogger, danaPump);
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal(aapsLogger, danaPump, activePlugin, injector);
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended(injector, aapsLogger, danaPump, activePlugin, dateUtil);
            MsgCheckValue_k checkValue = new MsgCheckValue_k(aapsLogger, danaPump, danaRKoreanPlugin);

            if (danaPump.isNewPump()) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
            }

            //mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingbolusstatus)));

            long now = System.currentTimeMillis();
            danaPump.setLastConnection(now);

            Profile profile = profileFunction.getProfile();
            if (profile != null && Math.abs(danaPump.getCurrentBasal() - profile.getBasal()) >= danaRKoreanPlugin.getPumpDescription().basalStep) {
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal(aapsLogger, danaPump, danaRPlugin));
                if (!danaRKoreanPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(new EventProfileNeedsUpdate());
                }
            }

            if (danaPump.getLastSettingsRead() + 60 * 60 * 1000L < now || !danaRKoreanPlugin.isInitialized()) {
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo(aapsLogger, danaPump));
                mSerialIOThread.sendMessage(new MsgSettingMeal(aapsLogger, rxBus, resourceHelper, danaPump, danaRKoreanPlugin));
                mSerialIOThread.sendMessage(new MsgSettingBasal_k(aapsLogger, danaPump, danaRKoreanPlugin));
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues(aapsLogger, danaPump));
                mSerialIOThread.sendMessage(new MsgSettingGlucose(aapsLogger, danaPump));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios(aapsLogger, danaPump));
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime(aapsLogger, danaPump, dateUtil));
                if (danaPump.getPumpTime() == 0) {
                    // initial handshake was not successfull
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
                    // add 10sec to be sure we are over minute (will be cutted off anyway)
                    mSerialIOThread.sendMessage(new MsgSetTime(aapsLogger, dateUtil, new Date(DateUtil.now() + T.secs(10).msecs())));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime(aapsLogger, danaPump, dateUtil));
                    timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
                }
                danaPump.setLastSettingsRead(now);
            }

            rxBus.send(new EventDanaRNewStatus());
            rxBus.send(new EventInitializationChanged());
            //NSUpload.uploadDeviceStatus();
            if (danaPump.getDailyTotalUnits() > danaPump.getMaxDailyTotalUnits() * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMP, "Approaching daily limit: " + danaPump.getDailyTotalUnits() + "/" + danaPump.getMaxDailyTotalUnits());
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, resourceHelper.gs(R.string.approachingdailylimit), Notification.URGENT);
                    rxBus.send(new EventNewNotification(reportFail));
                    nsUpload.uploadError(resourceHelper.gs(R.string.approachingdailylimit) + ": " + danaPump.getDailyTotalUnits() + "/" + danaPump.getMaxDailyTotalUnits() + "U");
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
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop(aapsLogger));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(aapsLogger, percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(aapsLogger, danaPump, activePlugin, injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop(aapsLogger));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(aapsLogger, danaPump, activePlugin, injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(aapsLogger, constraintChecker, insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector, aapsLogger, danaPump, activePlugin, dateUtil));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop(aapsLogger));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector, aapsLogger, danaPump, activePlugin, dateUtil));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult loadEvents() {
        return null;
    }

    public boolean bolus(double amount, int carbs, long carbtime, final Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        danaPump.setBolusingTreatment(t);
        danaPump.setBolusDone(false);
        MsgBolusStart start = new MsgBolusStart(aapsLogger, constraintChecker, danaPump, amount);
        danaPump.setBolusStopped(false);
        danaPump.setBolusStopForced(false);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(aapsLogger, carbtime, carbs));
        }

        if (amount > 0) {
            danaPump.setBolusingTreatment(t);
            danaPump.setBolusAmountToBeDelivered(amount);

            if (!danaPump.getBolusStopped()) {
                mSerialIOThread.sendMessage(start);
            } else {
                t.insulin = 0d;
                return false;
            }
            while (!danaPump.getBolusStopped() && !start.failed) {
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - danaPump.getBolusProgressLastTimeStamp()) > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    danaPump.setBolusStopped(true);
                    danaPump.setBolusStopForced(true);
                    aapsLogger.debug(LTag.PUMP, "Communication stopped");
                }
            }
            SystemClock.sleep(300);

            danaPump.setBolusingTreatment(null);
            commandQueue.readStatus("bolusOK", null);
        }

        return !start.failed;
    }

    public boolean carbsEntry(int amount) {
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(aapsLogger, System.currentTimeMillis(), amount);
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
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.updatingbasalrates)));
        Double[] basal = danaPump.buildDanaRProfileRecord(profile);
        MsgSetSingleBasalProfile msgSet = new MsgSetSingleBasalProfile(aapsLogger, rxBus, resourceHelper, basal);
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
