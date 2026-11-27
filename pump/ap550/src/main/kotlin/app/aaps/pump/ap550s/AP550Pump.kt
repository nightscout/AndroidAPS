package app.aaps.pump.ap550s

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.ap550s.keys.Ap550LongNonKey
import app.aaps.pump.ap550s.keys.Ap550StringNonKey
import app.aaps.pump.ap550s.database.model.ConnectHistoryItem
import app.aaps.pump.ap550s.defs.ConnectStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SingleIn(AppScope::class)
class AP550Pump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val decimalFormatter: DecimalFormatter
) {

    enum class ErrorState(val code: Int) {
        NONE(0x00),
        SUSPENDED(0x01),
        DAILYMAX(0x02),
        BOLUSBLOCK(0x04),
        ORDERDELIVERING(0x08),
        NOPRIME(0x10);

        companion object {
            private val map = values().associateBy(ErrorState::code)
            operator fun get(value: Int) = map[value]
        }
    }

    // Values the rest of AAPS reads as flows live in a MutableStateFlow and are exposed again as
    // plain properties below, so every existing `ap550Pump.lastConnection = ...` write site keeps
    // working while the pump interface can hand core a StateFlow.
    private val lastConnectionFlowInternal = MutableStateFlow(0L)
    private val lastBolusTimeFlowInternal = MutableStateFlow<Long?>(null)
    private val lastBolusAmountFlowInternal = MutableStateFlow<Double?>(null)
    private val reservoirRemainingUnitsFlowInternal = MutableStateFlow(0.0)
    private val batteryRemainingFlowInternal = MutableStateFlow<Int?>(null)
    private val currentBasalFlowInternal = MutableStateFlow(0.0)

    var lastConnection: Long
        get() = lastConnectionFlowInternal.value
        set(value) {
            lastConnectionFlowInternal.value = value
        }

    val lastConnectionFlow: StateFlow<Long> get() = lastConnectionFlowInternal

    var lastBolusTime: Long
        get() = lastBolusTimeFlowInternal.value ?: 0
        set(value) {
            lastBolusTimeFlowInternal.value = value
        }

    val lastBolusTimeFlow: StateFlow<Long?> get() = lastBolusTimeFlowInternal

    var lastSettingsRead: Long = 0
    var lastSyncPumpTime: Long = 0
    var lastBolusStartTimestamp:Long = 0

    // Info
    var serialNumber = ""
    var shippingDate: Long = 0
    var shippingCountry = ""
    var bleModel = "" // RS v3:  like BPN-1.0.1
    var isNewPump = true // R only , providing model info
    var password = -1 // R, RSv1
    var initPair = false
    var paired = false
    var pairCodeResult: Int = 0

    var otherIndex = -1
    var basicIndex = -1
    var tempBasalIndex = -1
    var bolusIndex = -1
    var ventIndex = -1
    var cfgchangeIndex = -1
    var pumpIsFirstBindForVentResetRecord = false
    var pumpIsFirstBindForCfgChangeRecord = false
    var pumpMuteStatus = false

    var mac_address_for_pair: String = ""
    var device_name_for_pair: String = ""
    var sn_for_pair: String = ""

    // time
    private var pumpTime: Long = 0
    var zoneOffset: Int = 0 // i (hw 7+)

    fun setPumpTime(value: Long) {
        pumpTime = value
    }

    fun setPumpTime(value: Long, zoneOffset: Int) {
        // Store time according to timezone in phone
        val tz = DateTimeZone.getDefault()
        val instant = DateTime.now().millis
        val offsetInMilliseconds = tz.getOffset(instant).toLong()
        val offset = TimeUnit.MILLISECONDS.toHours(offsetInMilliseconds)
        pumpTime = value + T.hours(offset).msecs()
        // but save zone in pump
        this.zoneOffset = zoneOffset
    }

    fun resetPumpTime() {
        pumpTime = 0
    }

    fun getPumpTime() = pumpTime

    var hwModel = 0
    val usingUTC
        get() = hwModel >= 7
    val profile24
        get() = hwModel >= 7

    var protocol = 0
    var productCode = 0
    var errorState: ErrorState = ErrorState.NONE
    var isConfigUD = false
    var isExtendedBolusEnabled = false
    var isEasyModeEnabled = false

    // Status
    var pumpSuspended = false
    var AAPSResumeButtomTime: Long = 0
    var AAPSStopButtomTime: Long = 0
    var calculatorEnabled = false
    var dailyTotalUnits = 0.0
    var dailyTotalBolusUnits = 0.0 // RS only
    var dailyTotalBasalUnits = 0.0 // RS only
    var decRatio = 0 // RS v3: [%] for pump IOB calculation
    var maxDailyTotalUnits = 0
    var bolusStep = 0.05
    var basalStep = 0.025
    var iob = 0.0
    var bolusBlocked = false

    var reservoirRemainingUnits: Double
        get() = reservoirRemainingUnitsFlowInternal.value
        set(value) {
            reservoirRemainingUnitsFlowInternal.value = value
        }

    val reservoirRemainingUnitsFlow: StateFlow<Double> get() = reservoirRemainingUnitsFlowInternal

    var batteryRemaining: Int?
        get() = batteryRemainingFlowInternal.value
        set(value) {
            batteryRemainingFlowInternal.value = value
        }

    val batteryRemainingFlow: StateFlow<Int?> get() = batteryRemainingFlowInternal

    var lastBolusAmount: Double
        get() = lastBolusAmountFlowInternal.value ?: 0.0
        set(value) {
            lastBolusAmountFlowInternal.value = value
        }

    val lastBolusAmountFlow: StateFlow<Double?> get() = lastBolusAmountFlowInternal

    var currentBasal: Double
        get() = currentBasalFlowInternal.value
        set(value) {
            currentBasalFlowInternal.value = value
        }

    val currentBasalFlow: StateFlow<Double> get() = currentBasalFlowInternal
    var isTempBasalInProgress: Boolean
        get() = tempBasalStart != 0L && dateUtil.now() in tempBasalStart..tempBasalStart + tempBasalDuration
        set(isRunning) {
            if (isRunning) throw IllegalArgumentException("Use to cancel TBR only")
            else {
                tempBasalStart = 0L
                tempBasalDuration = 0L
                tempBasalPercent = 0
                tempBasalAbsolute = 0.0
            }
        }
    var tempBasalPercent = 0
    var tempBasalRemainingMin: Int = 0
        get() = max(T.msecs(tempBasalStart + tempBasalDuration - dateUtil.now()).mins().toInt(), 0)
    var tempBasalTotalSec = 0
    var tempBasalStart: Long = 0
    var basalStartErrorCode: Int = 0 // last start basal erroCode
    var isDualBolusInProgress = false
    var isExtendedInProgress = false
    var extendedBolusMinutes = 0
    var extendedBolusAmount = 0.0
    var extendedBolusAbsoluteRate = 0.0
    var extendedBolusSoFarInMinutes = 0
    var extendedBolusStart: Long = 0
    var extendedBolusStartErrorCode: Int = 0 // last start extended erroCode
    var extendedBolusRemainingMinutes = 0
    var extendedBolusDeliveredSoFar = 0.0 //RS only = 0.0

    // Profile R,RSv1
    var units = 0
    var activeProfile = 0
    var easyBasalMode = 0
    var basal48Enable = false
    var currentCIR = 0.0
    var currentCF = 0.0
    var currentAI = 0.0
    var currentTargetLow = 0.0
    var currentTargetHigh = 0.0
    var currentDia = 0.0
    var currentAIDR = 0
    var morningCIR = 0
    var morningCF = 0.0
    var afternoonCIR = 0
    var afternoonCF = 0.0
    var eveningCIR = 0
    var eveningCF = 0.0
    var nightCIR = 0
    var nightCF = 0.0

    // version manager
    var pumpVersionCode1 = 0
    var pumpVersionCode2 = 0

    // Pump version, read from AP550_DEVICE_ID. It tells the DCN18 pump (2300) apart
    // from the older pump (CY8CKIT-062 PSoC 6 BLE Pioneer Kit).
    var pumpVersionString = ""

    var isManual = 0
    var isFirstConnect = false  // First connection after pairing
    fun setIsFirstConnect(value: Boolean) {
        this.isFirstConnect = value
    }
    fun setIsManual(value: Int) {
        this.isManual = value
    }

    // Profile I
    var cf24 = Array<Double>(24) { 0.0 }
    var cir24 = Array<Double>(24) { 0.0 }

    //var pumpProfiles = arrayOf<Array<Double>>()
    var pumpProfiles: Array<Array<Double>>? = null

    //Limits
    var maxBolus = 0.0
    var maxBasal = 0.0

    // DanaRS specific
    var rsPassword = ""
    var v3RSPump = false

    // User settings
    var timeDisplayType24 = false
    var buttonScrollOnOff = false
    var beepAndAlarm = 0
    var lcdOnTimeSec = 0
    var backlightOnTimeSec = 0
    var selectedLanguage = 0
    var shutdownHour = 0
    var lowReservoirRate = 0
    var cannulaVolume = 0
    var refillAmount = 0
    var userOptionsFrompump: ByteArray? = null
    var initialBolusAmount = 0.0

    // Bolus settings
    var bolusCalculationOption = 0
    var missedBolusConfig = 0
    // NOTE: named `unitsString` (not `getUnits()`) on purpose — a `fun getUnits(): String` collides at
    // the JVM level with the `var units: Int` property's generated `getUnits()` accessor.
    val unitsString: String
        get() = if (units == UNITS_MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText
    var injectingErrorCode: Int = 0
    var bolusStartErrorCode: Int = 0 // last start bolus erroCode
    var historyDoneReceived: Boolean = false // true when last history message is received
    var bolusingBolusId: Long = -1L // DetailedBolusInfo id of bolus currently being delivered
    var bolusAmountToBeDelivered = 0.0 // amount to be delivered
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    var bolusStopped = false // bolus finished
    var bolusStopForced = false // bolus forced to stop by user
    var bolusDone = false // success end
    var lastEventTimeLoaded: Long = 0 // timestamp of last received event

    val lastKnownHistoryId: Int = 0 // hwver 7+, 1-2000
    var tempBasalDuration: Long = 0 // in milliseconds
    var tempBasalAbsolute: Double = 0.0

    fun temporaryBasalToString(): String {
        if (!isTempBasalInProgress) return ""
        val passedMin =
            ((min(dateUtil.now(), tempBasalStart + tempBasalDuration) - tempBasalStart) / 60.0 / 1000).roundToInt()
        var tempBasal = if(tempBasalPercent > 0){
            "$tempBasalPercent%"
        }else{
            "$tempBasalAbsolute U/h"
        }
        return tempBasal + " @" +
            dateUtil.timeString(tempBasalStart) +
            " " + passedMin + "/" + T.msecs(tempBasalDuration).mins() + "'"
    }

    private val extendedBolusPassedMinutes: Int
        get() = T.msecs(max(0, dateUtil.now() - extendedBolusStart)).mins().toInt()

    var extendedBolusDuration: Long = 0

    var historyLink = ArrayList<ConnectHistoryItem>()

    private val extendedBolusDurationInMinutes: Int
        get() = T.msecs(extendedBolusDuration).mins().toInt()

    fun extendedBolusToString(): String {
        if (!isExtendedInProgress) return ""

        return "E " + decimalFormatter.to2Decimal(extendedBolusAbsoluteRate) + "U/h @" +
            dateUtil.timeString(extendedBolusStart) +
            " " + extendedBolusPassedMinutes + "/" + extendedBolusDurationInMinutes + "'"
    }

    fun createConvertedProfile(): ProfileStore? {
        pumpProfiles?.let {
            val json = JSONObject()
            val store = JSONObject()
            val profile = JSONObject()
            //        Morning / 6:00–10:59
            //        Afternoon / 11:00–16:59
            //        Evening / 17:00–21:59
            //        Night / 22:00–5:59
            try {
                json.put("defaultProfile", PROFILE_PREFIX + (activeProfile + 1))
                json.put("store", store)
                profile.put("dia", Constants.DEFAULT_DIA)
                val carbratios = JSONArray()
                if (!profile24) {
                    carbratios.put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentCIR))
                }
                profile.put("carbratio", carbratios)
                val sens = JSONArray()
                if (!profile24) {
                    sens.put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentCF))
                }
                profile.put("sens", sens)
                val basals = JSONArray()
                val basalValues = if (basal48Enable) 48 else 24
                val basalIncrement = if (basal48Enable) 30 * 60 else 60 * 60
                for (h in 0 until basalValues) {
                    var time: String
                    val df = NumberFormat.INTEGER_2_DIGITS
                    time = if (basal48Enable) {
                        df.format(h.toLong() / 2) + ":" + df.format(30 * (h % 2).toLong())
                    } else {
                        df.format(h.toLong()) + ":00"
                    }
                    basals.put(JSONObject().put("time", time).put("timeAsSeconds", h * basalIncrement).put("value", it[activeProfile][h]))
                }
                profile.put("basal", basals)
                profile.put("target_low", JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentTargetLow)))
                profile.put("target_high", JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentTargetHigh)))
                profile.put("units", if (units == UNITS_MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
                store.put(PROFILE_PREFIX + (activeProfile + 1), profile)
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            } catch (e: Exception) {
                return null
            }
            return profileStoreProvider().with(Json.parseToJsonElement(json.toString()).jsonObject)
        }
        return null
    }

    fun buildDanaRProfileRecord(nsProfile: Profile): Array<Double> {
        val record = Array(24) { 0.0 }
        for (hour in 0..23) {
            //Some values get truncated to the next lower one.
            // -> round them to two decimals and make sure we are a small delta larger (that will get truncated)
            val value = Math.round(100.0 * nsProfile.getBasalTimeFromMidnight((hour * 60 * 60))) / 100.0 + 0.00001
            aapsLogger.debug(LTag.PUMP, "NS basal value for $hour:00 is $value")
            record[hour] = value
        }
        return record
    }

    fun scanStatusRecord(connectStatus: ConnectStatus?) {
        val connectHistoryItem = ConnectHistoryItem(connectStatus!!)
        historyLink.add(connectHistoryItem)
    }

    fun scanStatusRecord(connectCommand: String) {
        val connectHistoryItem = ConnectHistoryItem(connectCommand)
        historyLink.add(connectHistoryItem)
    }

    val isPasswordOK: Boolean
