package info.nightscout.androidaps.dana

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by mike on 04.07.2016.
 */
@Singleton
class DanaPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val injector: HasAndroidInjector
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

    var lastConnection: Long = 0
    var lastSettingsRead: Long = 0

    // Info
    var serialNumber = ""
    var shippingDate: Long = 0
    var shippingCountry = ""
    var bleModel = "" // RS v3:  like BPN-1.0.1
    var isNewPump = true // R only , providing model info
    var password = -1 // R, RSv1

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
    var calculatorEnabled = false
    var dailyTotalUnits = 0.0
    var dailyTotalBolusUnits = 0.0 // RS only
    var dailyTotalBasalUnits = 0.0 // RS only
    var decRatio = 0 // RS v3: [%] for pump IOB calculation
    var maxDailyTotalUnits = 0
    var bolusStep = 0.1
    var basalStep = 0.1
    var iob = 0.0
    var reservoirRemainingUnits = 0.0
    var batteryRemaining = 0
    var bolusBlocked = false
    var lastBolusTime: Long = 0
    var lastBolusAmount = 0.0
    var currentBasal = 0.0
    var isTempBasalInProgress = false
    var tempBasalPercent = 0
    var tempBasalRemainingMin = 0
    var tempBasalTotalSec = 0
    var tempBasalStart: Long = 0
    var isDualBolusInProgress = false
    var isExtendedInProgress = false
    var extendedBolusMinutes = 0
    var extendedBolusAmount = 0.0
    var extendedBolusAbsoluteRate = 0.0
    var extendedBolusSoFarInMinutes = 0
    var extendedBolusStart: Long = 0
    var extendedBolusRemainingMinutes = 0
    var extendedBolusDeliveredSoFar = 0.0 //RS only = 0.0

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
    fun getUnits(): String {
        return if (units == UNITS_MGDL) Constants.MGDL else Constants.MMOL
    }

    var bolusStartErrorCode: Int = 0 // last start bolus erroCode
    var historyDoneReceived: Boolean = false // true when last history message is received
    var bolusingTreatment: Treatment? = null // actually delivered treatment
    var bolusAmountToBeDelivered = 0.0 // amount to be delivered
    var bolusProgressLastTimeStamp: Long = 0 // timestamp of last bolus progress message
    var bolusStopped = false // bolus finished
    var bolusStopForced = false // bolus forced to stop by user
    var bolusDone = false // success end
    var lastEventTimeLoaded: Long = 0 // timestamp of last received event

    val lastKnownHistoryId: Int = 0 // hwver 7+, 1-2000

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
                val carbratios = JSONArray()
                if (!profile24) {
                    carbratios.put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", nightCIR))
                    carbratios.put(JSONObject().put("time", "06:00").put("timeAsSeconds", 6 * 3600).put("value", morningCIR))
                    carbratios.put(JSONObject().put("time", "11:00").put("timeAsSeconds", 11 * 3600).put("value", afternoonCIR))
                    carbratios.put(JSONObject().put("time", "14:00").put("timeAsSeconds", 17 * 3600).put("value", eveningCIR))
                    carbratios.put(JSONObject().put("time", "22:00").put("timeAsSeconds", 22 * 3600).put("value", nightCIR))
                } else { // 24 values
                    for (i in 0..23) {
                        carbratios.put(JSONObject().put("time", String.format("%02d", i) + ":00").put("timeAsSeconds", i * 3600).put("value", cir24[i]))
                    }
                }
                profile.put("carbratio", carbratios)
                val sens = JSONArray()
                if (!profile24) {
                    sens.put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", nightCF))
                    sens.put(JSONObject().put("time", "06:00").put("timeAsSeconds", 6 * 3600).put("value", morningCF))
                    sens.put(JSONObject().put("time", "11:00").put("timeAsSeconds", 11 * 3600).put("value", afternoonCF))
                    sens.put(JSONObject().put("time", "17:00").put("timeAsSeconds", 17 * 3600).put("value", eveningCF))
                    sens.put(JSONObject().put("time", "22:00").put("timeAsSeconds", 22 * 3600).put("value", nightCF))
                } else { // 24 values
                    for (i in 0..23) {
                        sens.put(JSONObject().put("time", String.format("%02d", i) + ":00").put("timeAsSeconds", i * 3600).put("value", cf24[i]))
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
                    basals.put(JSONObject().put("time", time).put("timeAsSeconds", h * basalIncrement).put("value", it[activeProfile][h]))
                }
                profile.put("basal", basals)
                profile.put("target_low", JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentTarget)))
                profile.put("target_high", JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", currentTarget)))
                profile.put("units", if (units == UNITS_MGDL) Constants.MGDL else Constants.MMOL)
                store.put(PROFILE_PREFIX + (activeProfile + 1), profile)
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            } catch (e: Exception) {
                return null
            }
            return ProfileStore(injector, json)
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

    val isPasswordOK: Boolean
        get() = password == sp.getInt(R.string.key_danar_password, -2)

    val isRSPasswordOK: Boolean
        get() = rsPassword.equals(sp.getString(R.string.key_danars_password, ""), ignoreCase = true) || v3RSPump

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "DanaRPump reset")
        lastConnection = 0
        lastSettingsRead = 0
    }

    fun modelFriendlyName(): String =
        when (hwModel) {
            0x01 -> "DanaR Korean"
            0x03 ->
                if (protocol == 0x00) "DanaR old"
                else if (protocol == 0x02) "DanaR v2"
                else "DanaR" // 0x01 and 0x03 known
            0x05 ->
                if (protocol < 10) "DanaRS"
                else "DanaRS v3"
            0x06 -> "DanaRS Korean"
            0x07 -> "Dana-i"
            else -> "Unknown Dana pump"
        }

    companion object {
        const val UNITS_MGDL = 0
        const val UNITS_MMOL = 1
        const val DELIVERY_PRIME = 0x01
        const val DELIVERY_STEP_BOLUS = 0x02
        const val DELIVERY_BASAL = 0x04
        const val DELIVERY_EXT_BOLUS = 0x08
        const val PROFILE_PREFIX = "DanaR-"

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