package info.nightscout.androidaps.danar.services;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.SystemClock;

import java.io.IOException;

import javax.inject.Inject;

import app.aaps.core.interfaces.configuration.Constants;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.notifications.Notification;
import app.aaps.core.interfaces.profile.Profile;
import app.aaps.core.interfaces.profile.ProfileFunction;
import app.aaps.core.interfaces.pump.BolusProgressData;
import app.aaps.core.interfaces.pump.PumpEnactResult;
import app.aaps.core.interfaces.pump.defs.PumpType;
import app.aaps.core.interfaces.queue.Callback;
import app.aaps.core.interfaces.queue.Command;
import app.aaps.core.interfaces.queue.CommandQueue;
import app.aaps.core.interfaces.rx.events.EventInitializationChanged;
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress;
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged;
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged;
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.danar.DanaRPlugin;
import info.nightscout.androidaps.danar.SerialIOThread;
import info.nightscout.androidaps.danar.comm.MessageBase;
import info.nightscout.androidaps.danar.comm.MessageHashTableR;
import info.nightscout.androidaps.danar.comm.MsgBolusStart;
import info.nightscout.androidaps.danar.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.danar.comm.MsgCheckValue;
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
import info.nightscout.pump.dana.events.EventDanaRNewStatus;

public class DanaRExecutionService extends AbstractDanaRExecutionService {
    @Inject DanaRPlugin danaRPlugin;
    @Inject DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject CommandQueue commandQueue;
    @Inject MessageHashTableR messageHashTableR;
    @Inject ProfileFunction profileFunction;

    public DanaRExecutionService() {
        // non params constructor must exist
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new LocalBinder();
    }

    public class LocalBinder extends Binder {
        public DanaRExecutionService getServiceInstance() {
            return DanaRExecutionService.this;
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
                if (e.getMessage().contains("socket closed")) {
                    aapsLogger.error("Unhandled exception", e);
                }
            }

            if (isConnected()) {
                if (mSerialIOThread != null) {
                    mSerialIOThread.disconnect("Recreate SerialIOThread");
                }
                mSerialIOThread = new SerialIOThread(aapsLogger, mRfcommSocket, messageHashTableR, danaPump);
                mHandshakeInProgress = true;
                rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, 0));
            }

            mConnectionInProgress = false;
        }).start();
    }

    public void getPumpStatus() {
        try {
            rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpstatus)));
            MsgStatus statusMsg = new MsgStatus(injector);
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic(injector);
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal(injector);
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended(injector);
            MsgCheckValue checkValue = new MsgCheckValue(injector);

            if (danaPump.isNewPump()) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.isReceived()) {
                    return;
                }
            }

            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingbolusstatus)));

            long now = System.currentTimeMillis();
            danaPump.setLastConnection(now);

            Profile profile = profileFunction.getProfile();
            if (profile != null && Math.abs(danaPump.getCurrentBasal() - profile.getBasal()) >= danaRPlugin.getPumpDescription().getBasalStep()) {
                rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingBasal(injector));
                if (!danaRPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(new EventProfileSwitchChanged());
                }
            }

            if (danaPump.getLastSettingsRead() + 60 * 60 * 1000L < now || !danaRPlugin.isInitialized()) {
                rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo(injector));
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile(injector));
                mSerialIOThread.sendMessage(new MsgSettingMeal(injector));
                mSerialIOThread.sendMessage(new MsgSettingBasal(injector));
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues(injector));
                mSerialIOThread.sendMessage(new MsgSettingGlucose(injector));
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile(injector));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios(injector));
                mSerialIOThread.sendMessage(new MsgSettingProfileRatiosAll(injector));
                mSerialIOThread.sendMessage(new MsgSettingUserOptions(injector));
                rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumptime)));
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
                    mSerialIOThread.sendMessage(new MsgSetTime(injector, dateUtil.now()));
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
                    uiInteraction.addNotification(Notification.APPROACHING_DAILY_LIMIT, rh.gs(info.nightscout.pump.dana.R.string.approachingdailylimit), Notification.URGENT);
                    pumpSync.insertAnnouncement(rh.gs(info.nightscout.pump.dana.R.string.approachingdailylimit) + ": " + danaPump.getDailyTotalUnits() + "/" + danaPump.getMaxDailyTotalUnits() + "U", null, PumpType.DANA_R_KOREAN, danaRKoreanPlugin.serialNumber());
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
            rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(injector, percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop(injector));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal(injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(injector, insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended(injector));
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingextendedbolus)));
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
        int preferencesSpeed = sp.getInt(info.nightscout.pump.dana.R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (preferencesSpeed == 0)
            start = new MsgBolusStart(injector, amount);
        else
            start = new MsgBolusStartWithSpeed(injector, amount, preferencesSpeed);
        danaPump.setBolusStopped(false);
        danaPump.setBolusStopForced(false);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(injector, carbTimeStamp, carbs));
        }

        if (amount > 0) {
            danaPump.setBolusingTreatment(t);
            danaPump.setBolusAmountToBeDelivered(amount);
            long bolusStart = System.currentTimeMillis();

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

            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
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
            // try to find real amount if bolusing was interrupted or comm failed
            if (t.getInsulin() != amount) {
                disconnect("bolusingInterrupted");
                long bolusDurationInMSec = (long) (amount * speed * 1000);
                long expectedEnd = bolusStart + bolusDurationInMSec + 3000;

                while (System.currentTimeMillis() < expectedEnd) {
                    long waitTime = expectedEnd - System.currentTimeMillis();
                    bolusingEvent.setStatus(String.format(rh.gs(info.nightscout.pump.dana.R.string.waitingforestimatedbolusend), waitTime / 1000));
                    rxBus.send(bolusingEvent);
                    SystemClock.sleep(1000);
                }

                final Object o = new Object();
                synchronized (o) {
                    commandQueue.independentConnect("bolusingInterrupted", new Callback() {
                        @Override
                        public void run() {
                            if (danaPump.getLastBolusTime() > System.currentTimeMillis() - 60 * 1000L) { // last bolus max 1 min old
                                t.setInsulin(danaPump.getLastBolusAmount());
                                aapsLogger.debug(LTag.PUMP, "Used bolus amount from history: " + danaPump.getLastBolusAmount());
                            } else {
                                aapsLogger.debug(LTag.PUMP, "Bolus amount in history too old: " + dateUtil.dateAndTimeString(danaPump.getLastBolusTime()));
                            }
                            synchronized (o) {
                                o.notifyAll();
                            }
                        }
                    });
                    try {
                        o.wait();
                    } catch (InterruptedException e) {
                        aapsLogger.error("Unhandled exception", e);
                    }
                }
            } else {
                commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.bolus_ok), null);
            }
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
        rxBus.send(new EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.updatingbasalrates)));
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