//        get() = password == sp.getInt(R.string.key_ap550_password, -2)
        get() = password == 1234
    val isRSPasswordOK: Boolean
        get() = rsPassword.equals(preferences.get(Ap550StringNonKey.Password), ignoreCase = true) || v3RSPump

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "AP550Pump reset")
        lastConnection = 0
        lastSettingsRead = 0
        lastSyncPumpTime = 0
    }

    /**
     * Forget what we know about the bound pump's basal profile. Called when the pump is unpaired,
     * so the next pump is never compared against the previous pump's cached profile.
     *
     * The cached profile feeding `isThisProfileSet` is only refreshed once an hour, so without this
     * a newly bound pump would inherit the old pump's profile and look "already set". The stored
     * read timestamp is cleared too, so the fresh pump is read at once instead of waiting for the
     * next hourly read.
     */
    fun forgetProfile() {
        pumpProfiles = null
        preferences.put(Ap550LongNonKey.LastGetBasalTimestamp, 0L)
    }

    fun modelFriendlyName(): String =
        when (productCode) {
            0x01 -> "uAP 550S"
            0x02 -> "uAP 550SE"
            else -> "Unknown pump"
        }

    fun pumpType(): PumpType =
        when (hwModel) {
            0x01 -> PumpType.DANA_R_KOREAN
            0x03 ->
                when (protocol) {
                    0x00 -> PumpType.DANA_R
                    0x02 -> PumpType.DANA_RV2
                    else -> PumpType.DANA_R // 0x01 and 0x03 known
                }

            0x05 -> PumpType.DANA_RS
            0x06 -> PumpType.DANA_RS_KOREAN
            0x07 -> PumpType.DANA_I
            0x09 -> PumpType.DANA_I
            0x0A -> PumpType.DANA_I // Korean version
            else -> PumpType.AP550S // having here default type non TBR capable is causing problem with disabling loop
        }

    companion object {
        const val UNITS_MGDL = 0
        const val UNITS_MMOL = 1
        const val DELIVERY_PRIME = 0x01
        const val DELIVERY_STEP_BOLUS = 0x02
        const val DELIVERY_BASAL = 0x04
        const val DELIVERY_EXT_BOLUS = 0x08
        const val PROFILE_PREFIX = "550S-"

        // v2 history entries
        const val TEMPSTART = 1
        const val TEMPSTOP = 2
        const val EXTENDEDSTART = 3
        const val EXTENDEDSTOP = 4
        const val BOLUS = 5
        const val DUALBOLUS = 6
        const val DUALEXTENDEDSTART = 7
        const val DUALEXTENDEDSTOP = 8
        const val SUSPENDON = 9
        const val SUSPENDOFF = 10
        const val REFILL = 11
        const val PRIME = 12
        const val PROFILECHANGE = 13
        const val CARBS = 14
        const val PRIMECANNULA = 15
        const val TIMECHANGE = 16

        // Dana R btModel
        const val DOMESTIC_MODEL = 0x01
        const val EXPORT_MODEL = 0x03
    }
}