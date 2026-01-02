package app.aaps.pump.equil.manager

import android.os.SystemClock
import android.text.TextUtils
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.EquilBLE
import app.aaps.pump.equil.data.BolusProfile
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.BolusType
import app.aaps.pump.equil.database.EquilBasalValuesRecord
import app.aaps.pump.equil.database.EquilBolusRecord
import app.aaps.pump.equil.database.EquilHistoryPump
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.database.EquilTempBasalRecord
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.events.EventEquilAlarm
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.events.EventEquilInsulinChanged
import app.aaps.pump.equil.events.EventEquilModeChanged
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.Utils.bytesToInt
import app.aaps.pump.equil.manager.Utils.internalDecodeSpeedToUH
import app.aaps.pump.equil.manager.command.BaseCmd
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdExtendedBolusSet
import app.aaps.pump.equil.manager.command.CmdHistoryGet
import app.aaps.pump.equil.manager.command.CmdLargeBasalSet
import app.aaps.pump.equil.manager.command.CmdRunningModeGet
import app.aaps.pump.equil.manager.command.CmdTempBasalGet
import app.aaps.pump.equil.manager.command.CmdTempBasalSet
import app.aaps.pump.equil.manager.command.PumpEvent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import java.lang.reflect.Type
import java.util.Calendar
import java.util.Optional
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class EquilManager @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val pumpSync: PumpSync,
    private val equilBLE: EquilBLE,
    private val equilHistoryRecordDao: EquilHistoryRecordDao,
    private val equilHistoryPumpDao: EquilHistoryPumpDao,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val dateUtil: DateUtil
) {

    private val gsonInstance: Gson = createGson()
    var equilState: EquilState? = null
        private set

    fun init() {
        loadPodState()
        initEquilError()
        equilBLE.init(this)
        //equilBLE.connect("EquilManager::init")
    }

    var listEvent: MutableList<PumpEvent> = ArrayList()

    private fun initEquilError() {
        listEvent = ArrayList()
        listEvent.add(PumpEvent(4, 2, 2, rh.gs(R.string.equil_history_item3)))
        listEvent.add(PumpEvent(4, 3, 0, rh.gs(R.string.equil_history_item4)))
        listEvent.add(PumpEvent(4, 3, 2, rh.gs(R.string.equil_history_item5)))
        listEvent.add(PumpEvent(4, 6, 1, rh.gs(R.string.equil_shutdown_be)))
        listEvent.add(PumpEvent(4, 6, 2, rh.gs(R.string.equil_shutdown)))
        listEvent.add(PumpEvent(4, 8, 0, rh.gs(R.string.equil_shutdown)))
        listEvent.add(PumpEvent(5, 1, 2, rh.gs(R.string.equil_history_item18)))
    }

    fun getEquilError(port: Int, type: Int, level: Int): String {
        val pumpEvent = PumpEvent(port, type, level, "")
        val index = listEvent.indexOf(pumpEvent)
        if (index == -1) {
            return ""
        }
        return listEvent[index].comment
    }

    fun closeBleAuto() {
        equilBLE.closeBleAuto()
    }

    fun connect() {
        equilBLE.connect("EquilManager::connect")
    }

    fun getTempBasalPump(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val command = CmdTempBasalGet(aapsLogger, preferences, this)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }
            result.success = command.cmdSuccess
            result.enacted(command.time != 0)
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun setTempBasal(insulin: Double, time: Int, cancel: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val command = CmdTempBasalSet(insulin, time, aapsLogger, preferences, this)
            command.cancel = cancel
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }
            if (command.cmdSuccess) {
                val currentTime = System.currentTimeMillis()
                if (cancel) {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        currentTime,
                        currentTime,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                    setTempBasal(null)
                } else {
                    val tempBasalRecord =
                        EquilTempBasalRecord(
                            time * 60 * 1000,
                            insulin, currentTime
                        )
                    setTempBasal(tempBasalRecord)
                    pumpSync.syncTemporaryBasalWithPumpId(
                        currentTime,
                        insulin,
                        time.toLong() * 60 * 1000,
                        true,
                        PumpSync.TemporaryBasalType.NORMAL,
                        currentTime,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                }
                command.resolvedResult = ResolvedResult.SUCCESS
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
            result.success = command.cmdSuccess
            result.enacted(true)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun setExtendedBolus(insulin: Double, time: Int, cancel: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val command = CmdExtendedBolusSet(insulin, time, cancel, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }

            result.success = command.cmdSuccess
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
                val currentTimeMillis = System.currentTimeMillis()
                if (cancel) {
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        currentTimeMillis,
                        currentTimeMillis,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                } else {
                    pumpSync.syncExtendedBolusWithPumpId(
                        currentTimeMillis,
                        insulin,
                        time.toLong() * 60 * 1000,
                        true,
                        currentTimeMillis,
                        PumpType.EQUIL,
                        getSerialNumber()
                    )
                }

                result.enacted(true)
            } else {
                result.success = false
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun bolus(detailedBolusInfo: DetailedBolusInfo, bolusProfile: BolusProfile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val command = CmdLargeBasalSet(detailedBolusInfo.insulin, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }
            bolusProfile.stop = false
            val sleep = 2000
            val percent1 = (5f / detailedBolusInfo.insulin).toFloat()
            aapsLogger.debug(LTag.PUMPCOMM, "sleep===" + detailedBolusInfo.insulin + "===" + percent1)
            var percent = 0f
            if (command.cmdSuccess) {
                result.success = true
                result.enacted(true)
                while (!bolusProfile.stop && percent < 100) {
                    rxBus.send(EventOverviewBolusProgress(rh, percent / 100.0 * detailedBolusInfo.insulin, id = detailedBolusInfo.id))
                    SystemClock.sleep(sleep.toLong())
                    percent = percent + percent1
                    aapsLogger.debug(LTag.PUMPCOMM, "isCmdStatus===" + percent + "====" + bolusProfile.stop)
                }
                // constraint percent.
                percent = min(percent, 100.0f)
                result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
            } else {
                result.success = false
                result.enacted(false)
                result.comment = rh.gs(R.string.equil_command_connect_error)
            }
            result.bolusDelivered = percent / 100.0 * detailedBolusInfo.insulin
            if (result.success) {
                command.resolvedResult = ResolvedResult.SUCCESS
                val currentTime = System.currentTimeMillis()
                pumpSync.syncBolusWithPumpId(
                    currentTime,
                    result.bolusDelivered,
                    detailedBolusInfo.bolusType,
                    detailedBolusInfo.timestamp,
                    PumpType.EQUIL,
                    getSerialNumber()
                )
                val equilBolusRecord = EquilBolusRecord(result.bolusDelivered, BolusType.SMB, currentTime)
                setBolusRecord(equilBolusRecord)
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun stopBolus(bolusProfile: BolusProfile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val command: BaseCmd = CmdLargeBasalSet(0.0, aapsLogger, preferences, this)
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }
            bolusProfile.stop = command.cmdSuccess
            aapsLogger.debug(LTag.PUMPCOMM, "stopBolus===")
            result.success = command.cmdSuccess
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            loadEquilHistory()
            result.enacted(true)
        } catch (ex: Exception) {
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun loadEquilHistory(index: Int): Int {
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory start: ")
            val historyGet = CmdHistoryGet(index, aapsLogger, preferences, dateUtil, this)
            equilBLE.readHistory(historyGet)
            synchronized(historyGet) {
                (historyGet as Object).wait(historyGet.timeOut.toLong())
            }
            aapsLogger.debug(LTag.PUMPCOMM, "loadHistory end: ")
            return historyGet.currentIndex
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return -1
    }

    fun addHistory(command: BaseCmd): EquilHistoryRecord {
        val equilHistoryRecord = EquilHistoryRecord(System.currentTimeMillis(), getSerialNumber())
        if (command.getEventType() != null) {
            equilHistoryRecord.type = command.getEventType()
        }
        if (command is CmdBasalSet) {
            val profile = command.profile
            equilHistoryRecord.basalValuesRecord = EquilBasalValuesRecord(listOf(*profile.getBasalValues()))
        }
        if (command is CmdTempBasalSet) {
            val cancel = command.cancel
            if (!cancel) {
                val equilTempBasalRecord =
                    EquilTempBasalRecord(
                        command.duration * 60 * 1000,
                        command.insulin, System.currentTimeMillis()
                    )
                equilHistoryRecord.tempBasalRecord = equilTempBasalRecord
            }
        }
        if (command is CmdExtendedBolusSet) {
            val cancel = command.cancel
            if (!cancel) {
                val equilTempBasalRecord = EquilTempBasalRecord(command.durationInMinutes * 60 * 1000, command.insulin, System.currentTimeMillis())
                equilHistoryRecord.tempBasalRecord = equilTempBasalRecord
            }
        }
        if (command is CmdLargeBasalSet) {
            val insulin = command.insulin
            if (insulin != 0.0) {
                val equilBolusRecord = EquilBolusRecord(insulin, BolusType.SMB, System.currentTimeMillis())
                equilHistoryRecord.bolusRecord = equilBolusRecord
            }
        }

        if (equilHistoryRecord.type != null) {
            val id = equilHistoryRecordDao.insert(equilHistoryRecord)
            equilHistoryRecord.id = id
            aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord is {}", id)
        }
        return equilHistoryRecord
    }

    fun updateHistory(equilHistoryRecord: EquilHistoryRecord, result: ResolvedResult) {
        aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord2 is {} {}", equilHistoryRecord.id, result)
        equilHistoryRecord.resolvedAt = System.currentTimeMillis()
        equilHistoryRecord.resolvedStatus = result
        val status = equilHistoryRecordDao.update(equilHistoryRecord)
        aapsLogger.debug(LTag.PUMPCOMM, "equilHistoryRecord3== is {} {} status {}", equilHistoryRecord.id, equilHistoryRecord.resolvedStatus, status)
    }

    fun readModeAndHistory(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val command: BaseCmd = CmdRunningModeGet(aapsLogger, preferences, this)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
                SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                return loadEquilHistory()
            }
            result.success = command.cmdSuccess
            result.enacted(command.enacted)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun loadEquilHistory(): PumpEnactResult {
        SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
        val pumpEnactResult = pumpEnactResultProvider.get()
        var startIndex = getStartHistoryIndex() ?: return pumpEnactResult
        val index = getHistoryIndex() ?: return pumpEnactResult
        aapsLogger.debug(LTag.PUMPCOMM, "return ===$index====$startIndex")
        if (index == -1) {
            return pumpEnactResult.success(false)
        }
        var allCount = 1
        while (startIndex != index && allCount < 20) {
            startIndex++
            if (startIndex > 2000) {
                startIndex = 1
            }
            SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
            val currentIndex = loadEquilHistory(startIndex)
            aapsLogger.debug(LTag.PUMPCOMM, "while index===$startIndex===$index===$currentIndex")
            if (currentIndex > 1) {
                setStartHistoryIndex(currentIndex)
                allCount++
            } else {
                break
            }
        }
        return pumpEnactResult.success(true)
    }

    fun executeCmd(command: BaseCmd): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        try {
            val equilHistoryRecord = addHistory(command)
            equilBLE.writeCmd(command)
            synchronized(command) {
                (command as Object).wait(command.timeOut.toLong())
            }
            if (command.cmdSuccess) {
                command.resolvedResult = ResolvedResult.SUCCESS
            }
            updateHistory(equilHistoryRecord, command.resolvedResult)
            aapsLogger.debug(LTag.PUMPCOMM, "executeCmd result {}", command.resolvedResult)
            result.success = command.cmdSuccess
            result.enacted(command.enacted)
        } catch (ex: Exception) {
            ex.printStackTrace()
            result.success(false).enacted(false).comment(ex.message ?: "Exception")
        }
        return result
    }

    fun showNotification(id: Int, message: String, urgency: Int, sound: Int?) {
        val notification = Notification(id, message, urgency)
        if (sound != null) {
            notification.soundId = sound
        }
        sendEvent(EventNewNotification(notification))
    }

    private fun sendEvent(event: Event) {
        rxBus.send(event)
    }

    fun readPodState(): String {
        return preferences.get(EquilStringKey.State)
    }

    fun loadPodState() {
        equilState = null

        val storedPodState = readPodState()

        if (StringUtils.isEmpty(storedPodState)) {
            equilState = EquilState()
            aapsLogger.info(LTag.PUMP, "loadPodState: no Pod state was provided")
        } else {
            aapsLogger.info(LTag.PUMP, "loadPodState: serialized Pod state was provided: $storedPodState")
            try {
                equilState = gsonInstance.fromJson(storedPodState, EquilState::class.java)
            } catch (ex: Exception) {
                equilState = EquilState()
                aapsLogger.error(LTag.PUMP, "loadPodState: could not deserialize PodState: $storedPodState", ex)
            }
        }
    }

    fun hasPodState(): Boolean = equilState != null // 0x0=discarded

    fun storePodState() {
        val podState = gsonInstance.toJson(this.equilState)
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: {}", podState)
        storePodState(podState)
    }

    fun clearPodState() {
        this.equilState = EquilState()
        val podState = gsonInstance.toJson(equilState)
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: {}", podState)
        storePodState(podState)
    }

    private fun storePodState(podState: String) {
        preferences.put(EquilStringKey.State, podState)
    }

    fun setAddress(address: String) {
        equilState?.address = address
        storePodState()
    }

    fun setBluetoothConnectionState(bluetoothConnectionState: BluetoothConnectionState) {
        equilState?.bluetoothConnectionState = bluetoothConnectionState
        storePodState()
    }

    fun setSerialNumber(serialNumber: String) {
        equilState?.serialNumber = serialNumber
        storePodState()
    }

    fun getSerialNumber(): String = equilState?.serialNumber ?: "UNKNOWN"

    fun setBolusRecord(bolusRecord: EquilBolusRecord?) {
        equilState?.bolusRecord = bolusRecord
        storePodState()
    }

    fun getTempBasal(): EquilTempBasalRecord? = equilState?.tempBasal

    fun hasTempBasal(): Boolean = getTempBasal() != null

    fun isTempBasalRunning(): Boolean = isTempBasalRunningAt(null)

    fun isTempBasalRunningAt(at: DateTime?): Boolean {
        var time = at
        if (time == null) { // now
            if (!hasTempBasal()) {
                return true
            }
            time = DateTime.now()
        }
        getTempBasal()?.let { equilTempBasalRecord ->
            val tempBasalStartTime = DateTime(equilTempBasalRecord.startTime)
            val tempBasalEndTime = tempBasalStartTime.plus(equilTempBasalRecord.duration.toLong())
            return (time!!.isAfter(tempBasalStartTime) || time.isEqual(tempBasalStartTime)) && time.isBefore(tempBasalEndTime)
        }
        return false
    }

    fun setTempBasal(tempBasal: EquilTempBasalRecord?) {
        equilState?.tempBasal = tempBasal
        storePodState()
    }

    fun setBasalSchedule(basalSchedule: BasalSchedule) {
        equilState?.basalSchedule = basalSchedule
        storePodState()
    }

    fun setLastDataTime(lastDataTime: Long) {
        equilState?.lastDataTime = lastDataTime
        storePodState()
    }

    fun setCurrentInsulin(currentInsulin: Int) {
        equilState?.currentInsulin = currentInsulin
        storePodState()
    }

    fun setStartInsulin(startInsulin: Int) {
        aapsLogger.debug(LTag.PUMPCOMM, "startInsulin {}", startInsulin)
        equilState?.startInsulin = startInsulin
        storePodState()
    }

    fun setBattery(battery: Int) {
        equilState?.battery = battery
        storePodState()
    }

    fun setRunMode(runMode: RunMode) {
        equilState?.runMode = runMode
        storePodState()
    }

    fun setFirmwareVersion(firmwareVersion: String) {
        equilState?.firmwareVersion = firmwareVersion
        storePodState()
    }

    fun setRate(rate: Float) {
        equilState?.rate = rate
        storePodState()
    }

    fun getHistoryIndex(): Int? = equilState?.historyIndex

    fun setHistoryIndex(historyIndex: Int) {
        equilState?.historyIndex = historyIndex
        storePodState()
    }

    fun setActivationProgress(activationProgress: ActivationProgress) {
        equilState?.activationProgress = activationProgress
        storePodState()
    }

    fun getActivationProgress(): ActivationProgress {
        if (hasPodState()) {
            return Optional.ofNullable<ActivationProgress>(equilState?.activationProgress).orElse(ActivationProgress.NONE)
        }
        return ActivationProgress.NONE
    }

    fun isActivationCompleted(): Boolean {
        return getActivationProgress() == ActivationProgress.COMPLETED
    }

    fun getStartHistoryIndex(): Int? = equilState?.startHistoryIndex

    fun setStartHistoryIndex(startHistoryIndex: Int) {
        equilState?.startHistoryIndex = startHistoryIndex
        storePodState()
    }

    class EquilState {

        var activationProgress: ActivationProgress? = null
        var serialNumber: String? = null
        var address: String? = null
        var firmwareVersion: String? = null

        var lastDataTime: Long = 0
        var currentInsulin = 0
        var startInsulin = 0
        var battery = 0
        var tempBasal: EquilTempBasalRecord? = null
        var bolusRecord: EquilBolusRecord? = null
        var runMode: RunMode? = null
        var rate = 0f
        var historyIndex = 0

        var bluetoothConnectionState: BluetoothConnectionState? = BluetoothConnectionState.DISCONNECTED
        var startHistoryIndex = 0
        var basalSchedule: BasalSchedule? = null
    }

    fun setRunMode(mode: Int) {
        when (mode) {
            0    -> setRunMode(RunMode.SUSPEND)
            1    -> setRunMode(RunMode.RUN)
            2    -> setRunMode(RunMode.STOP)
            else -> setRunMode(RunMode.SUSPEND)
        }
        rxBus.send(EventEquilModeChanged())
    }

    fun setInsulinChange(status: Int) {
        if (status == 1) {
            rxBus.send(EventEquilInsulinChanged())
        }
    }

    fun decodeHistory(data: ByteArray) {
        var year = data[6].toInt() and 0xff
        year = year + 2000

        val month = data[7].toInt() and 0xff
        val day = data[8].toInt() and 0xff
        val hour = data[9].toInt() and 0xff
        val min = data[10].toInt() and 0xff
        val second = data[11].toInt() and 0xff
        //a5e207590501 17070e100f161100000000007d0204080000
        //ae6ae9100501 17070e100f16 1100000000007d0204080000
        val battery = data[12].toInt() and 0xff
        val insulin = data[13].toInt() and 0xff
        val rate = bytesToInt(data[15], data[14])
        val largeRate = bytesToInt(data[17], data[16])
        val index = bytesToInt(data[19], data[18])

        val port = data[20].toInt() and 0xff
        val type = data[21].toInt() and 0xff
        val level = data[22].toInt() and 0xff
        val parm = data[23].toInt() and 0xff
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, min)
        calendar.set(Calendar.SECOND, second)
        calendar.set(Calendar.MILLISECOND, 0)
        val equilHistoryPump = EquilHistoryPump()
        equilHistoryPump.battery = battery
        equilHistoryPump.insulin = insulin
        equilHistoryPump.rate = rate
        equilHistoryPump.largeRate = largeRate
        equilHistoryPump.timestamp = System.currentTimeMillis()
        equilHistoryPump.eventTimestamp = (calendar.getTimeInMillis() + index)
        equilHistoryPump.port = port
        equilHistoryPump.type = type
        equilHistoryPump.level = level
        equilHistoryPump.parm = parm
        equilHistoryPump.eventIndex = index
        equilHistoryPump.serialNumber = getSerialNumber()
        val id = equilHistoryPumpDao.insert(equilHistoryPump)
        aapsLogger.debug(LTag.PUMPCOMM, "decodeHistory insert id {}", id)
        rxBus.send(EventEquilDataChanged())
    }

    fun decodeData(data: ByteArray, saveData: Boolean) {
        var year = data[11].toInt() and 0xFF
        year = year + 2000
        val month = data[12].toInt() and 0xff
        val day = data[13].toInt() and 0xff
        val hour = data[14].toInt() and 0xff
        val min = data[15].toInt() and 0xff
        val second = data[16].toInt() and 0xff
        val battery = data[17].toInt() and 0xff
        val insulin = data[18].toInt() and 0xff
        val rate1 = bytesToInt(data[20], data[19])
        val rate = internalDecodeSpeedToUH(rate1)
        //val largeRate = bytesToInt(data[22], data[21]).toFloat()
        val historyIndex = bytesToInt(data[24], data[23])
        val currentIndex = getHistoryIndex()
        val port = data[25].toInt() and 0xff
        val level = data[26].toInt() and 0xff
        val parm = data[27].toInt() and 0xff
        val errorTips = getEquilError(port, level, parm)
        if (!TextUtils.isEmpty(errorTips) && currentIndex != historyIndex) {
            showNotification(
                Notification.FAILED_UPDATE_PROFILE,
                errorTips,
                Notification.NORMAL, app.aaps.core.ui.R.raw.alarm
            )
            if (saveData) {
                val time = System.currentTimeMillis()
                val equilHistoryRecord = EquilHistoryRecord(EquilHistoryRecord.EventType.EQUIL_ALARM, time, getSerialNumber())
                equilHistoryRecord.resolvedAt = System.currentTimeMillis()
                equilHistoryRecord.resolvedStatus = ResolvedResult.SUCCESS
                equilHistoryRecord.note = errorTips
                equilHistoryRecordDao.insert(equilHistoryRecord)
            }

        }
        if (!TextUtils.isEmpty(errorTips)) {
            rxBus.send(EventEquilAlarm(errorTips))
        }
        aapsLogger.debug(
            LTag.PUMPCOMM, "decodeData historyIndex {} errorTips {} port:{} level:{} " +
                "parm:{}",
            historyIndex,
            errorTips, port, level, parm
        )
        setHistoryIndex(historyIndex)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, min)
        calendar.set(Calendar.SECOND, second)
        setLastDataTime(System.currentTimeMillis())
        setCurrentInsulin(insulin)
        setBattery(battery)
        setRate(rate)
        rxBus.send(EventEquilDataChanged())
    }

    companion object {

        private fun createGson(): Gson {
            val gsonBuilder = GsonBuilder()
                .registerTypeAdapter(DateTime::class.java, JsonSerializer { dateTime: DateTime?, typeOfSrc: Type?, context: JsonSerializationContext? -> JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)) })
                .registerTypeAdapter(DateTime::class.java, JsonDeserializer { json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext? -> ISODateTimeFormat.dateTime().parseDateTime(json!!.asString) })
                .registerTypeAdapter(DateTimeZone::class.java, JsonSerializer { timeZone: DateTimeZone?, typeOfSrc: Type?, context: JsonSerializationContext? -> JsonPrimitive(timeZone!!.id) })
                .registerTypeAdapter(DateTimeZone::class.java, JsonDeserializer { json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext? -> DateTimeZone.forID(json!!.asString) })

            return gsonBuilder.create()
        }
    }
}
