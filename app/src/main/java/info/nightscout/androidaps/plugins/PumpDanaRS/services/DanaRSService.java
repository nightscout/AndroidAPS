package info.nightscout.androidaps.plugins.PumpDanaRS.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_APS_Basal_Set_Temporary_Basal;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_APS_History_Events;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_APS_Set_Event_History;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Get_Basal_Rate;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Get_Profile_Number;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Get_Temporary_Basal_State;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Set_Profile_Basal_Rate;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Set_Profile_Number;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Set_Temporary_Basal;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Bolus_Option;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_CIR_CF_Array;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Calculation_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Extended_Bolus_State;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Step_Bolus_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Set_Extended_Bolus;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Start;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Stop;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_General_Get_Pump_Check;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_General_Get_Shipping_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_General_Initial_Screen_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_General_Set_History_Upload_Mode;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Alarm;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Basal;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Blood_Glucose;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Bolus;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Carbohydrate;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Daily;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Prime;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Refill;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_History_Suspend;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Notify_Delivery_Complete;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Notify_Delivery_Rate_Display;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Option_Get_Pump_Time;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Option_Set_Pump_Time;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.T;

public class DanaRSService extends Service {
    private static Logger log = LoggerFactory.getLogger(DanaRSService.class);

    private BLEComm bleComm = BLEComm.getInstance(this);

    private IBinder mBinder = new LocalBinder();

    private DanaRPump danaRPump = DanaRPump.getInstance();
    private Treatment bolusingTreatment = null;

    private long lastHistoryFetched = 0;

    public DanaRSService() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
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
        try {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpstatus)));

            bleComm.sendMessage(new DanaRS_Packet_General_Initial_Screen_Information());
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingextendedbolusstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information()); // last bolus, bolusStep, maxBolus
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingtempbasalstatus)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());

            long now = System.currentTimeMillis();
            if (danaRPump.lastSettingsRead + 60 * 60 * 1000L < now || !MainApp.getSpecificPlugin(DanaRSPlugin.class).isInitialized()) {
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumpsettings)));
                bleComm.sendMessage(new DanaRS_Packet_General_Get_Shipping_Information()); // serial no
                bleComm.sendMessage(new DanaRS_Packet_General_Get_Pump_Check()); // firmware
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Profile_Number());
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Bolus_Option()); // isExtendedEnabled
                bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate()); // basal profile, basalStep, maxBasal
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Calculation_Information()); // target
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_CIR_CF_Array());
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingpumptime)));
                bleComm.sendMessage(new DanaRS_Packet_Option_Get_Pump_Time());
                long timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 3) {
                    waitForWholeMinute(); // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cutted off anyway)
                    bleComm.sendMessage(new DanaRS_Packet_Option_Set_Pump_Time(new Date(DateUtil.now() + T.secs(10).msecs())));
                    bleComm.sendMessage(new DanaRS_Packet_Option_Get_Pump_Time());
                    timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.lastSettingsRead = now;
            }

            loadEvents();

            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                log.debug("Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits);
                Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.gs(R.string.approachingdailylimit), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(reportFail));
                NSUpload.uploadError(MainApp.gs(R.string.approachingdailylimit) + ": " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits + "U");
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        log.debug("Pump status loaded");
    }

    public PumpEnactResult loadEvents() {
        DanaRS_Packet_APS_History_Events msg;
        if (lastHistoryFetched == 0) {
            msg = new DanaRS_Packet_APS_History_Events(0);
            log.debug("Loading complete event history");
        } else {
            msg = new DanaRS_Packet_APS_History_Events(lastHistoryFetched);
            log.debug("Loading event history from: " + new Date(lastHistoryFetched).toLocaleString());
        }
        bleComm.sendMessage(msg);
        while (!msg.done && bleComm.isConnected()) {
            SystemClock.sleep(100);
        }
        if (DanaRS_Packet_APS_History_Events.lastEventTimeLoaded != 0)
            lastHistoryFetched = DanaRS_Packet_APS_History_Events.lastEventTimeLoaded - 45 * 60 * 1000L; // always load last 45 min
        else
            lastHistoryFetched = 0;
        log.debug("Events loaded");
        danaRPump.lastConnection = System.currentTimeMillis();
        return new PumpEnactResult().success(true);
    }


    public boolean bolus(final double insulin, int carbs, long carbtime, Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.startingbolus)));
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
            lastHistoryFetched = carbtime - 60000;
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
                    log.debug("Communication stopped");
                }
            }
        }

        final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.t = t;
        bolusingEvent.percent = 99;

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
            bolusingEvent.status = String.format(MainApp.gs(R.string.waitingforestimatedbolusend), waitTime / 1000);
            MainApp.bus().post(bolusingEvent);
            SystemClock.sleep(1000);
        }
        // do not call loadEvents() directly, reconnection may be needed
        ConfigBuilderPlugin.getCommandQueue().loadEvents(new Callback() {
            @Override
            public void run() {
                // reread bolus status
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.gettingbolusstatus)));
                bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information()); // last bolus
                bolusingEvent.percent = 100;
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.disconnecting)));
            }
        });
        return !start.failed;
    }

    public void bolusStop() {
        if (Config.logDanaBTComm)
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
        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            SystemClock.sleep(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Temporary_Basal(percent, durationInHours));
        SystemClock.sleep(200);
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean highTempBasal(Integer percent) {
        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            SystemClock.sleep(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(percent));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean tempBasalShortDuration(Integer percent, int durationInMinutes) {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            log.error("Wrong duration param");
            return false;
        }

        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
            bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            SystemClock.sleep(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.settingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(percent, durationInMinutes == 15, durationInMinutes == 30));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingtempbasal)));
        bleComm.sendMessage(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
        bleComm.sendMessage(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(Double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.settingextendedbolus)));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Set_Extended_Bolus(insulin, durationInHalfHours));
        SystemClock.sleep(200);
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.stoppingextendedbolus)));
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel());
        bleComm.sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
        loadEvents();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean updateBasalsInPump(Profile profile) {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.buildDanaRProfileRecord(profile);
        DanaRS_Packet_Basal_Set_Profile_Basal_Rate msgSet = new DanaRS_Packet_Basal_Set_Profile_Basal_Rate(0, basal);
        bleComm.sendMessage(msgSet);
        DanaRS_Packet_Basal_Set_Profile_Number msgActivate = new DanaRS_Packet_Basal_Set_Profile_Number(0);
        bleComm.sendMessage(msgActivate);
        danaRPump.lastSettingsRead = 0; // force read full settings
        getPumpStatus();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
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

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (Config.logFunctionCalls)
            log.debug("EventAppExit received");

        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("EventAppExit finished");
    }

    void waitForWholeMinute() {
        while (true) {
            long time = DateUtil.now();
            long timeToWholeMinute = (60000 - time % 60000);
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 300)
                break;
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.gs(R.string.waitingfortimesynchronization, (int)(timeToWholeMinute / 1000))));
            SystemClock.sleep(Math.min(timeToWholeMinute, 100));
        }
    }
}
