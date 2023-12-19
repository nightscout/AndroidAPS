package app.aaps.pump.equil.manager;

import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.data.model.BS;
import app.aaps.core.data.pump.defs.PumpType;
import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.notifications.Notification;
import app.aaps.core.interfaces.objects.Instantiator;
import app.aaps.core.interfaces.profile.Profile;
import app.aaps.core.interfaces.pump.DetailedBolusInfo;
import app.aaps.core.interfaces.pump.PumpEnactResult;
import app.aaps.core.interfaces.pump.PumpSync;
import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.Event;
import app.aaps.core.interfaces.rx.events.EventDismissNotification;
import app.aaps.core.interfaces.rx.events.EventNewNotification;
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress;
import app.aaps.core.interfaces.sharedPreferences.SP;
import app.aaps.core.interfaces.utils.HardLimits;
import app.aaps.pump.equil.EquilConst;
import app.aaps.pump.equil.R;
import app.aaps.pump.equil.ble.EquilBLE;
import app.aaps.pump.equil.data.AlarmMode;
import app.aaps.pump.equil.data.BolusProfile;
import app.aaps.pump.equil.data.RunMode;
import app.aaps.pump.equil.database.BolusType;
import app.aaps.pump.equil.database.EquilBasalValuesRecord;
import app.aaps.pump.equil.database.EquilBolusRecord;
import app.aaps.pump.equil.database.EquilHistoryPump;
import app.aaps.pump.equil.database.EquilHistoryPumpDao;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.database.EquilHistoryRecordDao;
import app.aaps.pump.equil.database.EquilTempBasalRecord;
import app.aaps.pump.equil.database.ResolvedResult;
import app.aaps.pump.equil.driver.definition.ActivationProgress;
import app.aaps.pump.equil.driver.definition.BasalSchedule;
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState;
import app.aaps.pump.equil.events.EventEquilDataChanged;
import app.aaps.pump.equil.events.EventEquilInsulinChanged;
import app.aaps.pump.equil.events.EventEquilModeChanged;
import app.aaps.pump.equil.manager.command.BaseCmd;
import app.aaps.pump.equil.manager.command.CmdBasalGet;
import app.aaps.pump.equil.manager.command.CmdBasalSet;
import app.aaps.pump.equil.manager.command.CmdExtendedBolusSet;
import app.aaps.pump.equil.manager.command.CmdHistoryGet;
import app.aaps.pump.equil.manager.command.CmdLargeBasalSet;
import app.aaps.pump.equil.manager.command.CmdModelGet;
import app.aaps.pump.equil.manager.command.CmdTempBasalGet;
import app.aaps.pump.equil.manager.command.CmdTempBasalSet;
import app.aaps.pump.equil.manager.command.PumpEvent;
import dagger.android.HasAndroidInjector;

@Singleton
public class EquilManager {
    private final AAPSLogger aapsLogger;
    private final RxBus rxBus;
    private final ResourceHelper rh;
    private final SP sp;
    private final PumpSync pumpSync;
    private final Instantiator instantiator;
    EquilBLE equilBLE;
    EquilHistoryRecordDao equilHistoryRecordDao;
    EquilHistoryPumpDao equilHistoryPumpDao;

    public AAPSLogger getAapsLogger() {
        return aapsLogger;
    }

    public SP getSp() {
        return sp;
    }


    @Inject
    public EquilManager(
            AAPSLogger aapsLogger,
            RxBus rxBus,
            SP sp,
            ResourceHelper rh,
            PumpSync pumpSync,
            EquilBLE equilBLE,
            EquilHistoryRecordDao equilHistoryRecordDao,
            EquilHistoryPumpDao equilHistoryPumpDao,
            Instantiator instantiator
    ) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.sp = sp;
        this.rh = rh;
        this.pumpSync = pumpSync;
        this.equilBLE = equilBLE;
        this.equilHistoryRecordDao = equilHistoryRecordDao;
        this.equilHistoryPumpDao = equilHistoryPumpDao;
        this.instantiator = instantiator;

