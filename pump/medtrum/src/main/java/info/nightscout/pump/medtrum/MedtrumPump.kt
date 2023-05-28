package info.nightscout.pump.medtrum

import android.util.Base64
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.comm.enums.AlarmSetting
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

@Singleton
class MedtrumPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val temporaryBasalStorage: TemporaryBasalStorage
) {

    // Connection state flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionStateFlow: StateFlow<ConnectionState> = _connectionState
    var connectionState: ConnectionState
        get() = _connectionState.value
        set(value) {
            _connectionState.value = value
        }

    /** Patch activated state, mainly for UI, but also controls the connection flow,
     *  if patch is not activated, AAPS cannot connect to the pump, we can then connect trough the activation flow.
     *  Note: this is also saved in SP, by the set functions
     */
    private var _patchActivated = false
    val patchActivated: Boolean
        get() = _patchActivated

    // Pump state flow
    private val _pumpState = MutableStateFlow(MedtrumPumpState.NONE)
    val pumpStateFlow: StateFlow<MedtrumPumpState> = _pumpState
    var pumpState: MedtrumPumpState
        get() = _pumpState.value
        set(value) {
            _pumpState.value = value
        }

    // Prime progress as state flow
    private val _primeProgress = MutableStateFlow(0)
    val primeProgressFlow: StateFlow<Int> = _primeProgress
    var primeProgress: Int
        get() = _primeProgress.value
        set(value) {
            _primeProgress.value = value
        }
        
    private val _lastBasalRate = MutableStateFlow(0.0)
    val lastBasalRateFlow: StateFlow<Double> = _lastBasalRate
    var lastBasalRate: Double
        get() = _lastBasalRate.value
        set(value) {
            _lastBasalRate.value = value
        }

    private val _reservoir = MutableStateFlow(0.0)
    val reservoirFlow: StateFlow<Double> = _reservoir
    var reservoir: Double
        get() = _reservoir.value
        set(value) {
            _reservoir.value = value
        }
    
    /** Stuff stored in SP */
    private var _patchSessionToken = 0L
    var patchSessionToken: Long
        get() = _patchSessionToken
        set(value) {
            _patchSessionToken = value
            sp.putLong(R.string.key_session_token, value)
        }

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
            sp.putString(R.string.key_actual_basal_profile, encodedString?: "")
        }

    private var _lastBasalType: MutableStateFlow<BasalType> = MutableStateFlow(BasalType.NONE)
    val lastBasalTypeFlow: StateFlow<BasalType> = _lastBasalType
    var lastBasalType: BasalType
        get() = _lastBasalType.value
        set(value) {
            _lastBasalType.value = value
            sp.putInt(R.string.key_last_basal_type, value.ordinal) // TODO is this still needed in SP?
        }

    private var _pumpSN = 0L
    val pumpSN: Long
        get() = _pumpSN

    val pumpType: PumpType = PumpType.MEDTRUM_NANO // TODO, type based on pumpSN or pump activation/connection

    var lastTimeReceivedFromPump = 0L // Time in seconds!
    var suspendTime = 0L // Time in seconds!
    var patchStartTime = 0L // Time in seconds!
    var patchAge = 0L // Time in seconds!


    var batteryVoltage_A = 0.0
    var batteryVoltage_B = 0.0

    var alarmFlags = 0
    var alarmParameter = 0

    // bolus status
    var bolusingTreatment: EventOverviewBolusProgress.Treatment? = null // actually delivered treatment
    var bolusAmountToBeDelivered = 0.0 // amount to be delivered
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    var bolusStopped = false // bolus finished
    var bolusStopForced = false // bolus forced to stop by user
    var bolusDone = false // success end

    // Last basal status update 
    // TODO: Save this in SP?
    
    var lastBasalSequence = 0
    var lastBasalPatchId = 0L
    var lastBasalStartTime = 0L

    val baseBasalRate: Double
        get() = getCurrentHourlyBasalFromMedtrumProfileArray(actualBasalProfile)

    // TBR status
    val tempBasalInProgress: Boolean
        get() = lastBasalType == BasalType.ABSOLUTE_TEMP || lastBasalType == BasalType.RELATIVE_TEMP
    val tempBasalAbsoluteRate: Double
        get() = if (tempBasalInProgress) lastBasalRate else 0.0

    // Last stop status update
    var lastStopSequence = 0
    var lastStopPatchId = 0L

    // TODO set these setting on init
    // User settings (desired values, to be set on pump)
    var desiredPatchExpiration = false
    var desiredAlarmSetting = AlarmSetting.LIGHT_VIBRATE_AND_BEEP.code
    var desiredHourlyMaxInsulin: Int = 40
    var desiredDailyMaxInsulin: Int = 180

    init {
        // Load stuff from SP
        _patchActivated = sp.getBoolean(R.string.key_patch_activated, false)
        _patchSessionToken = sp.getLong(R.string.key_session_token, 0L)
        _currentSequenceNumber = sp.getInt(R.string.key_current_sequence_number, 0)
        _patchId = sp.getLong(R.string.key_patch_id, 0L)
        _syncedSequenceNumber = sp.getInt(R.string.key_synced_sequence_number, 0)
        lastBasalType = enumValues<BasalType>()[sp.getInt(R.string.key_last_basal_type, 0)] // TODO: is this nice?

        val encodedString = sp.getString(R.string.key_actual_basal_profile, "0")
        try {
            _actualBasalProfile = Base64.decode(encodedString, Base64.DEFAULT)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error decoding basal profile from SP: $encodedString")
        }

        if (patchActivated) {
            aapsLogger.debug(LTag.PUMP, "changePump: Patch is already activated, setting as ACTIVE")
            // Set inital status as active will be updated on first connection       
            pumpState = MedtrumPumpState.ACTIVE
        }

        loadUserSettingsFromSP()
    }

    fun loadUserSettingsFromSP() {
        // TODO
        // desiredPatchExpiration = sp.getBoolean(R.string.key_patch_expiration, false)
        // desiredAlarmSetting = sp.getInt(R.string.key_alarm_setting, AlarmSetting.LIGHT_VIBRATE_AND_BEEP.code)
        // desiredHourlyMaxInsulin = sp.getInt(R.string.key_hourly_max_insulin, 40)
        // desiredDailyMaxInsulin = sp.getInt(R.string.key_daily_max_insulin, 180)

        try {
            _pumpSN = sp.getString(info.nightscout.pump.medtrum.R.string.key_snInput, " ").toLong(radix = 16)
        } catch (e: NumberFormatException) {
            aapsLogger.debug(LTag.PUMP, "changePump: Invalid input!")
        }
    }

    fun setPatchActivatedState(activated: Boolean) {
        aapsLogger.debug(LTag.PUMP, "setPatchActivatedState: $activated")
        _patchActivated = activated
        sp.putBoolean(R.string.key_patch_activated, activated)
    }

    /** When the activation/deactivation screen, and the connection flow needs to be controlled,
     *  this can be used to set the ActivatedState without saving to SP, So when app is force closed the state is still maintained
     */
    fun setPatchActivatedStateTemp(activated: Boolean) {
        aapsLogger.debug(LTag.PUMP, "setPatchActivatedStateTemp: $activated")
        _patchActivated = activated
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

    fun getCurrentHourlyBasalFromMedtrumProfileArray(basalProfile: ByteArray): Double {
        val basalCount = basalProfile[0].toInt()
        var basal = 0.0
        if (basalProfile.size < 4 || (basalProfile.size - 1) % 3 != 0 || basalCount > 24) {
            aapsLogger.debug(LTag.PUMP, "getCurrentHourlyBasalFromMedtrumProfileArray: No valid basal profile set")
            return basal
        }

        val hourOfDayMinutes = dateUtil.dateAndTimeString(dateUtil.now()).substring(11, 13).toInt() * 60 + dateUtil.dateAndTimeString(dateUtil.now()).substring(14, 16).toInt()

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
                aapsLogger.debug(LTag.PUMP, "getCurrentHourlyBasalFromMedtrumProfileArray: basal: $basal")
                break
            }
            // aapsLogger.debug(LTag.PUMP, "getCurrentHourlyBasalFromMedtrumProfileArray: rate: $rate, startMinutes: $startMinutes, endMinutes: $endMinutes")
        }
        return basal
    }

    fun handleBasalStatusUpdate(basalType: BasalType, basalValue: Double, basalSequence: Int, basalPatchId: Long, basalStartTime: Long) {
        handleBasalStatusUpdate(basalType, basalValue, basalSequence, basalPatchId, basalStartTime, dateUtil.now())
    }

    fun handleBolusStatusUpdate(bolusType: Int, bolusCompleted: Boolean, amountDelivered: Double) {
        aapsLogger.debug(LTag.PUMP, "handleBolusStatusUpdate: bolusType: $bolusType bolusCompleted: $bolusCompleted amountDelivered: $amountDelivered")
        bolusProgressLastTimeStamp = dateUtil.now()
        if (bolusCompleted) {
            bolusDone = true
            bolusingTreatment?.insulin = amountDelivered
        } else {
            bolusingTreatment?.insulin = amountDelivered     
        }
    }

    fun handleBasalStatusUpdate(basalType: BasalType, basalRate: Double, basalSequence: Int, basalPatchId: Long, basalStartTime: Long, receivedTime: Long) {
        aapsLogger.debug(
            LTag.PUMP,
            "handleBasalStatusUpdate: basalType: $basalType basalValue: $basalRate basalSequence: $basalSequence basalPatchId: $basalPatchId basalStartTime: $basalStartTime " + "receivedTime: $receivedTime"
        )
        val expectedTemporaryBasal = pumpSync.expectedPumpState()?.temporaryBasal
        if (basalType.isTempBasal() && expectedTemporaryBasal?.pumpId != basalStartTime) {
            // Note: temporaryBasalInfo will be removed from temporaryBasalStorage after this call
            val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)

            // If duration is unknown, no way to get it now, set patch lifetime as duration
            val duration = temporaryBasalInfo?.duration ?: T.mins(4800).msecs()
            val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = basalStartTime,
                rate = basalRate, // TODO: Support percent here, this will break things? Check if this is correct
                duration = duration,
                isAbsolute = (basalType == BasalType.ABSOLUTE_TEMP),
                type = temporaryBasalInfo?.type,
                pumpId = basalStartTime,
                pumpType = pumpType,
                pumpSerial = pumpSN.toString(radix = 16)
            )
            aapsLogger.debug(
                LTag.PUMPCOMM,
                "handleBasalStatusUpdate: ${if (newRecord) "**NEW** " else ""}EVENT TEMP_START ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) " + "Rate: $basalRate Duration: ${duration} temporaryBasalInfo: $temporaryBasalInfo, expectedTemporaryBasal: $expectedTemporaryBasal"
            )
        } else if (basalType.isSuspendedByPump() && expectedTemporaryBasal?.pumpId != basalStartTime) {
            val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = basalStartTime,
                rate = 0.0,
                duration = T.mins(4800).msecs(), // TODO MAGIC NUMBER
                isAbsolute = true,
                type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                pumpId = basalStartTime,
                pumpType = pumpType,
                pumpSerial = pumpSN.toString(radix = 16)
            )
            aapsLogger.debug(
                LTag.PUMPCOMM,
                "handleBasalStatusUpdate: ${if (newRecord) "**NEW** " else ""}EVENT TEMP_START ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) expectedTemporaryBasal: $expectedTemporaryBasal"
            )
        } else if (basalType == BasalType.NONE && expectedTemporaryBasal?.pumpId != basalStartTime) { // Also some sort of suspend or unkown by pump
            val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = basalStartTime,
                rate = basalRate,
                duration = T.mins(4800).msecs(), // TODO MAGIC NUMBER
                isAbsolute = true,
                type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                pumpId = basalStartTime,
                pumpType = pumpType,
                pumpSerial = pumpSN.toString(radix = 16)
            )
            aapsLogger.debug(
                LTag.PUMPCOMM,
                "handleBasalStatusUpdate: ${if (newRecord) "**NEW** " else ""}EVENT TEMP_START ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) expectedTemporaryBasal: $expectedTemporaryBasal"
            )
        }

        // Update medtrum pump state
        lastBasalType = basalType
        lastBasalRate = basalRate
        lastBasalSequence = basalSequence
        if (basalSequence > currentSequenceNumber) {
            currentSequenceNumber = basalSequence
        }
        lastBasalPatchId = basalPatchId
        if (basalPatchId != patchId) {
            aapsLogger.error(LTag.PUMP, "handleBasalStatusUpdate: WTF? PatchId in status update does not match current patchId!")
        }
        lastBasalStartTime = basalStartTime
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
}
