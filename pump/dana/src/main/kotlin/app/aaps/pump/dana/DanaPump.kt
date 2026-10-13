package app.aaps.pump.dana

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.dana.keys.DanaStringKey
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.InvalidParameterException
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Singleton
class DanaPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val profileStoreProvider: Provider<ProfileStore>
) {

    @Suppress("unused")
    enum class ErrorState(val code: Int) {

        NONE(0x00),
        SUSPENDED(0x01),
        DAILY_MAX(0x02),
        BOLUS_BLOCK(0x04),
        ORDER_DELIVERING(0x08),
        NO_PRIME(0x10);

        companion object {

            private val map = entries.associateBy(ErrorState::code)
            operator fun get(value: Int) = map[value]
        }
    }

    var lastConnection: Long = 0
    var lastSettingsRead: Long = 0
    var readHistoryFrom: Long = 0 // start next history read from this timestamp
    var historyDoneReceived: Boolean = false // true when last history message is received

    // Info
    var serialNumber = ""
    var shippingDate: Long = 0
    var shippingCountry = ""
    var bleModel = "" // RS v3:  like BPN-1.0.1
    var isNewPump = true // R only , providing model info
    var password = -1 // R, RSv1

    // time
    var pumpTime: Long = 0
    var zoneOffset: Int = 0 // i (hw 7+)

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
    var calculatorEnabled = false
    var dailyTotalUnits = 0.0
    var maxDailyTotalUnits = 0
    var bolusStep = 0.1
    var basalStep = 0.1
    var iob = 0.0
    var reservoirRemainingUnits = 0.0
    var batteryRemaining: Int? = null
    var bolusBlocked = false
    var lastBolusTime: Long = 0
    var lastBolusAmount = 0.0
    var currentBasal = 0.0

    /*
     * TEMP BASALS
     */

    var tempBasalStart: Long = 0
    var tempBasalDuration: Long = 0 // in milliseconds
    var tempBasalPercent = 0

    var isTempBasalInProgress: Boolean
        get() = tempBasalStart != 0L && dateUtil.now() in tempBasalStart..tempBasalStart + tempBasalDuration
        set(isRunning) {
            require(!isRunning) { "Use to cancel TBR only" }
            tempBasalStart = 0L
            tempBasalDuration = 0L
            tempBasalPercent = 0
        }
    val tempBasalRemainingMin: Int
        get() = max(T.msecs(tempBasalStart + tempBasalDuration - dateUtil.now()).mins().toInt(), 0)

    fun temporaryBasalToString(): String {
        if (!isTempBasalInProgress) return ""

        val passedMin =
            ((min(dateUtil.now(), tempBasalStart + tempBasalDuration) - tempBasalStart) / 60.0 / 1000).roundToInt()
        return tempBasalPercent.toString() + "% @" +
            dateUtil.timeString(tempBasalStart) +
            " " + passedMin + "/" + T.msecs(tempBasalDuration).mins() + "'"
    }

    fun fromTemporaryBasal(tbr: PumpSync.PumpState.TemporaryBasal?) {
        if (tbr == null) {
            tempBasalStart = 0
            tempBasalDuration = 0
            tempBasalPercent = 0
        } else {
            tempBasalStart = tbr.timestamp
            tempBasalDuration = tbr.duration
            tempBasalPercent = tbr.rate.toInt()
        }
    }

    /*
        * EXTENDED BOLUSES
        */

    var extendedBolusStart: Long = 0
    var extendedBolusDuration: Long = 0
    var extendedBolusAmount = 0.0

    var isExtendedInProgress: Boolean
        get() = extendedBolusStart != 0L && dateUtil.now() in extendedBolusStart..extendedBolusStart + extendedBolusDuration
        set(isRunning) {
            require(!isRunning) { "Use to cancel EB only" }
            extendedBolusStart = 0L
            extendedBolusDuration = 0L
            extendedBolusAmount = 0.0
        }
    private val extendedBolusPassedMinutes: Int
        get() = T.msecs(max(0, dateUtil.now() - extendedBolusStart)).mins().toInt()
    val extendedBolusRemainingMinutes: Int
        get() = max(T.msecs(extendedBolusStart + extendedBolusDuration - dateUtil.now()).mins().toInt(), 0)
    private val extendedBolusDurationInMinutes: Int
        get() = T.msecs(extendedBolusDuration).mins().toInt()
    var extendedBolusAbsoluteRate: Double
        get() = extendedBolusAmount * T.hours(1).msecs() / extendedBolusDuration
        set(rate) {
            extendedBolusAmount = rate * extendedBolusDuration / T.hours(1).msecs()
        }

    fun extendedBolusToString(): String {
        if (!isExtendedInProgress) return ""

        return "E " + decimalFormatter.to2Decimal(extendedBolusAbsoluteRate) + "U/h @" +
            dateUtil.timeString(extendedBolusStart) +
            " " + extendedBolusPassedMinutes + "/" + extendedBolusDurationInMinutes + "'"
    }

    fun fromExtendedBolus(eb: PumpSync.PumpState.ExtendedBolus?) {
        if (eb == null) {
            extendedBolusStart = 0
            extendedBolusDuration = 0
            extendedBolusAmount = 0.0
        } else {
            extendedBolusStart = eb.timestamp
            extendedBolusDuration = eb.duration
            extendedBolusAmount = eb.amount
        }
    }

    var isDualBolusInProgress = false

    // Profile R,RSv1
    var units = 0
    var activeProfile = 0
    var easyBasalMode = 0
    var basal48Enable = false
    var currentCIR = 0
    var currentCF = 0.0
    var currentAI = 0.0
    var currentTarget = 0.0
    var currentAIDR = 0
    var morningCIR = 0
    var morningCF = 0.0
    var afternoonCIR = 0
    var afternoonCF = 0.0
    var eveningCIR = 0
    var eveningCF = 0.0
    var nightCIR = 0
    var nightCF = 0.0

    // Profile I
    var cf24 = Array(24) { 0.0 }
    var cir24 = Array(24) { 0.0 }

    var pumpProfiles: Array<Array<Double>>? = null

    //Limits
    var maxBolus = 0.0
    var maxBasal = 0.0

    // DanaRS specific
    var rsPassword = ""
    var ignoreUserPassword = false // true if replaced by enhanced encryption

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
    var target = 0 // mgdl 40~400 mmol 2.2~22 => 220~2200
    var userOptionsFromPump: ByteArray? = null
    var initialBolusAmount = 0.0

    // Bolus settings
    var bolusCalculationOption = 0
    var missedBolusConfig = 0
    fun getUnits(): String {
        return if (units == UNITS_MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText
    }

    var bolusStartErrorCode: Int = 0 // last start bolus errorCode
    var bolusingDetailedBolusInfo: DetailedBolusInfo? = null // actually delivered treatment
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    var bolusStopped = false // bolus finished
    var bolusStopForced = false // bolus forced to stop by user
    var bolusDone = false // success end
    var lastEventTimeLoaded: Long = 0 // timestamp of last received event

    // val lastKnownHistoryId: Int = 0 // hw ver 7+, 1-2000

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
                profile.put("dia", Constants.defaultDIA)
                val carbRatios = JSONArray()
                if (!profile24) {
                    carbRatios.put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", nightCIR))
                    carbRatios.put(JSONObject().put("time", "06:00").put("timeAsSeconds", 6 * 3600).put("value", morningCIR))
                    carbRatios.put(JSONObject().put("time", "11:00").put("timeAsSeconds", 11 * 3600).put("value", afternoonCIR))
                    carbRatios.put(JSONObject().put("time", "14:00").put("timeAsSeconds", 17 * 3600).put("value", eveningCIR))
                    carbRatios.put(JSONObject().put("time", "22:00").put("timeAsSeconds", 22 * 3600).put("value", nightCIR))
                } else { // 24 values
                    for (i in 0..23) {
                        carbRatios.put(
                            JSONObject()
                                .put("time", String.format("%02d", i) + ":00")
                                .put("timeAsSeconds", i * 3600)
                                .put("value", cir24[i])
                        )
                    }
                }
                profile.put("carbratio", carbRatios)
                val sens = JSONArray()
                if (!profile24) {
                    sens.put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", nightCF))
                    sens.put(JSONObject().put("time", "06:00").put("timeAsSeconds", 6 * 3600).put("value", morningCF))
                    sens.put(JSONObject().put("time", "11:00").put("timeAsSeconds", 11 * 3600).put("value", afternoonCF))
                    sens.put(JSONObject().put("time", "17:00").put("timeAsSeconds", 17 * 3600).put("value", eveningCF))
                    sens.put(JSONObject().put("time", "22:00").put("timeAsSeconds", 22 * 3600).put("value", nightCF))
                } else { // 24 values
                    for (i in 0..23) {
                        sens.put(
                            JSONObject()
                                .put("time", String.format("%02d", i) + ":00")
                                .put("timeAsSeconds", i * 3600)
                                .put("value", cf24[i])
                        )
                    }
                }
                profile.put("sens", sens)
                val basals = JSONArray()
                val basalValues = if (basal48Enable) 48 else 24
                val basalIncrement = if (basal48Enable) 30 * 60 else 60 * 60
                for (h in 0 until basalValues) {
                    var time: String
                    val df = DecimalFormat("00")
                    time = if (basal48Enable) {
                        df.format(h.toLong() / 2) + ":" + df.format(30 * (h % 2).toLong())
                    } else {
                        df.format(h.toLong()) + ":00"
                    }
                    basals.put(
                        JSONObject()
                            .put("time", time)
                            .put("timeAsSeconds", h * basalIncrement)
                            .put("value", it[activeProfile][h])
                    )
                }
                profile.put("basal", basals)
                profile.put(
                    "target_low",
                    JSONArray().put(
                        JSONObject()
                            .put("time", "00:00")
                            .put("timeAsSeconds", 0)
                            .put("value", currentTarget)
                    )
                )
                profile.put(
                    "target_high",
                    JSONArray().put(
                        JSONObject()
                            .put("time", "00:00")
                            .put("timeAsSeconds", 0)
                            .put("value", currentTarget)
                    )
                )
                profile.put("units", if (units == UNITS_MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
                store.put(PROFILE_PREFIX + (activeProfile + 1), profile)
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            } catch (e: Exception) {
                return null
            }
            return profileStoreProvider.get().with(json)
        }
        return null
    }

    fun buildDanaRProfileRecord(nsProfile: Profile): Array<Double> {
        val record = Array(24) { 0.0 }
        for (hour in 0..23) {
            //Some values get truncated to the next lower one.
            // -> round them to two decimals and make sure we are a small delta larger (that will get truncated)
            val value = (100.0 * nsProfile.getBasalTimeFromMidnight((hour * 60 * 60))).roundToLong() / 100.0 + 0.00001
            aapsLogger.debug(LTag.PUMP, "NS basal value for $hour:00 is $value")
            record[hour] = value
        }
        return record
    }

    val isPasswordOK: Boolean
        get() = password == preferences.get(DanaIntKey.Password)

    val isRSPasswordOK: Boolean
        get() = rsPassword.equals(
            preferences.get(DanaStringKey.Password),
            ignoreCase = true
        ) || ignoreUserPassword

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "DanaRPump reset")
        lastConnection = 0
        lastSettingsRead = 0
        readHistoryFrom = 0
    }

    fun modelFriendlyName(): String =
        when (hwModel) {
            0x01       -> "DanaR Korean"
            0x03       ->
                when (protocol) {
                    0x00 -> "DanaR old"
                    0x02 -> "DanaR v2"
                    else -> "DanaR" // 0x01 and 0x03 known
                }

            0x05       ->
                if (protocol < 10) "DanaRS"
                else "DanaRS v3"

            0x06       -> "DanaRS Korean"
            0x07       -> "Dana-i (BLE4.2)"
            0x09, 0x0A -> "Dana-i (BLE5)"
            else       -> "Unknown Dana pump"
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
            else -> PumpType.DANA_RS // having here default type non TBR capable is causing problem with disabling loop
        }

    // v2, RS history entries
    enum class HistoryEntry(val value: Int) {

        TEMP_START(1),
        TEMP_STOP(2),
        EXTENDED_START(3),
        EXTENDED_STOP(4),
        BOLUS(5),
        DUAL_BOLUS(6),
        DUAL_EXTENDED_START(7),
        DUAL_EXTENDED_STOP(8),
        SUSPEND_ON(9),
        SUSPEND_OFF(10),
        REFILL(11),
        PRIME(12),
        PROFILE_CHANGE(13),
        CARBS(14),
        PRIME_CANNULA(15),
        TIME_CHANGE(16)
        ;

        companion object {

            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: throw InvalidParameterException()
        }

    }

    companion object {

        const val UNITS_MGDL = 0
        const val UNITS_MMOL = 1
        const val DELIVERY_PRIME = 0x01
        const val DELIVERY_STEP_BOLUS = 0x02
        const val DELIVERY_BASAL = 0x04
        const val DELIVERY_EXT_BOLUS = 0x08
        const val PROFILE_PREFIX = "DanaR-"

        // Dana R btModel
        const val DOMESTIC_MODEL = 0x01
        const val EXPORT_MODEL = 0x03
    }
}