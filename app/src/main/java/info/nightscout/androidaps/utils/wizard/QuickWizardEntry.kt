package info.nightscout.androidaps.utils.wizard

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper.safeGetInt
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class QuickWizardEntry @Inject constructor(private val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    lateinit var storage: JSONObject
    var position: Int = -1

    companion object {
        const val YES = 0
        const val NO = 1
        private const val POSITIVE_ONLY = 2
        private const val NEGATIVE_ONLY = 3
    }

    init {
        injector.androidInjector().inject(this)
        val emptyData = "{\"buttonText\":\"\",\"carbs\":0,\"validFrom\":0,\"validTo\":86340}"
        try {
            storage = JSONObject(emptyData)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    /*
        {
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
        }
     */
    fun from(entry: JSONObject, position: Int): QuickWizardEntry {
        storage = entry
        this.position = position
        return this
    }

    fun isActive(): Boolean = Profile.secondsFromMidnight() >= validFrom() && Profile.secondsFromMidnight() <= validTo()

    fun doCalc(profile: Profile, profileName: String, lastBG: BgReading, _synchronized: Boolean): BolusWizard {
        val tempTarget = treatmentsPlugin.tempTargetFromHistory
        //BG
        var bg = 0.0
        if (useBG() == YES) {
            bg = lastBG.valueToUnits(profileFunction.getUnits())
        }
        // COB
        var cob = 0.0
        if (useCOB() == YES) {
            val cobInfo = iobCobCalculatorPlugin.getCobInfo(_synchronized, "QuickWizard COB")
            if (cobInfo.displayCob != null) cob = cobInfo.displayCob
        }
        // Bolus IOB
        var bolusIOB = false
        if (useBolusIOB() == YES) {
            bolusIOB = true
        }
        // Basal IOB
        treatmentsPlugin.updateTotalIOBTempBasals()
        val basalIob = treatmentsPlugin.lastCalculationTempBasals.round()
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
        if (useSuperBolus() == YES && sp.getBoolean(R.string.key_usesuperbolus, false)) {
            superBolus = true
        }
        if (loopPlugin.isEnabled(loopPlugin.getType()) && loopPlugin.isSuperBolus) superBolus = false
        // Trend
        val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
        var trend = false
        if (useTrend() == YES) {
            trend = true
        } else if (useTrend() == POSITIVE_ONLY && glucoseStatus != null && glucoseStatus.short_avgdelta > 0) {
            trend = true
        } else if (useTrend() == NEGATIVE_ONLY && glucoseStatus != null && glucoseStatus.short_avgdelta < 0) {
            trend = true
        }
        val percentage = sp.getDouble(R.string.key_boluswizard_percentage, 100.0)
        return BolusWizard(injector).doCalc(profile, profileName, tempTarget, carbs(), cob, bg, 0.0, percentage, true, useCOB() == YES, bolusIOB, basalIOB, superBolus, useTempTarget() == YES, trend, false, "QuickWizard")
    }

    fun buttonText(): String = safeGetString(storage, "buttonText", "")

    fun carbs(): Int = safeGetInt(storage, "carbs")

    fun validFromDate(): Date = DateUtil.toDate(validFrom())

    fun validToDate(): Date = DateUtil.toDate(validTo())

    fun validFrom(): Int = safeGetInt(storage, "validFrom")

    fun validTo(): Int = safeGetInt(storage, "validTo")

    fun useBG(): Int = safeGetInt(storage, "useBG", YES)

    fun useCOB(): Int = safeGetInt(storage, "useCOB", NO)

    fun useBolusIOB(): Int = safeGetInt(storage, "useBolusIOB", YES)

    fun useBasalIOB(): Int = safeGetInt(storage, "useBasalIOB", YES)

    fun useTrend(): Int = safeGetInt(storage, "useTrend", NO)

    fun useSuperBolus(): Int = safeGetInt(storage, "useSuperBolus", NO)

    fun useTempTarget(): Int = safeGetInt(storage, "useTempTarget", NO)
}