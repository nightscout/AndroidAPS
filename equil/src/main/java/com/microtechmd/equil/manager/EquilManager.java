package com.microtechmd.equil.manager;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.microtechmd.equil.EquilConst;
import com.microtechmd.equil.R;
import com.microtechmd.equil.ble.EquilBLE;
import com.microtechmd.equil.data.AlarmMode;
import com.microtechmd.equil.data.BolusProfile;
import com.microtechmd.equil.data.RunMode;
import com.microtechmd.equil.data.database.EquilBolusRecord;
import com.microtechmd.equil.data.database.EquilHistoryPump;
import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.data.database.EquilHistoryRecordDao;
import com.microtechmd.equil.data.database.EquilTempBasalRecord;
import com.microtechmd.equil.events.EventEquilDataChanged;
import com.microtechmd.equil.events.EventEquilInsulinChanged;
import com.microtechmd.equil.events.EventEquilModeChanged;
import com.microtechmd.equil.manager.action.EquilAction;
import com.microtechmd.equil.manager.command.BaseCmd;
import com.microtechmd.equil.manager.command.CmdBasalGet;
import com.microtechmd.equil.manager.command.CmdExtendedBolusSet;
import com.microtechmd.equil.manager.command.CmdHistoryGet;
import com.microtechmd.equil.manager.command.CmdLargeBasalSet;
import com.microtechmd.equil.manager.command.CmdTempBasalSet;
import com.microtechmd.equil.manager.command.PumpEvent;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.interfaces.ResourceHelper;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;

@Singleton
public class EquilManager {
    public static boolean is_debug = false;
    private final AAPSLogger aapsLogger;
    private final RxBus rxBus;
    private final ResourceHelper rh;
    private final HasAndroidInjector injector;
    private final SP sp;
    private final Context context;
    private final PumpSync pumpSync;
    private final AapsSchedulers aapsSchedulers;
    EquilBLE equilBLE;
    EquilHistoryRecordDao equilHistoryRecordDao;

    //    SettingProfile settingProfile;
    public AAPSLogger getAapsLogger() {
        return aapsLogger;
    }

    public SP getSp() {
        return sp;
    }

    @Inject
    public EquilManager(
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            RxBus rxBus,
            SP sp,
            ResourceHelper rh,
            HasAndroidInjector injector,
            Context context,
            PumpSync pumpSync, EquilBLE equilBLE,
            EquilHistoryRecordDao equilHistoryRecordDao) {
        this.aapsSchedulers = aapsSchedulers;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.sp = sp;
        this.rh = rh;
        this.injector = injector;
        this.context = context;
        this.pumpSync = pumpSync;
        this.equilBLE = equilBLE;
        this.equilHistoryRecordDao = equilHistoryRecordDao;
//        danaHistoryRecordDao.createOrUpdate(new DanaHistoryRecord(0,0x01));

        this.gsonInstance = createGson();
        loadPodState();
        initEquilError();
        equilBLE.init(this);
        equilBLE.startScan();
    }

    List<PumpEvent> listEvent;

    private void initEquilError() {
        listEvent = new ArrayList<>();

//        listEvent.add(new PumpEvent(4, 0, 0, ""));
        listEvent.add(new PumpEvent(4, 2, 2, rh.gs(R.string.equil_occlusion)));
        listEvent.add(new PumpEvent(4, 3, 0, rh.gs(R.string.equil_motor_reverse)));
        listEvent.add(new PumpEvent(4, 3, 2, rh.gs(R.string.equil_motor_fault)));
        listEvent.add(new PumpEvent(4, 6, 1, rh.gs(R.string.equil_shutdown_be)));
        listEvent.add(new PumpEvent(4, 6, 2, rh.gs(R.string.equil_shutdown)));
        listEvent.add(new PumpEvent(5, 1, 2, rh.gs(R.string.equil_insert_error)));

    }

