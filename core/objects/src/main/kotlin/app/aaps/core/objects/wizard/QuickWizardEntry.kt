package app.aaps.core.objects.wizard

import app.aaps.annotations.OpenForTesting
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.core.utils.JsonHelper.safeGetInt
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.core.utils.MidnightUtils
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider

class QuickWizardEntry @Inject constructor(
    aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val bolusWizardProvider: Provider<BolusWizard>
) {

    // for mock
    @OpenForTesting
    class Time {

        fun secondsFromMidnight(): Int = MidnightUtils.secondsFromMidnight()

    }

    var time = Time()

    lateinit var storage: JSONObject
    var position: Int = -1

    companion object {

        const val YES = 0
        const val NO = 1
        const val POSITIVE_ONLY = 2
        const val NEGATIVE_ONLY = 3
        const val DEVICE_ALL = 0
        const val DEVICE_PHONE = 1
        const val DEVICE_WATCH = 2
        const val DEFAULT = 0
        const val CUSTOM = 1
    }

    init {
        val guid = UUID.randomUUID().toString()
        val emptyData = """{
                "guid": "$guid",
                "buttonText": "",
                "carbs": 0,
                "validFrom": 0,
                "validTo": 86340,
                "device": "all",
                "usePercentage": "default",
                "percentage": 100
            }""".trimMargin()
        try {
            storage = JSONObject(emptyData)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    /*
        {
            guid: string,
            device: string, // (phone, watch, all)
            buttonText: "Meal",
            carbs: 36,
            validFrom: 8 * 60 * 60, // seconds from midnight
            validTo: 9 * 60 * 60,   // seconds from midnight
            useBG: 0,
            useCOB: 0,
            useBolusIOB: 0,
            useBasalIOB: 0,
            useTrend: 0,
            useSuperBolus: 0,
            useTemptarget: 0
            usePercentage: string, // default, custom
            percentage: int,
        }
     */
    fun from(entry: JSONObject, position: Int): QuickWizardEntry {
        // TODO set guid if missing for migration
        storage = entry
        this.position = position
        return this
    }

    fun isActive(): Boolean = time.secondsFromMidnight() >= validFrom() && time.secondsFromMidnight() <= validTo() && forDevice(DEVICE_PHONE)

    fun doCalc(profile: Profile, profileName: String, lastBG: InMemoryGlucoseValue): BolusWizard {
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        //BG
        var bg = 0.0
        if (useBG() == YES) {
            bg = lastBG.valueToUnits(profileFunction.getUnits())
        }
        // COB
        val cob =
            if (useCOB() == YES) iobCobCalculator.getCobInfo("QuickWizard COB").displayCob ?: 0.0
            else 0.0
        // IOB
        var uIOB = false
        if (useIOB() == YES) {
            uIOB = true
        }

        var uPositiveIOBOnly = false
        if (usePositiveIOBOnly() == YES) {
            uPositiveIOBOnly = true
        }
        // SuperBolus
        var superBolus = false
        if (useSuperBolus() == YES && preferences.get(BooleanKey.OverviewUseSuperBolus)) {
            superBolus = true
        }
        if (loop.runningMode == RM.Mode.SUPER_BOLUS) superBolus = false
        // Trend
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        var trend = false
        if (useTrend() == YES) {
            trend = true
        } else if (useTrend() == POSITIVE_ONLY && glucoseStatus != null && glucoseStatus.shortAvgDelta > 0) {
            trend = true
        } else if (useTrend() == NEGATIVE_ONLY && glucoseStatus != null && glucoseStatus.shortAvgDelta < 0) {
            trend = true
        }
        val percentage = if (usePercentage() == DEFAULT) preferences.get(IntKey.OverviewBolusPercentage) else percentage()
        return bolusWizardProvider.get().doCalc(
            profile,
            profileName,
            tempTarget,
            carbs(),
            cob,
            bg,
            0.0,
            percentage,
            true,
            useCOB() == YES,
            uIOB, //always use or don't both bolus
            uIOB, // & basal IOB
            superBolus,
            useTempTarget() == YES,
            trend,
            useAlarm() == YES,
            buttonText(),
            carbTime(),
            quickWizard = true,
            positiveIOBOnly = uPositiveIOBOnly
        ) //tbc, ok if only quickwizard, but if other sources elsewhere use Sources.QuickWizard
    }

    fun guid(): String = safeGetString(storage, "guid", "")

    fun device(): Int = safeGetInt(storage, "device", DEVICE_ALL)

    fun forDevice(device: Int) = device() == device || device() == DEVICE_ALL

    fun buttonText(): String = safeGetString(storage, "buttonText", "")

    fun carbs(): Int = safeGetInt(storage, "carbs")

    fun validFromDate(): Long = dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(validFrom())

    fun validToDate(): Long = dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(validTo())

    fun validFrom(): Int = safeGetInt(storage, "validFrom")

    fun validTo(): Int = safeGetInt(storage, "validTo")

    fun useBG(): Int = safeGetInt(storage, "useBG", YES)

    fun useCOB(): Int = safeGetInt(storage, "useCOB", NO)

    fun useIOB(): Int = safeGetInt(storage, "useIOB", YES)

    fun usePositiveIOBOnly(): Int = safeGetInt(storage, "usePositiveIOBOnly", NO)

    fun useTrend(): Int = safeGetInt(storage, "useTrend", NO)

    fun useSuperBolus(): Int = safeGetInt(storage, "useSuperBolus", NO)

    fun useTempTarget(): Int = safeGetInt(storage, "useTempTarget", NO)

    fun usePercentage(): Int = safeGetInt(storage, "usePercentage", CUSTOM)

    fun percentage(): Int = safeGetInt(storage, "percentage", 100)

    fun useEcarbs(): Int = safeGetInt(storage, "useEcarbs", NO)

    fun carbs2(): Int = safeGetInt(storage, "carbs2")

    fun time(): Int = safeGetInt(storage, "time")

    fun duration(): Int = safeGetInt(storage, "duration")

    fun carbTime(): Int = safeGetInt(storage, "carbTime")

    fun useAlarm(): Int = safeGetInt(storage, "useAlarm", NO)
}
