package info.nightscout.pump.medtrum

import android.util.Base64
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.comm.enums.AlarmSetting
import info.nightscout.pump.medtrum.comm.enums.AlarmState
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.pump.medtrum.util.MedtrumSnUtil
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

@Singleton
class MedtrumPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val temporaryBasalStorage: TemporaryBasalStorage
) {

    companion object {

        const val FAKE_TBR_LENGTH = 4800L
    }

    // Connection state flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionStateFlow: StateFlow<ConnectionState> = _connectionState
    var connectionState: ConnectionState
        get() = _connectionState.value
        set(value) {
            _connectionState.value = value
        }

    // Pump state flow
    private val _pumpState = MutableStateFlow(MedtrumPumpState.NONE)
    val pumpStateFlow: StateFlow<MedtrumPumpState> = _pumpState
    var pumpState: MedtrumPumpState
        get() = _pumpState.value
        set(value) {
            _pumpState.value = value
            sp.putInt(R.string.key_pump_state, value.state.toInt())
        }

    // Active alarms
    private var _activeAlarms: EnumSet<AlarmState> = EnumSet.noneOf(AlarmState::class.java)
    var activeAlarms: EnumSet<AlarmState>
        get() = _activeAlarms
        set(value) {
            _activeAlarms = value
        }

    // Prime progress as state flow
    private val _primeProgress = MutableStateFlow(0)
    val primeProgressFlow: StateFlow<Int> = _primeProgress
    var primeProgress: Int
        get() = _primeProgress.value
        set(value) {
            _primeProgress.value = value
        }

    private var _lastBasalType: MutableStateFlow<BasalType> = MutableStateFlow(BasalType.NONE)
    val lastBasalTypeFlow: StateFlow<BasalType> = _lastBasalType
    val lastBasalType: BasalType
        get() = _lastBasalType.value

    private val _lastBasalRate = MutableStateFlow(0.0)
    val lastBasalRateFlow: StateFlow<Double> = _lastBasalRate
    val lastBasalRate: Double
        get() = _lastBasalRate.value

    private val _reservoir = MutableStateFlow(0.0)
    val reservoirFlow: StateFlow<Double> = _reservoir
    var reservoir: Double
        get() = _reservoir.value
        set(value) {
            _reservoir.value = value
        }

    var batteryVoltage_A = 0.0 // Not used in UI
    private val _batteryVoltage_B = MutableStateFlow(0.0)
    val batteryVoltage_BFlow: StateFlow<Double> = _batteryVoltage_B
    var batteryVoltage_B: Double
        get() = _batteryVoltage_B.value
        set(value) {
            _batteryVoltage_B.value = value
        }

    /** Stuff stored in SP */
    private var _patchSessionToken = 0L
    var patchSessionToken: Long
        get() = _patchSessionToken
        set(value) {
            _patchSessionToken = value
            sp.putLong(R.string.key_session_token, value)
        }

    // Note: This is not always incremented by the pump, so it is not a reliable indicator for activation unless we reset it on deactivation
    // see resetPatchParameters()
    private var _patchId = 0L
    var patchId: Long
        get() = _patchId
        set(value) {
            _patchId = value
            sp.putLong(R.string.key_patch_id, value)
        }

    private var _currentSequenceNumber = 0
    var currentSequenceNumber: Int
        get() = _currentSequenceNumber
        set(value) {
            _currentSequenceNumber = value
            sp.putInt(R.string.key_current_sequence_number, value)
        }

    private var _syncedSequenceNumber = 0
    var syncedSequenceNumber: Int
        get() = _syncedSequenceNumber
        set(value) {
            _syncedSequenceNumber = value
            sp.putInt(R.string.key_synced_sequence_number, value)
        }

    private var _actualBasalProfile = byteArrayOf(0)
    var actualBasalProfile: ByteArray
        get() = _actualBasalProfile
        set(value) {
            _actualBasalProfile = value
            val encodedString = Base64.encodeToString(value, Base64.DEFAULT)
            sp.putString(R.string.key_actual_basal_profile, encodedString ?: "")
        }

    private var _lastBolusTime = 0L // Time in ms!
    var lastBolusTime: Long
        get() = _lastBolusTime
        set(value) {
            _lastBolusTime = value
            sp.putLong(R.string.key_last_bolus_time, value)
        }

    private var _lastBolusAmount = 0.0
    var lastBolusAmount: Double
        get() = _lastBolusAmount
        set(value) {
            _lastBolusAmount = value
            sp.putDouble(R.string.key_last_bolus_amount, value)
        }

    private var _lastConnection = 0L // Time in ms!
    var lastConnection: Long
        get() = _lastConnection
        set(value) {
            _lastConnection = value
            sp.putLong(R.string.key_last_connection, value)
        }

    private var _deviceType: Int = 0 // As reported by pump
    var deviceType: Int
        get() = _deviceType
        set(value) {
            _deviceType = value
            sp.putInt(R.string.key_device_type, value)
        }

    private var _swVersion: String = "" // As reported by pump
    var swVersion: String
        get() = _swVersion
        set(value) {
            _swVersion = value
            sp.putString(R.string.key_sw_version, value)
        }

    private var _patchStartTime = 0L // Time in ms!
    var patchStartTime: Long
        get() = _patchStartTime
        set(value) {
            _patchStartTime = value
            sp.putLong(R.string.key_patch_start_time, value)
        }

    private var _pumpTimeZoneOffset = 0 // As reported by pump
    var pumpTimeZoneOffset: Int
        get() = _pumpTimeZoneOffset
        set(value) {
            _pumpTimeZoneOffset = value
            sp.putInt(R.string.key_pump_time_zone_offset, value)
        }

    private var _pumpSN = 0L
    val pumpSN: Long
        get() = _pumpSN

    val pumpSNFromSP: Long
        get() =
            try {
                sp.getString(R.string.key_sn_input, "0").toLong(radix = 16)
            } catch (e: NumberFormatException) {
                aapsLogger.debug(LTag.PUMP, "pumpSNFromSP: Invalid input!")
                0L
            }

    var needCheckTimeUpdate = false
    var lastTimeReceivedFromPump = 0L // Time in ms!
    var suspendTime = 0L // Time in ms!
    var patchAge = 0L // Time in seconds?! // As reported by pump, not used (yet)

    // bolus status
    private var _bolusStartTime = 0L // Time in ms!
    var bolusStartTime: Long
        get() = _bolusStartTime
        set(value) {
            _bolusStartTime = value
            sp.putLong(R.string.key_bolus_start_time, value)
        }
    private var _bolusAmountToBeDelivered = 0.0 // amount to be delivered
    var bolusAmountToBeDelivered: Double
        get() = _bolusAmountToBeDelivered
        set(value) {
            _bolusAmountToBeDelivered = value
            sp.putDouble(R.string.key_bolus_amount_to_be_delivered, value)
        }
    var bolusingTreatment: EventOverviewBolusProgress.Treatment? = null // actually delivered treatment
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    var bolusStopped = false // bolus stopped by user
    var bolusDone = false // Bolus completed or stopped on pump

    private val _bolusAmountDelivered = MutableStateFlow(0.0)
    val bolusAmountDeliveredFlow: StateFlow<Double> = _bolusAmountDelivered

    // Last basal status update (from pump)
    private var _lastBasalSequence = 0
    val lastBasalSequence: Int
        get() = _lastBasalSequence

    private var _lastBasalPatchId = 0L
    val lastBasalPatchId: Long
        get() = _lastBasalPatchId

    private var _lastBasalStartTime = 0L
    val lastBasalStartTime: Long
        get() = _lastBasalStartTime

    val baseBasalRate: Double
        get() = getHourlyBasalFromMedtrumProfileArray(actualBasalProfile, dateUtil.now())

    // TBR status
    val tempBasalInProgress: Boolean
        get() = lastBasalType == BasalType.ABSOLUTE_TEMP || lastBasalType == BasalType.RELATIVE_TEMP
    val tempBasalAbsoluteRate: Double
        get() = if (tempBasalInProgress) lastBasalRate else 0.0

    // Last stop status update
    var lastStopSequence = 0
    var lastStopPatchId = 0L

    // User settings (desired values, to be set on pump)
    var desiredPatchExpiration = false
    var desiredAlarmSetting = AlarmSetting.LIGHT_VIBRATE_AND_BEEP
    var desiredHourlyMaxInsulin: Int = 40
    var desiredDailyMaxInsulin: Int = 180

    fun pumpType(): PumpType = pumpType(deviceType)

    fun pumpType(type: Int): PumpType =
        when (type) {
            MedtrumSnUtil.MD_0201, MedtrumSnUtil.MD_8201 -> PumpType.MEDTRUM_NANO
            MedtrumSnUtil.MD_8301                        -> PumpType.MEDTRUM_300U
            else                                         -> PumpType.MEDTRUM_UNTESTED
        }

    fun loadVarsFromSP() {
        // Load stuff from SP
        _patchSessionToken = sp.getLong(R.string.key_session_token, 0L)
        _lastConnection = sp.getLong(R.string.key_last_connection, 0L)
        _lastBolusTime = sp.getLong(R.string.key_last_bolus_time, 0L)
        _lastBolusAmount = sp.getDouble(R.string.key_last_bolus_amount, 0.0)
        _currentSequenceNumber = sp.getInt(R.string.key_current_sequence_number, 0)
        _patchId = sp.getLong(R.string.key_patch_id, 0L)
        _syncedSequenceNumber = sp.getInt(R.string.key_synced_sequence_number, 0)
        _pumpState.value = MedtrumPumpState.fromByte(sp.getInt(R.string.key_pump_state, MedtrumPumpState.NONE.state.toInt()).toByte())
        _deviceType = sp.getInt(R.string.key_device_type, 0)
        _swVersion = sp.getString(R.string.key_sw_version, "")
        _patchStartTime = sp.getLong(R.string.key_patch_start_time, 0L)
        _pumpTimeZoneOffset = sp.getInt(R.string.key_pump_time_zone_offset, 0)
        _bolusStartTime = sp.getLong(R.string.key_bolus_start_time, 0L)
        _bolusAmountToBeDelivered = sp.getDouble(R.string.key_bolus_amount_to_be_delivered, 0.0)

        loadActiveAlarms()

        val encodedString = sp.getString(R.string.key_actual_basal_profile, "0")
        try {
            _actualBasalProfile = Base64.decode(encodedString, Base64.DEFAULT)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PUMP, "Error decoding basal profile from SP: $encodedString")
        }

        loadUserSettingsFromSP()
    }

    fun loadUserSettingsFromSP() {
        desiredPatchExpiration = sp.getBoolean(R.string.key_patch_expiration, false)
        val alarmSettingCode = sp.getString(R.string.key_alarm_setting, AlarmSetting.LIGHT_VIBRATE_AND_BEEP.code.toString()).toByte()
        desiredAlarmSetting = AlarmSetting.values().firstOrNull { it.code == alarmSettingCode } ?: AlarmSetting.LIGHT_VIBRATE_AND_BEEP
        desiredHourlyMaxInsulin = sp.getInt(R.string.key_hourly_max_insulin, 40)
        desiredDailyMaxInsulin = sp.getInt(R.string.key_daily_max_insulin, 180)
        _pumpSN = pumpSNFromSP

    }

    fun buildMedtrumProfileArray(nsProfile: Profile): ByteArray? {
        val list = nsProfile.getBasalValues()
        var basals = byteArrayOf()
        for (item in list) {
            val rate = round(item.value / 0.05).toInt()
            val time = item.timeAsSeconds / 60
            if (rate > 0xFFF || time > 0xFFF) {
                aapsLogger.error(LTag.PUMP, "buildMedtrumProfileArray: rate or time too large: $rate, $time")
                return null
            }
            basals += ((rate shl 12) + time).toByteArray(3)
            aapsLogger.debug(LTag.PUMP, "buildMedtrumProfileArray: value: ${item.value} time: ${item.timeAsSeconds}, converted: $rate, $time")
        }
        return (list.size).toByteArray(1) + basals
    }

    fun getHourlyBasalFromMedtrumProfileArray(basalProfile: ByteArray, timestamp: Long): Double {
        val basalCount = basalProfile[0].toInt()
        var basal = 0.0
        if (basalProfile.size < 4 || (basalProfile.size - 1) % 3 != 0 || basalCount > 24) {
            aapsLogger.debug(LTag.PUMP, "getHourlyBasalFromMedtrumProfileArray: No valid basal profile set")
            return basal
        }

        val date = GregorianCalendar()
        date.timeInMillis = timestamp
        val hourOfDayMinutes = date[GregorianCalendar.HOUR_OF_DAY] * 60 + date[GregorianCalendar.MINUTE]

        for (index in 0 until basalCount) {
            val currentIndex = 1 + (index * 3)
            val nextIndex = currentIndex + 3
            val rateAndTime = basalProfile.copyOfRange(currentIndex, nextIndex).toInt()
            val rate = (rateAndTime shr 12) * 0.05
            val startMinutes = rateAndTime and 0xFFF

            val endMinutes = if (nextIndex < basalProfile.size) {
                val nextRateAndTime = basalProfile.copyOfRange(nextIndex, nextIndex + 3).toInt()
                nextRateAndTime and 0xFFF
            } else {
                24 * 60
            }

            if (hourOfDayMinutes in startMinutes until endMinutes) {
                basal = rate
                aapsLogger.debug(LTag.PUMP, "getHourlyBasalFromMedtrumProfileArray: basal: $basal")
                break
            }
        }
        return basal
    }

    fun handleBolusStatusUpdate(bolusType: Int, bolusCompleted: Boolean, amountDelivered: Double) {
        aapsLogger.debug(LTag.PUMP, "handleBolusStatusUpdate: bolusType: $bolusType bolusCompleted: $bolusCompleted amountDelivered: $amountDelivered")
        bolusProgressLastTimeStamp = dateUtil.now()
        _bolusAmountDelivered.value = amountDelivered
        bolusingTreatment?.insulin = amountDelivered
        bolusDone = bolusCompleted
    }

    fun handleBasalStatusUpdate(basalType: BasalType, basalValue: Double, basalSequence: Int, basalPatchId: Long, basalStartTime: Long) {
        handleBasalStatusUpdate(basalType, basalValue, basalSequence, basalPatchId, basalStartTime, dateUtil.now())
    }

    fun handleBasalStatusUpdate(basalType: BasalType, basalRate: Double, basalSequence: Int, basalPatchId: Long, basalStartTime: Long, receivedTime: Long) {
        aapsLogger.debug(
            LTag.PUMP,
            "handleBasalStatusUpdate: basalType: $basalType basalValue: $basalRate basalSequence: $basalSequence basalPatchId: $basalPatchId basalStartTime: $basalStartTime " + "receivedTime: $receivedTime"
        )
        @Suppress("UNNECESSARY_SAFE_CALL") // Safe call to allow mocks to return null
        val expectedTemporaryBasal = pumpSync.expectedPumpState()?.temporaryBasal
        when {
            basalType.isTempBasal() && expectedTemporaryBasal?.pumpId != basalStartTime                                                                     -> {
                // Note: temporaryBasalInfo will be removed from temporaryBasalStorage after this call
                val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)

                // If duration is unknown, no way to get it now, set patch lifetime as duration
                val duration = temporaryBasalInfo?.duration ?: T.mins(FAKE_TBR_LENGTH).msecs()
                val adjustedBasalRate = if (basalType == BasalType.ABSOLUTE_TEMP) {
                    basalRate
                } else {
                    (basalRate / baseBasalRate) * 100 // calculate the percentage of the original basal rate
                }
                val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = basalStartTime,
                    rate = adjustedBasalRate,
                    duration = duration,
                    isAbsolute = (basalType == BasalType.ABSOLUTE_TEMP),
                    type = temporaryBasalInfo?.type,
                    pumpId = basalStartTime,
                    pumpType = pumpType(),
                    pumpSerial = pumpSN.toString(radix = 16)
                )
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "handleBasalStatusUpdate: ${newRecordInfo(newRecord)}EVENT TEMP_START ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) " + "Rate: $basalRate Duration: ${duration} temporaryBasalInfo: $temporaryBasalInfo, expectedTemporaryBasal: $expectedTemporaryBasal"
                )
            }

            basalType.isSuspendedByPump() && expectedTemporaryBasal?.pumpId != basalStartTime                                                               -> {
                val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = basalStartTime,
                    rate = 0.0,
                    duration = T.mins(FAKE_TBR_LENGTH).msecs(),
                    isAbsolute = true,
                    type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                    pumpId = basalStartTime,
                    pumpType = pumpType(),
                    pumpSerial = pumpSN.toString(radix = 16)
                )
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "handleBasalStatusUpdate: ${newRecordInfo(newRecord)}EVENT TEMP_START ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) expectedTemporaryBasal: $expectedTemporaryBasal"
                )
            }

            basalType == BasalType.NONE && expectedTemporaryBasal?.rate != basalRate && expectedTemporaryBasal?.duration != T.mins(FAKE_TBR_LENGTH).msecs() -> {
                // Pump suspended, set fake TBR
                setFakeTBR()
            }

            basalType == BasalType.STANDARD                                                                                                                 -> {
                if (expectedTemporaryBasal != null) {
                    // Pump resumed, sync end
                    val success = pumpSync.syncStopTemporaryBasalWithPumpId(
                        timestamp = basalStartTime + 250, // Time of normal basal start = time of tbr end
                        endPumpId = basalStartTime + 250, // +250ms Make sure there is time between start and stop of TBR
                        pumpType = pumpType(),
                        pumpSerial = pumpSN.toString(radix = 16)
                    )
                    aapsLogger.debug(LTag.PUMPCOMM, "handleBasalStatusUpdate: EVENT TEMP_END ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) success: $success")
                }
            }
        }

        // Update medtrum pump state
        _lastBasalType.value = basalType
        _lastBasalRate.value = basalRate
        _lastBasalSequence = basalSequence
        if (basalSequence > currentSequenceNumber) {
            currentSequenceNumber = basalSequence
        }
        _lastBasalPatchId = basalPatchId
        if (basalPatchId != patchId) {
            aapsLogger.error(LTag.PUMP, "handleBasalStatusUpdate: WTF? PatchId in status update does not match current patchId!")
        }
        _lastBasalStartTime = basalStartTime
    }

    fun handleStopStatusUpdate(stopSequence: Int, stopPatchId: Long) {
        aapsLogger.debug(LTag.PUMP, "handleStopStatusUpdate: stopSequence: $stopSequence stopPatchId: $stopPatchId")
        lastStopSequence = stopSequence
        if (stopSequence > currentSequenceNumber) {
            currentSequenceNumber = stopSequence
        }
        lastStopPatchId = stopPatchId
        if (stopPatchId != patchId) {
            aapsLogger.error(LTag.PUMP, "handleStopStatusUpdate: WTF? PatchId in status update does not match current patchId!")
        }
    }

    fun setFakeTBRIfNeeded() {
        val expectedTemporaryBasal = pumpSync.expectedPumpState().temporaryBasal
        if (expectedTemporaryBasal?.duration != T.mins(FAKE_TBR_LENGTH).msecs()) {
            setFakeTBR()
        }
    }

    private fun setFakeTBR() {
        val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = 0.0,
            duration = T.mins(FAKE_TBR_LENGTH).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = dateUtil.now(),
            pumpType = pumpType(),
            pumpSerial = pumpSN.toString(radix = 16)
        )
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "handleBasalStatusUpdate: ${newRecordInfo(newRecord)}EVENT TEMP_START (FAKE)"
        )
    }

    fun temporaryBasalToString(): String {
        if (!tempBasalInProgress) return ""
        return tempBasalAbsoluteRate.toString() + "U/h"
    }

    fun addAlarm(alarm: AlarmState) {
        activeAlarms.add(alarm)
        saveActiveAlarms()
    }

    fun removeAlarm(alarm: AlarmState) {
        activeAlarms.remove(alarm)
        saveActiveAlarms()
    }

    fun clearAlarmState() {
        activeAlarms.clear()
        saveActiveAlarms()
    }

    fun alarmStateToString(alarmState: AlarmState): String {
        val stringId = when (alarmState) {
            AlarmState.NONE                 -> R.string.alarm_none
            AlarmState.PUMP_LOW_BATTERY     -> R.string.alarm_pump_low_battery
            AlarmState.PUMP_LOW_RESERVOIR   -> R.string.alarm_pump_low_reservoir
            AlarmState.PUMP_EXPIRES_SOON    -> R.string.alarm_pump_expires_soon
            AlarmState.LOW_BG_SUSPENDED     -> R.string.alarm_low_bg_suspended
            AlarmState.LOW_BG_SUSPENDED2    -> R.string.alarm_low_bg_suspended2
            AlarmState.AUTO_SUSPENDED       -> R.string.alarm_auto_suspended
            AlarmState.HOURLY_MAX_SUSPENDED -> R.string.alarm_hourly_max_suspended
            AlarmState.DAILY_MAX_SUSPENDED  -> R.string.alarm_daily_max_suspended
            AlarmState.SUSPENDED            -> R.string.alarm_suspended
            AlarmState.PAUSED               -> R.string.alarm_paused
            AlarmState.OCCLUSION            -> R.string.alarm_occlusion
            AlarmState.EXPIRED              -> R.string.alarm_expired
            AlarmState.RESERVOIR_EMPTY      -> R.string.alarm_reservoir_empty
            AlarmState.PATCH_FAULT          -> R.string.alarm_patch_fault
            AlarmState.PATCH_FAULT2         -> R.string.alarm_patch_fault2
            AlarmState.BASE_FAULT           -> R.string.alarm_base_fault
            AlarmState.BATTERY_OUT          -> R.string.alarm_battery_out
            AlarmState.NO_CALIBRATION       -> R.string.alarm_no_calibration
        }
        return rh.gs(stringId)
    }

    fun resetPatchParameters() {
        patchId = 0
        syncedSequenceNumber = 1
        currentSequenceNumber = 1
    }

    fun handleNewPatch(newPatchId: Long, sequenceNumber: Int, newStartTime: Long) {
        patchId = newPatchId
        patchStartTime = newStartTime
        currentSequenceNumber = sequenceNumber // We are activated, set the new seq nr
        syncedSequenceNumber = 1 // Always start with 1
        // Sync cannula change
        pumpSync.insertTherapyEventIfNewWithTimestamp(
            timestamp = newStartTime,
            type = DetailedBolusInfo.EventType.CANNULA_CHANGE,
            pumpType = pumpType(),
            pumpSerial = pumpSN.toString(radix = 16)
        )
        pumpSync.insertTherapyEventIfNewWithTimestamp(
            timestamp = newStartTime,
            type = DetailedBolusInfo.EventType.INSULIN_CHANGE,
            pumpType = pumpType(),
            pumpSerial = pumpSN.toString(radix = 16)
        )
    }

    private fun saveActiveAlarms() {
        val alarmsStr = activeAlarms.joinToString(separator = ",") { it.name }
        sp.putString(R.string.key_active_alarms, alarmsStr)
    }

    private fun loadActiveAlarms() {
        val alarmsStr = sp.getString(R.string.key_active_alarms, "")
        activeAlarms = if (alarmsStr.isEmpty()) {
            EnumSet.noneOf(AlarmState::class.java)
        } else {
            alarmsStr.split(",")
                .mapNotNull { AlarmState.values().find { alarm -> alarm.name == it } }
                .let { EnumSet.copyOf(it) }
        }
    }

    private fun newRecordInfo(newRecord: Boolean): String {
        return "${if (newRecord) "**NEW** " else ""}"
    }
}