    public String getEquilError(int port, int type, int level) {
        PumpEvent pumpEvent = new PumpEvent(port, type, level, "");
        int index = listEvent.indexOf(pumpEvent);
        if (index == -1) {
            return "";
        }
        return listEvent.get(index).getConent();
    }

    // Convenience method
    public <T> T executeAction(EquilAction<T> action) {
        return action.execute(this);
    }

    public PumpEnactResult closeBle() {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            equilBLE.disconnect();
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult readStatus() {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            equilBLE.getEquilStatus();
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult setTempBasal(double insulin, int time) {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            CmdTempBasalSet command = new CmdTempBasalSet(0, 0);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            if (command.isCmdStatus()) {
                SystemClock.sleep(500);
                command = new CmdTempBasalSet(insulin, time);
                command.setEquilManager(this);
                equilBLE.writeCmd(command);
                synchronized (command) {
                    command.wait(command.getTimeOut());
                }
                result.setSuccess(command.isCmdStatus());
                result.enacted(true);
            } else {
                result.setSuccess(false);
            }

        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult setExtendedBolus(double insulin, int time) {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            CmdExtendedBolusSet command = new CmdExtendedBolusSet(insulin, time);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            if (command.isCmdStatus()) {
                result.setSuccess(command.isCmdStatus());
                result.enacted(true);
            } else {
                result.setSuccess(false);
            }

        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }


    public PumpEnactResult bolus(DetailedBolusInfo detailedBolusInfo, BolusProfile bolusProfile) {
        EventOverviewBolusProgress progressUpdateEvent = EventOverviewBolusProgress.INSTANCE;
        progressUpdateEvent.setT(new EventOverviewBolusProgress.Treatment(HardLimits.MAX_IOB_LGS, 0,
                detailedBolusInfo.getBolusType() ==
                        DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.getId()));
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            CmdLargeBasalSet command = new CmdLargeBasalSet(detailedBolusInfo.insulin);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            bolusProfile.setStop(false);
            int sleep = command.getStepTime() / 20 * 200;
            sleep = 2000;
            float percent1 = (float) (5f / detailedBolusInfo.insulin);
            aapsLogger.debug(LTag.EQUILBLE, "sleep===" + detailedBolusInfo.insulin + "===" + percent1);
            float percent = 0;
            if (command.isCmdStatus()) {
                while (!bolusProfile.getStop() && percent < 100) {
                    progressUpdateEvent.setPercent((int) percent);
                    progressUpdateEvent.setStatus(this.rh.gs(R.string.equil_bolus_delivered,
                            Double.valueOf(percent / 100f * detailedBolusInfo.insulin),
                            Double.valueOf(detailedBolusInfo.insulin)));
                    rxBus.send(progressUpdateEvent);
                    SystemClock.sleep(sleep);
                    percent = percent + percent1;
                    aapsLogger.debug(LTag.EQUILBLE, "isCmdStatus===" + percent + "====" + bolusProfile.getStop());
                }
            }


            result.setSuccess(command.isCmdStatus());
            result.enacted(true);
            result.setComment(rh.gs(R.string.virtualpump_resultok));
            result.setBolusDelivered(Double.valueOf(percent / 100f * detailedBolusInfo.insulin));
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult stopBolus(BolusProfile bolusProfile) {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            BaseCmd command = new CmdLargeBasalSet(0);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            bolusProfile.setStop(command.isCmdStatus());
            result.setSuccess(command.isCmdStatus());
            result.enacted(true);
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public int loadHistory(int index) {
        try {
            aapsLogger.debug(LTag.EQUILBLE, "loadHistory start: ");
            CmdHistoryGet historyGet = new CmdHistoryGet(index);
            historyGet.setEquilManager(this);
            equilBLE.writeCmd(historyGet);
            synchronized (historyGet) {
                historyGet.wait(historyGet.getTimeOut());
            }
            aapsLogger.debug(LTag.EQUILBLE, "loadHistory end: ");
            SystemClock.sleep(100);
            return historyGet.getCurrentIndex();
        } catch (Exception ex) {
            ex.printStackTrace();

        }
        return -1;
    }

    public PumpEnactResult getBasal(Profile profile) {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            CmdBasalGet cmdBasalGet = new CmdBasalGet(profile);
            cmdBasalGet.setEquilManager(this);
            equilBLE.writeCmd(cmdBasalGet);
            synchronized (cmdBasalGet) {
                cmdBasalGet.wait(cmdBasalGet.getTimeOut());
            }
            result.setSuccess(cmdBasalGet.isCmdStatus());
        } catch (Exception ex) {
            ex.printStackTrace();
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult executeCmd(BaseCmd command) {


        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            command.setEquilManager(this);
            equilBLE.writeCmd(command);

            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            aapsLogger.debug(LTag.EQUILBLE, "isCmdStatus===" + command.isCmdStatus());
            result.setSuccess(command.isCmdStatus());
            result.enacted(command.isEnacted());

        } catch (Exception ex) {
            ex.printStackTrace();
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult executeCommandGet(BaseCmd command) {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            result.setSuccess(command.isCmdStatus());
            result.enacted(true);
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public String translateException(Throwable ex) {
        return "";
    }

    private void handleException(Exception ex) {
        aapsLogger.error(LTag.PUMP, "Caught an unexpected non-OmnipodException from OmnipodManager", ex);
    }

    public boolean isConnected() {
        return equilBLE.isConnected();
    }

    public void showNotification(int id, String message, int urgency, Integer sound) {
        Notification notification = new Notification( //
                id, //
                message, //
                urgency);
        if (sound != null) {
            notification.setSoundId(sound);
        }
        sendEvent(new EventNewNotification(notification));
    }

    public void dismissNotification(int id) {
        sendEvent(new EventDismissNotification(id));
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }

    private Gson gsonInstance;
    private EquilState equilState;

    private static Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (dateTime, typeOfSrc, context) ->
                        new JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)))
                .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                        ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
                .registerTypeAdapter(DateTimeZone.class, (JsonSerializer<DateTimeZone>) (timeZone, typeOfSrc, context) ->
                        new JsonPrimitive(timeZone.getID()))
                .registerTypeAdapter(DateTimeZone.class, (JsonDeserializer<DateTimeZone>) (json, typeOfT, context) ->
                        DateTimeZone.forID(json.getAsString()));

        return gsonBuilder.create();
    }

    protected String readPodState() {
        return sp.getString(EquilConst.Prefs.EQUIL_STATE, "");
    }

    public final void loadPodState() {
        equilState = null;

        String storedPodState = readPodState();

        if (StringUtils.isEmpty(storedPodState)) {
            equilState = new EquilState();
            aapsLogger.info(LTag.PUMP, "loadPodState: no Pod state was provided");
        } else {
            aapsLogger.info(LTag.PUMP, "loadPodState: serialized Pod state was provided: " + storedPodState);
            try {
                equilState = gsonInstance.fromJson(storedPodState, EquilState.class);
            } catch (Exception ex) {
                equilState = new EquilState();
                aapsLogger.error(LTag.PUMP, "loadPodState: could not deserialize PodState: " + storedPodState, ex);
            }
        }
    }

    public final boolean hasPodState() {

        return this.equilState != null; // 0x0=discarded
    }

    private void setSafe(Runnable runnable) {
        if (!hasPodState()) {
            throw new IllegalStateException("Cannot mutate PodState: podState is null");
        }
        runnable.run();
    }

    public void storePodState() {
        String podState = gsonInstance.toJson(this.equilState);
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: {}", podState);
        storePodState(podState);
    }

    public void clearPodState() {
        this.equilState = new EquilState();
        String podState = gsonInstance.toJson(equilState);
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: {}", podState);
        storePodState(podState);
    }

    private void setAndStore(Runnable runnable) {
        setSafe(runnable);
        storePodState();
    }

    private <T> T getSafe(Supplier<T> supplier) {
        if (!hasPodState()) {
            throw new IllegalStateException("Cannot read from PodState: podState is null");
        }
        return supplier.get();
    }

    protected void storePodState(String podState) {
        sp.putString(EquilConst.Prefs.EQUIL_STATE, podState);
    }

    public String getSerialNumber() {
        return getSafe(() -> equilState.getSerialNumber());
    }

    public final void setSerialNumber(String serialNumber) {
        setAndStore(() -> equilState.setSerialNumber(serialNumber));
    }

    public EquilBolusRecord getBolusRecord() {
        return getSafe(() -> equilState.getBolusRecord());
    }

    public final void setBolusRecord(EquilBolusRecord bolusRecord) {
        setAndStore(() -> equilState.setBolusRecord(bolusRecord));
    }


    public EquilTempBasalRecord getTempBasal() {
        return getSafe(() -> equilState.getTempBasal());
    }

    public final boolean hasTempBasal() {
        return getTempBasal() != null;
    }

    public final boolean isTempBasalRunning() {
        return isTempBasalRunningAt(null);
    }

    public final boolean isTempBasalRunningAt(DateTime time) {
        if (time == null) { // now
            if (!hasTempBasal()) {
                return true;
            }
            time = DateTime.now();
        }
        EquilTempBasalRecord equilTempBasalRecord = getTempBasal();
        if (hasTempBasal()) {
            DateTime tempBasalStartTime = new DateTime(equilTempBasalRecord.getStartTime());
//            tempBasalStartTime = tempBasalStartTime.minus(equilTempBasalRecord.getStartTime());
//            aapsLogger.error("isTempBasalRunningAt===" + android.text.format.DateFormat.format(
//                    "yyyy-MM-dd HH:mm:ss",
//                    tempBasalStartTime.getMillis()
//            ).toString());
//            aapsLogger.error("isTempBasalRunningAt222===" + android.text.format.DateFormat.format(
//                    "yyyy-MM-dd HH:mm:ss",
//                    equilTempBasalRecord.getStartTime()
//            ).toString());
            DateTime tempBasalEndTime = tempBasalStartTime.plus(equilTempBasalRecord.getDuration());
//            aapsLogger.error("isTempBasalRunningAt3333===" + android.text.format.DateFormat.format(
//                    "yyyy-MM-dd HH:mm:ss",
//                    tempBasalEndTime.getMillis()
//            ).toString());
            return (time.isAfter(tempBasalStartTime) || time.isEqual(tempBasalStartTime)) && time.isBefore(tempBasalEndTime);
        }
        return false;
    }

    public final void setTempBasal(EquilTempBasalRecord tempBasal) {
        setAndStore(() -> equilState.setTempBasal(tempBasal));
    }


    public long getLastDataTime() {
        return getSafe(() -> equilState.getLastDataTime());
    }

    public void setLastDataTime(long lastDataTime) {
        setAndStore(() -> equilState.setLastDataTime(lastDataTime));
    }

    public long getDevicesTime() {
        return getSafe(() -> equilState.getDevicesTime());
    }

    public void setDevicesTime(long devicesTime) {
        setAndStore(() -> equilState.setDevicesTime(devicesTime));
    }

    public int getCurrentInsulin() {
        return getSafe(() -> equilState.getCurrentInsulin());
    }

    public void setCurrentInsulin(int currentInsulin) {
        setAndStore(() -> equilState.setCurrentInsulin(currentInsulin));

    }

    public int getStartInsulin() {
//        return getSafe(() -> equilState.getCurrentInsulin());
        return 200;
    }

    public void setStartInsulin(int startInsulin) {
        setAndStore(() -> equilState.setStartInsulin(startInsulin));

    }

    public int getBattery() {
        return getSafe(() -> equilState.getBattery());

    }

    public void setBattery(int battery) {
        setAndStore(() -> equilState.setBattery(battery));

    }

    public RunMode getRunMode() {
        return getSafe(() -> equilState.getRunMode());
    }

    public void setRunMode(RunMode runMode) {
        setAndStore(() -> equilState.setRunMode(runMode));
    }

    public AlarmMode getAlarmMode() {
        return getSafe(() -> equilState.getAlarmMode());

    }

    public void setAlarmMode(AlarmMode alarmMode) {
        setAndStore(() -> equilState.setAlarmMode(alarmMode));

    }

    public String getFirmwareVersion() {
        return getSafe(() -> equilState.getFirmwareVersion());

    }

    public void setFirmwareVersion(String firmwareVersion) {
        setAndStore(() -> equilState.setFirmwareVersion(firmwareVersion));
    }

    public float getRate() {
        return getSafe(() -> equilState.getRate());
    }

    public void setRate(float rate) {
        setAndStore(() -> equilState.setRate(rate));
    }

    public int getHistoryIndex() {
        return getSafe(() -> equilState.getHistoryIndex());

    }

    public void setHistoryIndex(int historyIndex) {
        setAndStore(() -> equilState.setHistoryIndex(historyIndex));

    }

    public String getAddress() {
        return getSafe(() -> equilState.getAddress());
    }

    public void setAddress(String address) {
        setAndStore(() -> equilState.setAddress(address));

    }

    private static final class EquilState {
        private String serialNumber;
        private String address;
        private String firmwareVersion;

        private long lastDataTime;
        private long devicesTime;
        private int currentInsulin;
        private int startInsulin;
        private int battery;
        private EquilTempBasalRecord tempBasal;
        private EquilBolusRecord bolusRecord;
        private RunMode runMode;
        private AlarmMode alarmMode = AlarmMode.TONE_AND_SHAKE;
        private float rate;
        private int historyIndex;

        public int getHistoryIndex() {
            return historyIndex;
        }

        public void setHistoryIndex(int historyIndex) {
            this.historyIndex = historyIndex;
        }

        public float getRate() {
            return rate;
        }

        public void setRate(float rate) {
            this.rate = rate;
        }

        public AlarmMode getAlarmMode() {
            return alarmMode;
        }

        public void setAlarmMode(AlarmMode alarmMode) {
            this.alarmMode = alarmMode;
        }

        public RunMode getRunMode() {
            return runMode;
        }

        public void setRunMode(RunMode runMode) {
            this.runMode = runMode;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public void setSerialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public long getLastDataTime() {
            return lastDataTime;
        }

        public void setLastDataTime(long lastDataTime) {
            this.lastDataTime = lastDataTime;
        }

        public long getDevicesTime() {
            return devicesTime;
        }

        public void setDevicesTime(long devicesTime) {
            this.devicesTime = devicesTime;
        }

        public int getCurrentInsulin() {
            return currentInsulin;
        }

        public void setCurrentInsulin(int currentInsulin) {
            this.currentInsulin = currentInsulin;
        }

        public int getStartInsulin() {
            return startInsulin;
        }

        public void setStartInsulin(int startInsulin) {
            this.startInsulin = startInsulin;
        }

        public String getFirmwareVersion() {
            return firmwareVersion;
        }

        public void setFirmwareVersion(String firmwareVersion) {
            this.firmwareVersion = firmwareVersion;
        }


        public int getBattery() {
            return battery;
        }

        public void setBattery(int battery) {
            this.battery = battery;
        }

        public EquilTempBasalRecord getTempBasal() {
            return tempBasal;
        }

        public void setTempBasal(EquilTempBasalRecord tempBasal) {
            this.tempBasal = tempBasal;
        }

        public EquilBolusRecord getBolusRecord() {
            return bolusRecord;
        }

        public void setBolusRecord(EquilBolusRecord bolusRecord) {
            this.bolusRecord = bolusRecord;
        }
    }

    public void setModel(int modeint) {
        if (modeint == 0) {
            setRunMode(RunMode.SUSPEND);
        } else if (modeint == 1) {
            setRunMode(RunMode.RUN);
        } else if (modeint == 2) {
            setRunMode(RunMode.STOP);

        } else {
            setRunMode(RunMode.SUSPEND);
        }
        rxBus.send(new EventEquilModeChanged());
    }

    public void setInsulinChange(int status) {
        if (status == 1) {
            rxBus.send(new EventEquilInsulinChanged());
        }
    }

    public void decodeHistory(byte[] data) {
        int year = data[6] & 0xff;
        year = year + 2000;

        int month = data[7] & 0xff;
        int day = data[8] & 0xff;
        int hour = data[9] & 0xff;
        int min = data[10] & 0xff;
        int second = data[11] & 0xff;
        //a5e207590501 17070e100f161100000000007d0204080000
        //ae6ae9100501 17070e100f16 1100000000007d0204080000
        int battery = data[12] & 0xff;
        int insulin = data[13] & 0xff;

        int rate = Utils.bytesToInt(data[15], data[14]);
        int largeRate = Utils.bytesToInt(data[17], data[16]);
        int index = Utils.bytesToInt(data[19], data[18]);

        int port = data[20] & 0xff;
        int type = data[21] & 0xff;
        int level = data[22] & 0xff;
        int parm = data[23] & 0xff;
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, min);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0);
        EquilHistoryPump equilHistoryPump = new EquilHistoryPump();
        equilHistoryPump.setBattery(battery);
        equilHistoryPump.setInsulin(insulin);
        equilHistoryPump.setRate(rate);
        equilHistoryPump.setLargeRate(largeRate);
        equilHistoryPump.setTimestamp(System.currentTimeMillis());
        equilHistoryPump.setEventTimestamp((calendar.getTimeInMillis() + index));
        equilHistoryPump.setPort(port);
        equilHistoryPump.setType(type);
        equilHistoryPump.setLevel(level);
        equilHistoryPump.setParm(parm);
        equilHistoryPump.setEventIndex(index);


        rxBus.send(new EventEquilDataChanged());
    }

    public void decodeData(byte[] data) {
        int year = data[11] & 0xFF;
        year = year + 2000;
        int month = data[12] & 0xff;
        int day = data[13] & 0xff;
        int hour = data[14] & 0xff;
        int min = data[15] & 0xff;
        int second = data[16] & 0xff;
        int battery = data[17] & 0xff;
        int insulin = data[18] & 0xff;
        int rate1 = Utils.bytesToInt(data[20], data[19]);
        float rate = Utils.internalDecodeSpeedToUH(rate1);
        float largeRate = Utils.bytesToInt(data[22], data[21]);

        int historyIndex = Utils.bytesToInt(data[24], data[23]);
        int currentIndex = getHistoryIndex();

        int port = data[25] & 0xff;
        int level = data[26] & 0xff;
        int parm = data[27] & 0xff;
        String errorTips = getEquilError(port, level, parm);
        if (!TextUtils.isEmpty(errorTips) && currentIndex != historyIndex) {
            showNotification(Notification.FAILED_UPDATE_PROFILE,
                    errorTips,
                    Notification.NORMAL, R.raw.alarm);
            long time = System.currentTimeMillis();
            EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(time,
                    null,
                    null,
                    EquilHistoryRecord.EventType.EQUIL_ALARM,
                    time,
                    getSerialNumber()
            );
//            equilBLE.handler.postDelayed(new Runnable() {
//                @Override public void run() {
//                    equilHistoryRecordDao.insert(equilHistoryRecord);
//                }
//            },1);
        }
        setHistoryIndex(historyIndex);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, min);
        calendar.set(Calendar.SECOND, second);

        setLastDataTime(System.currentTimeMillis());
        setCurrentInsulin(insulin);
        setBattery(battery);
        setRate(rate);
        rxBus.send(new EventEquilDataChanged());
    }
}
