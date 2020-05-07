package info.nightscout.androidaps.plugins.pump.danaRS.services;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import javax.inject.Inject;

import dagger.android.DaggerService;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.dialogs.BolusProgressDialog;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRSMessageHashTable;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_APS_Basal_Set_Temporary_Basal;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_APS_History_Events;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_APS_Set_Event_History;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Get_Basal_Rate;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Get_Profile_Number;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Get_Temporary_Basal_State;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Set_Profile_Basal_Rate;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Set_Profile_Number;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Basal_Set_Temporary_Basal;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Get_Bolus_Option;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Get_CIR_CF_Array;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Get_Calculation_Information;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Get_Extended_Bolus_State;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Get_Step_Bolus_Information;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Set_Extended_Bolus;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Start;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Stop;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_General_Get_Pump_Check;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_General_Get_Shipping_Information;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_General_Initial_Screen_Information;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_General_Set_History_Upload_Mode;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Alarm;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Basal;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Blood_Glucose;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Bolus;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Carbohydrate;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Daily;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Prime;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Refill;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_History_Suspend;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Get_Pump_Time;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Get_User_Option;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Set_Pump_Time;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DanaRSService extends DaggerService {
    @Inject HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject ProfileFunction profileFunction;
    @Inject CommandQueueProvider commandQueue;
    @Inject Context context;
    @Inject DanaRSPlugin danaRSPlugin;
    @Inject DanaRPump danaRPump;
    @Inject DanaRSMessageHashTable danaRSMessageHashTable;
    @Inject ActivePluginProvider activePlugin;
    @Inject ConstraintChecker constraintChecker;
    @Inject DetailedBolusInfoStorage detailedBolusInfoStorage;
    @Inject DateUtil dateUtil;

    private CompositeDisposable disposable = new CompositeDisposable();

    private BLEComm bleComm;

    private IBinder mBinder = new LocalBinder();

    private Treatment bolusingTreatment = null;

    private long lastHistoryFetched = 0;
    private long lastApproachingDailyLimit = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        bleComm = new BLEComm(this, danaRSMessageHashTable, danaRPump);
        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "EventAppExit received");
                    stopSelf();
                }, exception -> FabricPrivacy.getInstance().logException(exception))
        );
    }

    @Override
    public void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }

    public boolean isConnected() {
        return bleComm.isConnected;
    }

    public boolean isConnecting() {
        return bleComm.isConnecting;
    }

    public boolean connect(String from, String address, Object confirmConnect) {
        return bleComm.connect(from, address, confirmConnect);
    }

    public void stopConnecting() {
        bleComm.stopConnecting();
    }

    public void disconnect(String from) {
        bleComm.disconnect(from);
    }

    @SuppressWarnings("unused")
    public void sendMessage(DanaRS_Packet message) {
        bleComm.sendMessage(message);
    }

    public void getPumpStatus() {
        try {
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpstatus)));

            bleComm.sendMessage(new DanaRS_Packet_General_Initial_Screen_Information(aapsLogger, danaRPump));
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingextendedbolusstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump));
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingbolusstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information(aapsLogger, danaRPump, dateUtil)); // last bolus, bolusStep, maxBolus
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingtempbasalstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump, dateUtil));

            danaRPump.setLastConnection(System.currentTimeMillis());

            Profile profile = profileFunction.getProfile();
            PumpInterface pump = activePlugin.getActivePump();
            if (profile != null && Math.abs(danaRPump.getCurrentBasal() - profile.getBasal()) >= pump.getPumpDescription().basalStep) {
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)));
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump)); // basal profile, basalStep, maxBasal
                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(new EventProfileNeedsUpdate());
                }
            }

            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumptime)));
            bleComm.sendMessage(new DanaRS_Packet_Option_Get_Pump_Time(aapsLogger, danaRPump, dateUtil));

            long timeDiff = (danaRPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
            if (danaRPump.getPumpTime() == 0) {
                // initial handshake was not successfull
                // deinitialize pump
                danaRPump.setLastConnection(0);
                rxBus.send(new EventDanaRNewStatus());
                rxBus.send(new EventInitializationChanged());
                return;
            }
            long now = System.currentTimeMillis();
            if (danaRPump.getLastSettingsRead() + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)));
                bleComm.sendMessage(new DanaRS_Packet_General_Get_Shipping_Information(aapsLogger, danaRPump, dateUtil)); // serial no
                bleComm.sendMessage(new DanaRS_Packet_General_Get_Pump_Check(aapsLogger, danaRPump, rxBus, resourceHelper)); // firmware
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Profile_Number(aapsLogger, danaRPump));
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Bolus_Option(aapsLogger, rxBus, resourceHelper, danaRPump)); // isExtendedEnabled
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump)); // basal profile, basalStep, maxBasal
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Calculation_Information(aapsLogger, danaRPump)); // target
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_CIR_CF_Array(aapsLogger, danaRPump));
                bleComm.sendMessage(new DanaRS_Packet_Option_Get_User_Option(aapsLogger, danaRPump)); // Getting user options
                danaRPump.setLastSettingsRead(now);
            }

            aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: " + timeDiff + " seconds");
            if (Math.abs(timeDiff) > 3) {
                if (Math.abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: " + timeDiff + " seconds - large difference");
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    Intent i = new Intent(context, ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.error);
                    i.putExtra("status", resourceHelper.gs(R.string.largetimediff));
                    i.putExtra("title", resourceHelper.gs(R.string.largetimedifftitle));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);

                    //deinitialize pump
                    danaRPump.setLastConnection(0);
                    rxBus.send(new EventDanaRNewStatus());
                    rxBus.send(new EventInitializationChanged());
                    return;
                } else {
                    if (danaRPump.getProtocol() >= 6) {
                        bleComm.sendMessage(new DanaRS_Packet_Option_Set_Pump_Time(aapsLogger, dateUtil, DateUtil.now()));
                    } else {
                        waitForWholeMinute(); // Dana can set only whole minute
                        // add 10sec to be sure we are over minute (will be cutted off anyway)
                        bleComm.sendMessage(new DanaRS_Packet_Option_Set_Pump_Time(aapsLogger, dateUtil, DateUtil.now() + T.secs(10).msecs()));
                    }
                    bleComm.sendMessage(new DanaRS_Packet_Option_Get_Pump_Time(aapsLogger, danaRPump, dateUtil));
                    timeDiff = (danaRPump.getPumpTime() - System.currentTimeMillis()) / 1000L;
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: " + timeDiff + " seconds");
                }
            }

            loadEvents();

            rxBus.send(new EventDanaRNewStatus());
            rxBus.send(new EventInitializationChanged());
            //NSUpload.uploadDeviceStatus();
            if (danaRPump.getDailyTotalUnits() > danaRPump.getMaxDailyTotalUnits() * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMPCOMM, "Approaching daily limit: " + danaRPump.getDailyTotalUnits() + "/" + danaRPump.getMaxDailyTotalUnits());
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, resourceHelper.gs(R.string.approachingdailylimit), Notification.URGENT);
                    rxBus.send(new EventNewNotification(reportFail));
                    NSUpload.uploadError(resourceHelper.gs(R.string.approachingdailylimit) + ": " + danaRPump.getDailyTotalUnits() + "/" + danaRPump.getMaxDailyTotalUnits() + "U");
                    lastApproachingDailyLimit = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception", e);
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump status loaded");
    }

    public PumpEnactResult loadEvents() {

        if (!danaRSPlugin.isInitialized()) {
            PumpEnactResult result = new PumpEnactResult(injector).success(false);
            result.comment = "pump not initialized";
            return result;
        }

        SystemClock.sleep(1000);

        DanaRS_Packet_APS_History_Events msg;
        if (lastHistoryFetched == 0) {
            msg = new DanaRS_Packet_APS_History_Events(aapsLogger, rxBus, resourceHelper, activePlugin, danaRSPlugin, detailedBolusInfoStorage, injector, dateUtil, 0);
            aapsLogger.debug(LTag.PUMPCOMM, "Loading complete event history");
        } else {
            msg = new DanaRS_Packet_APS_History_Events(aapsLogger, rxBus, resourceHelper, activePlugin, danaRSPlugin, detailedBolusInfoStorage, injector, dateUtil, lastHistoryFetched);
            aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(lastHistoryFetched));
        }
        bleComm.sendMessage(msg);
        while (!danaRSPlugin.apsHistoryDone && bleComm.isConnected()) {
            SystemClock.sleep(100);
        }
        if (danaRSPlugin.lastEventTimeLoaded != 0)
            lastHistoryFetched = danaRSPlugin.lastEventTimeLoaded - T.mins(1).msecs();
        else
            lastHistoryFetched = 0;
        aapsLogger.debug(LTag.PUMPCOMM, "Events loaded");
        danaRPump.setLastConnection(System.currentTimeMillis());
        return new PumpEnactResult(injector).success(true);
    }


    public PumpEnactResult setUserSettings() {
        bleComm.sendMessage(new DanaRS_Packet_Option_Get_User_Option(aapsLogger, danaRPump));
        return new PumpEnactResult(injector).success(true);
    }


    public boolean bolus(final double insulin, int carbs, long carbtime, Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.startingbolus)));
        bolusingTreatment = t;
        final int preferencesSpeed = sp.getInt(R.string.key_danars_bolusspeed, 0);
        danaRSPlugin.bolusingTreatment = t;
        danaRSPlugin.bolusAmountToBeDelivered = insulin;
        danaRSPlugin.bolusStopped = false;
        danaRSPlugin.bolusStopForced = false;
        danaRSPlugin.bolusProgressLastTimeStamp = DateUtil.now();

        DanaRS_Packet_Bolus_Set_Step_Bolus_Start start = new DanaRS_Packet_Bolus_Set_Step_Bolus_Start(aapsLogger, danaRSPlugin, constraintChecker, insulin, preferencesSpeed);
        if (carbs > 0) {
//            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbtime, carbs); ####
//            bleComm.sendMessage(msg);
            DanaRS_Packet_APS_Set_Event_History msgSetHistoryEntry_v2 = new DanaRS_Packet_APS_Set_Event_History(aapsLogger, dateUtil, DanaRPump.CARBS, carbtime, carbs, 0);
            bleComm.sendMessage(msgSetHistoryEntry_v2);
            lastHistoryFetched = Math.min(lastHistoryFetched, carbtime - T.mins(1).msecs());
        }

        final long bolusStart = System.currentTimeMillis();
        if (insulin > 0) {
            if (!danaRSPlugin.bolusStopped) {
                bleComm.sendMessage(start);
            } else {
                t.insulin = 0d;
                return false;
            }

            while (!danaRSPlugin.bolusStopped && !start.failed && !danaRSPlugin.bolusDone) {
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - danaRSPlugin.bolusProgressLastTimeStamp) > 15 * 1000L) { // if i didn't receive status for more than 20 sec expecting broken comm
                    danaRSPlugin.bolusStopped = true;
                    danaRSPlugin.bolusStopForced = true;
                    aapsLogger.debug(LTag.PUMPCOMM, "Communication stopped");
                }
            }
        }

        final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
        bolusingEvent.setT(t);
        bolusingEvent.setPercent(99);

        bolusingTreatment = null;
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
        long bolusDurationInMSec = (long) (insulin * speed * 1000);
        long expectedEnd = bolusStart + bolusDurationInMSec + 2000;
        while (System.currentTimeMillis() < expectedEnd) {
            long waitTime = expectedEnd - System.currentTimeMillis();
            bolusingEvent.setStatus(String.format(resourceHelper.gs(R.string.waitingforestimatedbolusend), waitTime / 1000));
            rxBus.send(bolusingEvent);
            SystemClock.sleep(1000);
        }
        // do not call loadEvents() directly, reconnection may be needed
        commandQueue.loadEvents(new Callback() {
            @Override
            public void run() {
                // reread bolus status
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.gettingbolusstatus)));
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information(aapsLogger, danaRPump, dateUtil)); // last bolus
                bolusingEvent.setPercent(100);
                rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.disconnecting)));
            }
        });
        return !start.failed;
    }

    public void bolusStop() {
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        DanaRS_Packet_Bolus_Set_Step_Bolus_Stop stop = new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(aapsLogger, rxBus, resourceHelper, danaRSPlugin);
        danaRSPlugin.bolusStopForced = true;
        if (isConnected()) {
            bleComm.sendMessage(stop);
            while (!danaRSPlugin.bolusStopped) {
                bleComm.sendMessage(stop);
                SystemClock.sleep(200);
            }
        } else {
            danaRSPlugin.bolusStopped = true;
        }
    }

    public boolean tempBasal(Integer percent, int durationInHours) {
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Temporary_Basal(aapsLogger, percent, durationInHours));
        SystemClock.sleep(200);
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump, dateUtil));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean highTempBasal(Integer percent) {
        if (danaRPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(aapsLogger, percent));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump, dateUtil));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalShortDuration(Integer percent, int durationInMinutes) {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param");
            return false;
        }

        if (danaRPump.isTempBasalInProgress()) {
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger));
            SystemClock.sleep(500);
        }
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(aapsLogger, percent));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump, dateUtil));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump, dateUtil));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(Double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.settingextendedbolus)));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Set_Extended_Bolus(aapsLogger, insulin, durationInHalfHours));
        SystemClock.sleep(200);
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingextendedbolus)));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel(aapsLogger));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump));
        loadEvents();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean updateBasalsInPump(Profile profile) {
        if (!isConnected()) return false;
        rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.updatingbasalrates)));
        Double[] basal = danaRPump.buildDanaRProfileRecord(profile);
        DanaRS_Packet_Basal_Set_Profile_Basal_Rate msgSet = new DanaRS_Packet_Basal_Set_Profile_Basal_Rate(aapsLogger, 0, basal);
        bleComm.sendMessage(msgSet);
        DanaRS_Packet_Basal_Set_Profile_Number msgActivate = new DanaRS_Packet_Basal_Set_Profile_Number(aapsLogger, 0);
        bleComm.sendMessage(msgActivate);
        danaRPump.setLastSettingsRead(0); // force read full settings
        getPumpStatus();
        rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public PumpEnactResult loadHistory(byte type) {
        PumpEnactResult result = new PumpEnactResult(injector);
        if (!isConnected()) return result;
        DanaRS_Packet_History_ msg = null;
        switch (type) {
            case RecordTypes.RECORD_TYPE_ALARM:
                msg = new DanaRS_Packet_History_Alarm(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_PRIME:
                msg = new DanaRS_Packet_History_Prime(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_BASALHOUR:
                msg = new DanaRS_Packet_History_Basal(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_BOLUS:
                msg = new DanaRS_Packet_History_Bolus(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_CARBO:
                msg = new DanaRS_Packet_History_Carbohydrate(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_DAILY:
                msg = new DanaRS_Packet_History_Daily(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_GLUCOSE:
                msg = new DanaRS_Packet_History_Blood_Glucose(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_REFILL:
                msg = new DanaRS_Packet_History_Refill(aapsLogger, rxBus, dateUtil);
                break;
            case RecordTypes.RECORD_TYPE_SUSPEND:
                msg = new DanaRS_Packet_History_Suspend(aapsLogger, rxBus, dateUtil);
                break;
        }
        if (msg != null) {
            bleComm.sendMessage(new DanaRS_Packet_General_Set_History_Upload_Mode(aapsLogger, 1));
            SystemClock.sleep(200);
            bleComm.sendMessage(msg);
            while (!msg.getDone() && isConnected()) {
                SystemClock.sleep(100);
            }
            SystemClock.sleep(200);
            bleComm.sendMessage(new DanaRS_Packet_General_Set_History_Upload_Mode(aapsLogger, 0));
        }
        result.success = true;
        result.comment = "OK";
        return result;
    }


    public class LocalBinder extends Binder {
        public DanaRSService getServiceInstance() {
            return DanaRSService.this;
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    void waitForWholeMinute() {
        while (true) {
            long time = DateUtil.now();
            long timeToWholeMinute = (60000 - time % 60000);
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 300)
                break;
            rxBus.send(new EventPumpStatusChanged(resourceHelper.gs(R.string.waitingfortimesynchronization, (int) (timeToWholeMinute / 1000))));
            SystemClock.sleep(Math.min(timeToWholeMinute, 100));
        }
    }
}
