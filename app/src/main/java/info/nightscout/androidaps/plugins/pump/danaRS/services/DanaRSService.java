package info.nightscout.androidaps.plugins.pump.danaRS.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
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
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
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
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Notify_Delivery_Complete;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Notify_Delivery_Rate_Display;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Get_Pump_Time;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Get_User_Option;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Set_Pump_Time;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Option_Set_User_Option;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DanaRSService extends Service {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private CompositeDisposable disposable = new CompositeDisposable();

    private BLEComm bleComm = BLEComm.getInstance(this);

    private IBinder mBinder = new LocalBinder();

    private Treatment bolusingTreatment = null;

    private long lastHistoryFetched = 0;
    private long lastApproachingDailyLimit = 0;

    public DanaRSService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (L.isEnabled(L.PUMP)) log.debug("EventAppExit received");
                    stopSelf();
                }, FabricPrivacy::logException)
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

    public void sendMessage(DanaRS_Packet message) {
        bleComm.sendMessage(message);
    }

    public void getPumpStatus() {
        DanaRPump danaRPump = DanaRPump.getInstance();
        try {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpstatus)));

            bleComm.sendMessage(new DanaRS_Packet_General_Initial_Screen_Information());
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingextendedbolusstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information()); // last bolus, bolusStep, maxBolus
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingtempbasalstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());

            danaRPump.lastConnection = System.currentTimeMillis();

            Profile profile = ProfileFunctions.getInstance().getProfile();
            PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
            if (profile != null && Math.abs(danaRPump.currentBasal - profile.getBasal()) >= pump.getPumpDescription().basalStep) {
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate()); // basal profile, basalStep, maxBasal
                if (!pump.isThisProfileSet(profile) && !ConfigBuilderPlugin.getPlugin().getCommandQueue().isRunning(Command.CommandType.BASALPROFILE)) {
                    RxBus.INSTANCE.send(new EventProfileNeedsUpdate());
                }
            }

            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumptime)));
            bleComm.sendMessage(new DanaRS_Packet_Option_Get_Pump_Time());

            long timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
            if (danaRPump.pumpTime == 0) {
                // initial handshake was not successfull
                // deinitialize pump
                danaRPump.lastConnection = 0;
                RxBus.INSTANCE.send(new EventDanaRNewStatus());
                RxBus.INSTANCE.send(new EventInitializationChanged());
                return;
            }
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Pump time difference: " + timeDiff + " seconds");
            if (Math.abs(timeDiff) > 3) {
                if (Math.abs(timeDiff) > 60 * 60 * 1.5) {
                    if (L.isEnabled(L.PUMPCOMM))
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
                    bleComm.sendMessage(new DanaRS_Packet_Option_Set_Pump_Time(new Date(DateUtil.now() + T.secs(10).msecs())));
                    bleComm.sendMessage(new DanaRS_Packet_Option_Get_Pump_Time());
                    timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L;
                    if (L.isEnabled(L.PUMPCOMM))
                        log.debug("Pump time difference: " + timeDiff + " seconds");
                }
            }

            long now = System.currentTimeMillis();
            if (danaRPump.lastSettingsRead + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                bleComm.sendMessage(new DanaRS_Packet_General_Get_Shipping_Information()); // serial no
                bleComm.sendMessage(new DanaRS_Packet_General_Get_Pump_Check()); // firmware
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Profile_Number());
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Bolus_Option()); // isExtendedEnabled
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate()); // basal profile, basalStep, maxBasal
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Calculation_Information()); // target
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_CIR_CF_Array());
                bleComm.sendMessage(new DanaRS_Packet_Option_Get_User_Option()); // Getting user options
                danaRPump.lastSettingsRead = now;
            }

            loadEvents();

            RxBus.INSTANCE.send(new EventDanaRNewStatus());
            RxBus.INSTANCE.send(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                if (L.isEnabled(L.PUMPCOMM))
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
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Pump status loaded");
    }

    public PumpEnactResult loadEvents() {

        if (!DanaRSPlugin.getPlugin().isInitialized()) {
            PumpEnactResult result = new PumpEnactResult().success(false);
            result.comment = "pump not initialized";
            return result;
        }

        SystemClock.sleep(1000);

        DanaRS_Packet_APS_History_Events msg;
        if (lastHistoryFetched == 0) {
            msg = new DanaRS_Packet_APS_History_Events(0);
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Loading complete event history");
        } else {
            msg = new DanaRS_Packet_APS_History_Events(lastHistoryFetched);
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Loading event history from: " + DateUtil.dateAndTimeFullString(lastHistoryFetched));
        }
        bleComm.sendMessage(msg);
        while (!msg.done && bleComm.isConnected()) {
            SystemClock.sleep(100);
        }
        if (DanaRS_Packet_APS_History_Events.lastEventTimeLoaded != 0)
            lastHistoryFetched = DanaRS_Packet_APS_History_Events.lastEventTimeLoaded - T.mins(1).msecs();
        else
            lastHistoryFetched = 0;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Events loaded");
        DanaRPump.getInstance().lastConnection = System.currentTimeMillis();
        return new PumpEnactResult().success(true);
    }


    public PumpEnactResult setUserSettings() {
        bleComm.sendMessage(new DanaRS_Packet_Option_Set_User_Option());
        bleComm.sendMessage(new DanaRS_Packet_Option_Get_User_Option());
        return new PumpEnactResult().success(true);
    }


    public boolean bolus(final double insulin, int carbs, long carbtime, Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.startingbolus)));
        bolusingTreatment = t;
        final int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        DanaRS_Packet_Bolus_Set_Step_Bolus_Start start = new DanaRS_Packet_Bolus_Set_Step_Bolus_Start(insulin, preferencesSpeed);
        DanaRS_Packet_Bolus_Set_Step_Bolus_Stop stop = new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(insulin, t); // initialize static variables
        DanaRS_Packet_Notify_Delivery_Complete complete = new DanaRS_Packet_Notify_Delivery_Complete(insulin, t); // initialize static variables

        if (carbs > 0) {
//            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbtime, carbs); ####
//            bleComm.sendMessage(msg);
            DanaRS_Packet_APS_Set_Event_History msgSetHistoryEntry_v2 = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, carbtime, carbs, 0);
            bleComm.sendMessage(msgSetHistoryEntry_v2);
            lastHistoryFetched = Math.min(lastHistoryFetched, carbtime - T.mins(1).msecs());
        }

        final long bolusStart = System.currentTimeMillis();
        if (insulin > 0) {
            if (!stop.stopped) {
                bleComm.sendMessage(start);
            } else {
                t.insulin = 0d;
                return false;
            }
            DanaRS_Packet_Notify_Delivery_Rate_Display progress = new DanaRS_Packet_Notify_Delivery_Rate_Display(insulin, t); // initialize static variables

            while (!stop.stopped && !start.failed && !complete.done) {
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - progress.lastReceive) > 15 * 1000L) { // if i didn't receive status for more than 20 sec expecting broken comm
                    stop.stopped = true;
                    stop.forced = true;
                    if (L.isEnabled(L.PUMPCOMM))
                        log.debug("Communication stopped");
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
            bolusingEvent.setStatus(String.format(MainApp.gs(R.string.waitingforestimatedbolusend), waitTime / 1000));
            RxBus.INSTANCE.send(bolusingEvent);
            SystemClock.sleep(1000);
        }
        // do not call loadEvents() directly, reconnection may be needed
        ConfigBuilderPlugin.getPlugin().getCommandQueue().loadEvents(new Callback() {
            @Override
            public void run() {
                // reread bolus status
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information()); // last bolus
                bolusingEvent.setPercent(100);
                RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.disconnecting)));
            }
        });
        return !start.failed;
    }

    public void bolusStop() {
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        DanaRS_Packet_Bolus_Set_Step_Bolus_Stop stop = new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop();
        stop.forced = true;
        if (isConnected()) {
            bleComm.sendMessage(stop);
            while (!stop.stopped) {
                bleComm.sendMessage(stop);
                SystemClock.sleep(200);
            }
        } else {
            stop.stopped = true;
        }
    }

    public boolean tempBasal(Integer percent, int durationInHours) {
        if (!isConnected()) return false;
        if (DanaRPump.getInstance().isTempBasalInProgress) {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            SystemClock.sleep(500);
        }
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Temporary_Basal(percent, durationInHours));
        SystemClock.sleep(200);
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean highTempBasal(Integer percent) {
        if (DanaRPump.getInstance().isTempBasalInProgress) {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            SystemClock.sleep(500);
        }
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(percent));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalShortDuration(Integer percent, int durationInMinutes) {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            log.error("Wrong duration param");
            return false;
        }

        if (DanaRPump.getInstance().isTempBasalInProgress) {
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            SystemClock.sleep(500);
        }
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(percent, durationInMinutes == 15, durationInMinutes == 30));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(Double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.settingextendedbolus)));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Set_Extended_Bolus(insulin, durationInHalfHours));
        SystemClock.sleep(200);
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingextendedbolus)));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel());
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
        loadEvents();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public boolean updateBasalsInPump(Profile profile) {
        if (!isConnected()) return false;
        RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.getInstance().buildDanaRProfileRecord(profile);
        DanaRS_Packet_Basal_Set_Profile_Basal_Rate msgSet = new DanaRS_Packet_Basal_Set_Profile_Basal_Rate(0, basal);
        bleComm.sendMessage(msgSet);
        DanaRS_Packet_Basal_Set_Profile_Number msgActivate = new DanaRS_Packet_Basal_Set_Profile_Number(0);
        bleComm.sendMessage(msgActivate);
        DanaRPump.getInstance().lastSettingsRead = 0; // force read full settings
        getPumpStatus();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true;
    }

    public PumpEnactResult loadHistory(byte type) {
        PumpEnactResult result = new PumpEnactResult();
        if (!isConnected()) return result;
        DanaRS_Packet_History_ msg = null;
        switch (type) {
            case RecordTypes.RECORD_TYPE_ALARM:
                msg = new DanaRS_Packet_History_Alarm();
                break;
            case RecordTypes.RECORD_TYPE_PRIME:
                msg = new DanaRS_Packet_History_Prime();
                break;
            case RecordTypes.RECORD_TYPE_BASALHOUR:
                msg = new DanaRS_Packet_History_Basal();
                break;
            case RecordTypes.RECORD_TYPE_BOLUS:
                msg = new DanaRS_Packet_History_Bolus();
                break;
            case RecordTypes.RECORD_TYPE_CARBO:
                msg = new DanaRS_Packet_History_Carbohydrate();
                break;
            case RecordTypes.RECORD_TYPE_DAILY:
                msg = new DanaRS_Packet_History_Daily();
                break;
            case RecordTypes.RECORD_TYPE_GLUCOSE:
                msg = new DanaRS_Packet_History_Blood_Glucose();
                break;
            case RecordTypes.RECORD_TYPE_REFILL:
                msg = new DanaRS_Packet_History_Refill();
                break;
            case RecordTypes.RECORD_TYPE_SUSPEND:
                msg = new DanaRS_Packet_History_Suspend();
                break;
        }
        if (msg != null) {
            bleComm.sendMessage(new DanaRS_Packet_General_Set_History_Upload_Mode(1));
            SystemClock.sleep(200);
            bleComm.sendMessage(msg);
            while (!msg.done && isConnected()) {
                SystemClock.sleep(100);
            }
            SystemClock.sleep(200);
            bleComm.sendMessage(new DanaRS_Packet_General_Set_History_Upload_Mode(0));
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
            RxBus.INSTANCE.send(new EventPumpStatusChanged(MainApp.gs(R.string.waitingfortimesynchronization, (int) (timeToWholeMinute / 1000))));
            SystemClock.sleep(Math.min(timeToWholeMinute, 100));
        }
    }
}
