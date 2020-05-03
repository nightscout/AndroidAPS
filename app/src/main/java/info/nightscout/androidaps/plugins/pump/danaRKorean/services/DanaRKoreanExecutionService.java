package info.nightscout.androidaps.plugins.pump.danaRKorean.services;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Date;

import javax.inject.Inject;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.SerialIOThread;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStart;
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
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MessageHashTableRKorean;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MsgCheckValue_k;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MsgSettingBasal_k;
import info.nightscout.androidaps.plugins.pump.danaRKorean.comm.MsgStatusBasic_k;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DanaRKoreanExecutionService extends AbstractDanaRExecutionService {
    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject ResourceHelper resourceHelper;
    @Inject ConstraintChecker constraintChecker;
    @Inject DanaRPump danaRPump;
    @Inject DanaRPlugin danaRPlugin;
    @Inject DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject ConfigBuilderPlugin configBuilderPlugin;
    @Inject CommandQueueProvider commandQueue;
    @Inject Context context;
    @Inject MessageHashTableRKorean messageHashTableRKorean;
    @Inject ActivePluginProvider activePlugin;
    @Inject ProfileFunction profileFunction;

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
                mSerialIOThread = new SerialIOThread(mRfcommSocket, messageHashTableRKorean, danaRPump);
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
            MsgStatusBasic_k statusBasicMsg = new MsgStatusBasic_k(aapsLogger, danaRPump);
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal(aapsLogger, danaRPump, activePlugin, injector);
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended(injector, aapsLogger, danaRPump, activePlugin);
            MsgCheckValue_k checkValue = new MsgCheckValue_k(aapsLogger, danaRPump, danaRKoreanPlugin);

            if (danaRPump.isNewPump()) {
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
            danaRPump.setLastConnection(now);

            Profile profile = profileFunction.getProfile();
            if (profile != null && Math.abs(danaRPump.getCurrentBasal() - profile.getBasal()) >= danaRKoreanPlugin.getPumpDescription().basalStep) {
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal(aapsLogger, danaRPump, danaRPlugin));
                if (!danaRKoreanPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(new EventProfileNeedsUpdate());
                }
            }

            if (danaRPump.getLastSettingsRead() + 60 * 60 * 1000L < now || !danaRKoreanPlugin.isInitialized()) {
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo(aapsLogger, danaRPump));
                mSerialIOThread.sendMessage(new MsgSettingMeal(aapsLogger, rxBus, resourceHelper, danaRPump, danaRKoreanPlugin));
                mSerialIOThread.sendMessage(new MsgSettingBasal_k(aapsLogger, danaRPump, danaRKoreanPlugin));
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues(aapsLogger, danaRPump));
                mSerialIOThread.sendMessage(new MsgSettingGlucose(aapsLogger, danaRPump));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios(aapsLogger, danaRPump));
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime(aapsLogger, danaRPump));
                if (danaRPump.getPumpTime() == 0) {
                    // initial handshake was not successfull
                    // deinitialize pump
                    danaRPump.setLastConnection(0);
                    danaRPump.setLastSettingsRead(0);
                    rxBus.send(new EventDanaRNewStatus());
                    rxBus.send(new EventInitializationChanged());
                    return;
                }
                long timeDiff = (danaRPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    waitForWholeMinute(); // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cutted off anyway)
                    mSerialIOThread.sendMessage(new MsgSetTime(aapsLogger, new Date(DateUtil.now() + T.secs(10).msecs())));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime(aapsLogger, danaRPump));
                    timeDiff = (danaRPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.setLastSettingsRead(now);
            }

            rxBus.send(new EventDanaRNewStatus());
            rxBus.send(new EventInitializationChanged());
            //NSUpload.uploadDeviceStatus();
            if (danaRPump.getDailyTotalUnits() > danaRPump.getMaxDailyTotalUnits() * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMP, "Approaching daily limit: " + danaRPump.getDailyTotalUnits() + "/" + danaRPump.getMaxDailyTotalUnits());
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, resourceHelper.gs(R.string.approachingdailylimit), Notification.URGENT);
                    rxBus.send(new EventNewNotification(reportFail));
                    NSUpload.uploadError(resourceHelper.gs(R.string.approachingdailylimit) + ": " + danaRPump.getDailyTotalUnits() + "/" + danaRPump.getMaxDailyTotalUnits() + "U");
                    lastApproachingDailyLimit = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public boolean tempBasal(int percent, int durationInHours) {
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop(aapsLogger));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(aapsLogger, percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(aapsLogger, danaRPump, activePlugin, injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop(aapsLogger));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(aapsLogger, danaRPump, activePlugin, injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(aapsLogger, constraintChecker, insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector, aapsLogger, danaRPump, activePlugin));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop(aapsLogger));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector, aapsLogger, danaRPump, activePlugin));
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

        mBolusingTreatment = t;
        MsgBolusStart start = new MsgBolusStart(aapsLogger, constraintChecker, danaRPump, amount);
        danaRPump.setBolusStopped(false);
        danaRPump.setBolusStopForced(false);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(aapsLogger, carbtime, carbs));
        }

        if (amount > 0) {
            danaRPump.setBolusingTreatment(t);
            danaRPump.setBolusAmountToBeDelivered(amount);

            if (!danaRPump.getBolusStopped()) {
                mSerialIOThread.sendMessage(start);
            } else {
                t.insulin = 0d;
                return false;
            }
            while (!danaRPump.getBolusStopped() && !start.failed) {
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - danaRPump.getBolusProgressLastTimeStamp()) > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    danaRPump.setBolusStopped(true);
                    danaRPump.setBolusStopForced(true);
                    aapsLogger.debug(LTag.PUMP, "Communication stopped");
                }
            }
            SystemClock.sleep(300);

            mBolusingTreatment = null;
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
        Double[] basal = danaRPump.buildDanaRProfileRecord(profile);
        MsgSetSingleBasalProfile msgSet = new MsgSetSingleBasalProfile(aapsLogger, rxBus, resourceHelper, basal);
        mSerialIOThread.sendMessage(msgSet);
        danaRPump.setLastSettingsRead(0); // force read full settings
        getPumpStatus();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return null;
    }

}
