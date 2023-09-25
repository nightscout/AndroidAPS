package app.aaps.core.main.wizard

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.extensions.valueToUnits
import app.aaps.core.main.iob.round
import app.aaps.core.utils.JsonHelper.safeGetInt
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.core.utils.MidnightUtils
import app.aaps.database.ValueWrapper
import dagger.android.HasAndroidInjector
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class QuickWizardEntry @Inject constructor(private val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

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
        private const val POSITIVE_ONLY = 2
        private const val NEGATIVE_ONLY = 3
        const val DEVICE_ALL = 0
        const val DEVICE_PHONE = 1
        const val DEVICE_WATCH = 2
        const val DEFAULT = 0
        const val CUSTOM = 1
    }

    init {
        injector.androidInjector().inject(this)
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
        val dbRecord = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        val tempTarget = if (dbRecord is ValueWrapper.Existing) dbRecord.value else null
        //BG
        var bg = 0.0
        if (useBG() == YES) {
            bg = lastBG.valueToUnits(profileFunction.getUnits())
        }
        // COB
        val cob =
            if (useCOB() == YES) iobCobCalculator.getCobInfo("QuickWizard COB").displayCob ?: 0.0
            else 0.0
        // Bolus IOB
        var bolusIOB = false
        if (useBolusIOB() == YES) {
            bolusIOB = true
        }
        // Basal IOB
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        var basalIOB = false
        if (useBasalIOB() == YES) {
            basalIOB = true
        } else if (useBasalIOB() == POSITIVE_ONLY && basalIob.iob > 0) {
            basalIOB = true
        } else if (useBasalIOB() == NEGATIVE_ONLY && basalIob.iob < 0) {
            basalIOB = true
        }
        // SuperBolus
        var superBolus = false
        if (useSuperBolus() == YES && sp.getBoolean(info.nightscout.core.utils.R.string.key_usesuperbolus, false)) {
            superBolus = true
        }
        if (loop.isEnabled() && loop.isSuperBolus) superBolus = false
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
        val percentage = if (usePercentage() == DEFAULT) sp.getInt(info.nightscout.core.utils.R.string.key_boluswizard_percentage, 100) else percentage()
        return BolusWizard(injector).doCalc(
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
            bolusIOB,
            basalIOB,
            superBolus,
            useTempTarget() == YES,
            trend,
            false,
            buttonText(),
            quickWizard = true
        ) //tbc, ok if only quickwizard, but if other sources elsewhere use Sources.QuickWizard
    }

    fun guid(): String = safeGetString(storage, "guid", "")

    fun device(): Int = safeGetInt(storage, "device", DEVICE_ALL)

    fun forDevice(device: Int) = device() == device || device() == DEVICE_ALL

    fun buttonText(): String = safeGetString(storage, "buttonText", "")

    fun carbs(): Int = safeGetInt(storage, "carbs")

    fun validFromDate(): Long = dateUtil.secondsOfTheDayToMilliseconds(validFrom())

    fun validToDate(): Long = dateUtil.secondsOfTheDayToMilliseconds(validTo())

    fun validFrom(): Int = safeGetInt(storage, "validFrom")

    fun validTo(): Int = safeGetInt(storage, "validTo")

    fun useBG(): Int = safeGetInt(storage, "useBG", YES)

    fun useCOB(): Int = safeGetInt(storage, "useCOB", NO)

    fun useBolusIOB(): Int = safeGetInt(storage, "useBolusIOB", YES)

    fun useBasalIOB(): Int = safeGetInt(storage, "useBasalIOB", YES)

    fun useTrend(): Int = safeGetInt(storage, "useTrend", NO)

    fun useSuperBolus(): Int = safeGetInt(storage, "useSuperBolus", NO)

    fun useTempTarget(): Int = safeGetInt(storage, "useTempTarget", NO)

    fun usePercentage(): Int = safeGetInt(storage, "usePercentage", DEFAULT)

    fun percentage(): Int = safeGetInt(storage, "percentage", 100)
}