        this.gsonInstance = createGson();
        loadPodState();
        initEquilError();
        equilBLE.init(this);
        equilBLE.getEquilStatus();
    }

    List<PumpEvent> listEvent;

    private void initEquilError() {
        listEvent = new ArrayList<>();
        listEvent.add(new PumpEvent(4, 2, 2, rh.gs(R.string.equil_history_item3)));
        listEvent.add(new PumpEvent(4, 3, 0, rh.gs(R.string.equil_history_item4)));
        listEvent.add(new PumpEvent(4, 3, 2, rh.gs(R.string.equil_history_item5)));
        listEvent.add(new PumpEvent(4, 6, 1, rh.gs(R.string.equil_shutdown_be)));
        listEvent.add(new PumpEvent(4, 6, 2, rh.gs(R.string.equil_shutdown)));
        listEvent.add(new PumpEvent(4, 8, 0, rh.gs(R.string.equil_shutdown)));
        listEvent.add(new PumpEvent(5, 1, 2, rh.gs(R.string.equil_history_item18)));

    }

    public String getEquilError(int port, int type, int level) {
        PumpEvent pumpEvent = new PumpEvent(port, type, level, "");
        int index = listEvent.indexOf(pumpEvent);
        if (index == -1) {
            return "";
        }
        return listEvent.get(index).getConent();
    }

    public void closeBleAuto() {
        equilBLE.closeBleAuto();

    }

    public PumpEnactResult closeBle() {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            equilBLE.disconnect();
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult readStatus() {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            equilBLE.getEquilStatus();
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult getTempBasalPump() {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            CmdTempBasalGet command = new CmdTempBasalGet();
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            result.setSuccess(command.isCmdStatus());
            result.enacted(command.getTime() != 0);
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD);
        } catch (Exception ex) {
            ex.printStackTrace();
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult setTempBasal(double insulin, int time, boolean cancel) {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            CmdTempBasalSet command = new CmdTempBasalSet(insulin, time);
            command.setCancel(cancel);
            EquilHistoryRecord equilHistoryRecord = addHistory(command);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            if (command.isCmdStatus()) {
                long currentTime = System.currentTimeMillis();
                if (cancel) {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                            currentTime,
                            currentTime,
                            PumpType.EQUIL,
                            getSerialNumber()
                    );
                    setTempBasal(null);
                } else {
                    EquilTempBasalRecord tempBasalRecord =
                            new EquilTempBasalRecord(time * 60 * 1000,
                                    insulin, currentTime);
                    setTempBasal(tempBasalRecord);
                    pumpSync.syncTemporaryBasalWithPumpId(
                            currentTime,
                            insulin,
                            (long) time * 60 * 1000,
                            true,
                            PumpSync.TemporaryBasalType.NORMAL,
                            currentTime,
                            PumpType.EQUIL,
                            getSerialNumber()
                    );
                }
                command.setResolvedResult(ResolvedResult.SUCCESS);
            }
            updateHistory(equilHistoryRecord, command.getResolvedResult());
            result.setSuccess(command.isCmdStatus());
            result.enacted(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult setExtendedBolus(double insulin, int time, boolean cancel) {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            CmdExtendedBolusSet command = new CmdExtendedBolusSet(insulin, time, cancel);
            EquilHistoryRecord equilHistoryRecord = addHistory(command);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }

            result.setSuccess(command.isCmdStatus());
            if (command.isCmdStatus()) {
                command.setResolvedResult(ResolvedResult.SUCCESS);
                long currentTimeMillis = System.currentTimeMillis();
                if (cancel) {
                    pumpSync.syncStopExtendedBolusWithPumpId(
                            currentTimeMillis,
                            currentTimeMillis,
                            PumpType.EQUIL,
                            getSerialNumber()
                    );
                } else {
                    pumpSync.syncExtendedBolusWithPumpId(
                            currentTimeMillis,
                            insulin,
                            (long) time * 60 * 1000,
                            true,
                            currentTimeMillis,
                            PumpType.EQUIL,
                            getSerialNumber()
                    );
                }

                result.enacted(true);
            } else {
                result.setSuccess(false);
            }
            updateHistory(equilHistoryRecord, command.getResolvedResult());
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }


    public PumpEnactResult bolus(DetailedBolusInfo detailedBolusInfo, BolusProfile bolusProfile) {
        EventOverviewBolusProgress progressUpdateEvent = EventOverviewBolusProgress.INSTANCE;
        progressUpdateEvent.setT(new EventOverviewBolusProgress.Treatment(HardLimits.MAX_IOB_LGS, 0,
                detailedBolusInfo.getBolusType() ==
                        BS.Type.SMB, detailedBolusInfo.getId()));
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            CmdLargeBasalSet command = new CmdLargeBasalSet(detailedBolusInfo.insulin);
            EquilHistoryRecord equilHistoryRecord = addHistory(command);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            bolusProfile.setStop(false);
            int sleep = command.getStepTime() / 20 * 200;
            sleep = 2000;
            float percent1 = (float) (5f / detailedBolusInfo.insulin);
            aapsLogger.debug(LTag.PUMPCOMM, "sleep===" + detailedBolusInfo.insulin + "===" + percent1);
            float percent = 0;
            if (command.isCmdStatus()) {
                result.setSuccess(true);
                result.enacted(true);
                while (!bolusProfile.getStop() && percent < 100) {
                    progressUpdateEvent.setPercent((int) percent);
                    progressUpdateEvent.setStatus(this.rh.gs(R.string.equil_bolus_delivered,
                            percent / 100d * detailedBolusInfo.insulin,
                            detailedBolusInfo.insulin));
                    rxBus.send(progressUpdateEvent);
                    SystemClock.sleep(sleep);
                    percent = percent + percent1;
                    aapsLogger.debug(LTag.PUMPCOMM, "isCmdStatus===" + percent + "====" + bolusProfile.getStop());
                }
                result.setComment(rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok));
            } else {
                result.setSuccess(false);
                result.enacted(false);
                result.setComment(rh.gs(R.string.equil_command_connect_error));
            }
            result.setBolusDelivered(percent / 100d * detailedBolusInfo.insulin);
            if (result.getSuccess()) {
                command.setResolvedResult(ResolvedResult.SUCCESS);
                long currentTime = System.currentTimeMillis();
                pumpSync.syncBolusWithPumpId(currentTime,
                        result.getBolusDelivered(),
                        detailedBolusInfo.getBolusType(),
                        detailedBolusInfo.timestamp,
                        PumpType.EQUIL,
                        getSerialNumber());
                EquilBolusRecord equilBolusRecord =
                        new EquilBolusRecord(result.getBolusDelivered(),
                                BolusType.SMB, currentTime);
                setBolusRecord(equilBolusRecord);

            }
            updateHistory(equilHistoryRecord, command.getResolvedResult());
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult stopBolus(BolusProfile bolusProfile) {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            BaseCmd command = new CmdLargeBasalSet(0);
            EquilHistoryRecord equilHistoryRecord = addHistory(command);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            bolusProfile.setStop(command.isCmdStatus());
            aapsLogger.debug(LTag.PUMPCOMM, "stopBolus===");
            result.setSuccess(command.isCmdStatus());
            if (command.isCmdStatus()) {
                command.setResolvedResult(ResolvedResult.SUCCESS);
            }
            updateHistory(equilHistoryRecord, command.getResolvedResult());
            result.enacted(true);
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public int loadEquilHistory(int index) {
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory start: ");
            CmdHistoryGet historyGet = new CmdHistoryGet(index);
            historyGet.setEquilManager(this);
            equilBLE.readHistory(historyGet);
            synchronized (historyGet) {
                historyGet.wait(historyGet.getTimeOut());
            }
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory end: ");
            return historyGet.getCurrentIndex();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public int loadHistory(int index) {
        try {
            CmdHistoryGet historyGet = new CmdHistoryGet(index);
            historyGet.setEquilManager(this);
            equilBLE.writeCmd(historyGet);
            synchronized (historyGet) {
                historyGet.wait(historyGet.getTimeOut());
            }
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory end: ");
            return historyGet.getCurrentIndex();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public PumpEnactResult getBasal(Profile profile) {
        PumpEnactResult result = instantiator.providePumpEnactResult();
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

    public EquilHistoryRecord addHistory(BaseCmd command) {
        EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(System.currentTimeMillis(), getSerialNumber());
        if (command.getEventType() != null) {
            equilHistoryRecord.setType(command.getEventType());
        }
        if (command instanceof CmdBasalSet) {
            Profile profile = ((CmdBasalSet) command).getProfile();
            equilHistoryRecord.setBasalValuesRecord(new EquilBasalValuesRecord(Arrays.asList(profile.getBasalValues())));
        }
        if (command instanceof CmdTempBasalSet) {
            CmdTempBasalSet cmd = ((CmdTempBasalSet) command);
            boolean cancel = cmd.isCancel();
            if (!cancel) {
                EquilTempBasalRecord equilTempBasalRecord =
                        new EquilTempBasalRecord(cmd.getDuration() * 60 * 1000,
                                cmd.getInsulin(), System.currentTimeMillis());
                equilHistoryRecord.setTempBasalRecord(equilTempBasalRecord);
            }
        }
        if (command instanceof CmdExtendedBolusSet) {
            CmdExtendedBolusSet cmd = ((CmdExtendedBolusSet) command);
            boolean cancel = cmd.isCancel();
            if (!cancel) {
                EquilTempBasalRecord equilTempBasalRecord =
                        new EquilTempBasalRecord(cmd.getDurationInMinutes() * 60 * 1000,
                                cmd.getInsulin(), System.currentTimeMillis());
                equilHistoryRecord.setTempBasalRecord(equilTempBasalRecord);
            }
        }
        if (command instanceof CmdLargeBasalSet) {
            CmdLargeBasalSet cmd = ((CmdLargeBasalSet) command);
            double insulin = cmd.getInsulin();
            if (insulin != 0) {
                EquilBolusRecord equilBolusRecord =
                        new EquilBolusRecord(insulin,
                                BolusType.SMB, System.currentTimeMillis());
                equilHistoryRecord.setBolusRecord(equilBolusRecord);
            }
        }

        if (equilHistoryRecord.getType() != null) {
            long id = equilHistoryRecordDao.insert(equilHistoryRecord);
            equilHistoryRecord.setId(id);
            aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord is {}", id);
        }
        return equilHistoryRecord;
    }

    public void updateHistory(EquilHistoryRecord equilHistoryRecord, ResolvedResult result) {
        if (result != null && equilHistoryRecord != null) {
            aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord2 is {} {}",
                    equilHistoryRecord.getId(), result);
            equilHistoryRecord.setResolvedAt(System.currentTimeMillis());
            equilHistoryRecord.setResolvedStatus(result);
            int status = equilHistoryRecordDao.update(equilHistoryRecord);
            aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord3== is {} {} status {}",
                    equilHistoryRecord.getId(), equilHistoryRecord.getResolvedStatus(), status);
        }
    }

    public PumpEnactResult readEquilStatus() {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            BaseCmd command = new CmdModelGet();
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            if (command.isCmdStatus()) {
                command.setResolvedResult(ResolvedResult.SUCCESS);
                SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD);
                return loadEquilHistory();
            }
            result.setSuccess(command.isCmdStatus());
            result.enacted(command.isEnacted());
        } catch (Exception ex) {
            ex.printStackTrace();
            result.success(false).enacted(false).comment(translateException(ex));
        }
        return result;
    }

    public PumpEnactResult loadEquilHistory() {
        PumpEnactResult pumpEnactResult = instantiator.providePumpEnactResult();
        int startIndex;
        startIndex = getStartHistoryIndex();
        int index = getHistoryIndex();
        aapsLogger.debug(LTag.PUMPCOMM, "return ===" + index + "====" + startIndex);
        if (index == -1) {
            return pumpEnactResult.success(false);
        }
        int allCount = 1;
        while (startIndex != index && allCount < 20) {
            startIndex++;
            if (startIndex > 2000) {
                startIndex = 1;
            }
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD);
            int currentIndex = loadEquilHistory(startIndex);
            aapsLogger.debug(LTag.PUMPCOMM, "while index===" + startIndex + "===" + index + "===" + currentIndex);
            if (currentIndex > 1) {
                setStartHistoryIndex(currentIndex);
                allCount++;
            } else {
                break;
            }
        }
        return pumpEnactResult.success(true);
    }

    public PumpEnactResult executeCmd(BaseCmd command) {
        PumpEnactResult result = instantiator.providePumpEnactResult();
        try {
            EquilHistoryRecord equilHistoryRecord = addHistory(command);
            command.setEquilManager(this);
            equilBLE.writeCmd(command);
            synchronized (command) {
                command.wait(command.getTimeOut());
            }
            if (command.isCmdStatus()) {
                command.setResolvedResult(ResolvedResult.SUCCESS);
            }
            updateHistory(equilHistoryRecord, command.getResolvedResult());
            aapsLogger.debug(LTag.PUMPCOMM, "executeCmd result {}", command.getResolvedResult());
            result.setSuccess(command.isCmdStatus());
            result.enacted(command.isEnacted());
        } catch (Exception ex) {

            ex.printStackTrace();
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

    private final Gson gsonInstance;
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
        return sp.getString(EquilConst.Prefs.INSTANCE.getEQUIL_STATE(), "");
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
        sp.putString(EquilConst.Prefs.INSTANCE.getEQUIL_STATE(), podState);
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
            DateTime tempBasalEndTime = tempBasalStartTime.plus(equilTempBasalRecord.getDuration());
            return (time.isAfter(tempBasalStartTime) || time.isEqual(tempBasalStartTime)) && time.isBefore(tempBasalEndTime);
        }
        return false;
    }

    public final boolean isPumpRunning() {
        return getRunMode() == RunMode.RUN;
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
        return getSafe(() -> equilState.getStartInsulin());
//        return 200;
    }

    public void setStartInsulin(int startInsulin) {
        aapsLogger.debug(LTag.PUMPCOMM, "startInsulin {}", startInsulin);
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

    public final ActivationProgress getActivationProgress() {
        if (hasPodState()) {
            return Optional.ofNullable(equilState.getActivationProgress()).orElse(ActivationProgress.NONE);
        }
        return ActivationProgress.NONE;
    }

    public final boolean isActivationCompleted() {
        return getActivationProgress() == ActivationProgress.COMPLETED;
    }

    public final boolean isActivationInitialized() {
        return getActivationProgress() != ActivationProgress.NONE;
    }

    public void setActivationProgress(ActivationProgress activationProgress) {
        setAndStore(() -> equilState.setActivationProgress(activationProgress));
    }


    public BluetoothConnectionState getBluetoothConnectionState() {
        return getSafe(() -> equilState.getBluetoothConnectionState());
    }

    public void setBluetoothConnectionState(BluetoothConnectionState bluetoothConnectionState) {
        setAndStore(() -> equilState.setBluetoothConnectionState(bluetoothConnectionState));
    }

    public int getStartHistoryIndex() {
        return getSafe(() -> equilState.getStartHistoryIndex());

    }

    public void setStartHistoryIndex(int startHistoryIndex) {
        setAndStore(() -> equilState.setStartHistoryIndex(startHistoryIndex));

    }

    public BasalSchedule getBasalSchedule() {
        return getSafe(() -> equilState.getBasalSchedule());

    }

    public void setBasalSchedule(BasalSchedule basalSchedule) {
        setAndStore(() -> equilState.setBasalSchedule(basalSchedule));

    }

    private static final class EquilState {
        private ActivationProgress activationProgress;
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
        @NonNull private AlarmMode alarmMode = AlarmMode.TONE_AND_SHAKE;
        private float rate;
        private int historyIndex;

        BluetoothConnectionState bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED;
        private int startHistoryIndex;
        private BasalSchedule basalSchedule;

        public BasalSchedule getBasalSchedule() {
            return basalSchedule;
        }

        public void setBasalSchedule(BasalSchedule basalSchedule) {
            this.basalSchedule = basalSchedule;
        }

        public int getStartHistoryIndex() {
            return startHistoryIndex;
        }

        public void setStartHistoryIndex(int startHistoryIndex) {
            this.startHistoryIndex = startHistoryIndex;
        }

        public BluetoothConnectionState getBluetoothConnectionState() {
            return bluetoothConnectionState;
        }

        public void setBluetoothConnectionState(BluetoothConnectionState bluetoothConnectionState) {
            this.bluetoothConnectionState = bluetoothConnectionState;
        }

        public ActivationProgress getActivationProgress() {
            return activationProgress;
        }

        public void setActivationProgress(ActivationProgress activationProgress) {
            this.activationProgress = activationProgress;
        }

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

        @NonNull public AlarmMode getAlarmMode() {
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
        equilHistoryPump.setSerialNumber(getSerialNumber());
        long id = equilHistoryPumpDao.insert(equilHistoryPump);
        aapsLogger.debug(LTag.PUMPCOMM, "decodeHistory insert id {}", id);
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
                    Notification.NORMAL, app.aaps.core.ui.R.raw.alarm);
            long time = System.currentTimeMillis();
            EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(
                    EquilHistoryRecord.EventType.EQUIL_ALARM,
                    time,
                    getSerialNumber()
            );
            equilHistoryRecord.setResolvedAt(System.currentTimeMillis());
            equilHistoryRecord.setResolvedStatus(ResolvedResult.SUCCESS);
            equilHistoryRecord.setNote(errorTips);
            equilHistoryRecordDao.insert(equilHistoryRecord);
        }
        aapsLogger.debug(LTag.PUMPCOMM, "decodeData historyIndex {} errorTips {} port:{} level:{} " +
                        "parm:{}",
                historyIndex,
                errorTips, port, level, parm);
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
