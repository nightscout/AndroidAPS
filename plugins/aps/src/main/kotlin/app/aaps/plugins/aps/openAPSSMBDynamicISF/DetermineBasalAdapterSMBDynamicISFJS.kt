package app.aaps.plugins.aps.openAPSSMBDynamicISF

import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.aps.SMBDefaults
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.GlucoseStatus
import app.aaps.core.interfaces.iob.IobTotal
import app.aaps.core.interfaces.iob.MealData
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.stats.IsfCalculator
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.main.extensions.convertedToAbsolute
import app.aaps.core.main.extensions.getPassedDurationToTimeInMinutes
import app.aaps.core.main.extensions.plannedRemainingMinutes
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class DetermineBasalAdapterSMBDynamicISFJS internal constructor(scriptReader: ScriptReader, injector: HasAndroidInjector)
    : DetermineBasalAdapterSMBJS(scriptReader, injector) {

    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var isfCalculator: IsfCalculator

    override val jsFolder = "OpenAPSSMBDynamicISF"
    override val jsAdditionalScript = """
var getIsfByProfile = function (bg, profile) {
    var cap = profile.dynISFBgCap;
    if (bg > cap) bg = (cap + (bg - cap)/3);
    var sens_BG = Math.log((bg / profile.insulinDivisor) + 1);
    var scaler = Math.log((profile.normalTarget / profile.insulinDivisor) + 1) / sens_BG;
    return profile.sensNormalTarget * (1 - (1 - scaler) * profile.dynISFvelocity);
}"""

    @Suppress("SpellCheckingInspection")
    override fun setData(
        profile: Profile,
        maxIob: Double,
        maxBasal: Double,
        minBg: Double,
        maxBg: Double,
        targetBg: Double,
        basalRate: Double,
        iobArray: Array<IobTotal>,
        glucoseStatus: GlucoseStatus,
        mealData: MealData,
        autosensDataRatio: Double,
        tempTargetSet: Boolean,
        microBolusAllowed: Boolean,
        uamAllowed: Boolean,
        advancedFiltering: Boolean,
        flatBGsDetected: Boolean
    ) {
        val pump = activePlugin.activePump
        val pumpBolusStep = pump.pumpDescription.bolusStep
        this.profile.put("max_iob", maxIob)
        //mProfile.put("dia", profile.getDia());
        this.profile.put("type", "current")
        this.profile.put("max_daily_basal", profile.getMaxDailyBasal())
        this.profile.put("max_basal", maxBasal)
        this.profile.put("min_bg", minBg)
        this.profile.put("max_bg", maxBg)
        this.profile.put("target_bg", targetBg)
        this.profile.put("carb_ratio", profile.getIc())
        this.profile.put("sens", profile.getIsfMgdl())
        this.profile.put("max_daily_safety_multiplier", sp.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3))
        this.profile.put("current_basal_safety_multiplier", sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0))
        this.profile.put("lgsThreshold", profileUtil.convertToMgdlDetect(sp.getDouble(R.string.key_lgs_threshold, 65.0)))

        //mProfile.put("high_temptarget_raises_sensitivity", SP.getBoolean(R.string.key_high_temptarget_raises_sensitivity, SMBDefaults.high_temptarget_raises_sensitivity));
        this.profile.put(
            "high_temptarget_raises_sensitivity",
            sp.getBoolean(app.aaps.core.utils.R.string.key_high_temptarget_raises_sensitivity, SMBDefaults.high_temptarget_raises_sensitivity)
        )
        //mProfile.put("low_temptarget_lowers_sensitivity", SP.getBoolean(R.string.key_low_temptarget_lowers_sensitivity, SMBDefaults.low_temptarget_lowers_sensitivity));
        this.profile.put("low_temptarget_lowers_sensitivity", sp.getBoolean(app.aaps.core.utils.R.string.key_low_temptarget_lowers_sensitivity, SMBDefaults.low_temptarget_lowers_sensitivity))
        this.profile.put("sensitivity_raises_target", sp.getBoolean(R.string.key_sensitivity_raises_target, SMBDefaults.sensitivity_raises_target))
        this.profile.put("resistance_lowers_target", sp.getBoolean(R.string.key_resistance_lowers_target, SMBDefaults.resistance_lowers_target))
        this.profile.put("adv_target_adjustments", SMBDefaults.adv_target_adjustments)
        this.profile.put("exercise_mode", SMBDefaults.exercise_mode)
        this.profile.put("half_basal_exercise_target", SMBDefaults.half_basal_exercise_target)
        this.profile.put("maxCOB", SMBDefaults.maxCOB)
        this.profile.put("skip_neutral_temps", pump.setNeutralTempAtFullHour())
        // min_5m_carbimpact is not used within SMB determinebasal
        //if (mealData.usedMinCarbsImpact > 0) {
        //    mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        //} else {
        //    mProfile.put("min_5m_carbimpact", SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact));
        //}
        this.profile.put("remainingCarbsCap", SMBDefaults.remainingCarbsCap)
        this.profile.put("enableUAM", uamAllowed)
        this.profile.put("A52_risk_enable", SMBDefaults.A52_risk_enable)
        val smbEnabled = sp.getBoolean(R.string.key_use_smb, false)
        this.profile.put("SMBInterval", sp.getInt(R.string.key_smb_interval, SMBDefaults.SMBInterval))
        this.profile.put("enableSMB_with_COB", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_COB, false))
        this.profile.put("enableSMB_with_temptarget", smbEnabled && sp.getBoolean(R.string.key_enableSMB_with_temptarget, false))
        this.profile.put("allowSMB_with_high_temptarget", smbEnabled && sp.getBoolean(R.string.key_allowSMB_with_high_temptarget, false))
        this.profile.put("enableSMB_always", smbEnabled && sp.getBoolean(R.string.key_enableSMB_always, false) && advancedFiltering)
        this.profile.put("enableSMB_after_carbs", smbEnabled && sp.getBoolean(R.string.key_enableSMB_after_carbs, false) && advancedFiltering)
        this.profile.put("maxSMBBasalMinutes", sp.getInt(R.string.key_smb_max_minutes, SMBDefaults.maxSMBBasalMinutes))
        this.profile.put("maxUAMSMBBasalMinutes", sp.getInt(R.string.key_uam_smb_max_minutes, SMBDefaults.maxUAMSMBBasalMinutes))
        this.profile.put("maxUAMSMBBasalMinutes", sp.getInt(R.string.key_uam_smb_max_minutes, SMBDefaults.maxUAMSMBBasalMinutes))
        //set the min SMB amount to be the amount set by the pump.
        this.profile.put("bolus_increment", pumpBolusStep)
        this.profile.put("carbsReqThreshold", sp.getInt(R.string.key_carbsReqThreshold, SMBDefaults.carbsReqThreshold))
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_max", SafeParse.stringToDouble(sp.getString(app.aaps.core.utils.R.string.key_openapsama_autosens_max, "1.2")))
        this.profile.put("autosens_min", SafeParse.stringToDouble(sp.getString(app.aaps.core.utils.R.string.key_openapsama_autosens_min, "0.7")))
        //set the min SMB amount to be the amount set by the pump.
        if (profileFunction.getUnits() == GlucoseUnit.MMOL) {
            this.profile.put("out_units", "mmol/L")
        }
        val now = System.currentTimeMillis()
        val tb = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
        currentTemp.put("temp", "absolute")
        currentTemp.put("duration", tb?.plannedRemainingMinutes ?: 0)
        currentTemp.put("rate", tb?.convertedToAbsolute(now, profile) ?: 0.0)
        // as we have non default temps longer than 30 mintues
        if (tb != null) currentTemp.put("minutesrunning", tb.getPassedDurationToTimeInMinutes(now))

        iobData = iobCobCalculator.convertToJSONArray(iobArray)
        mGlucoseStatus.put("glucose", glucoseStatus.glucose)
        mGlucoseStatus.put("noise", glucoseStatus.noise)
        if (sp.getBoolean(R.string.key_always_use_shortavg, false)) {
            mGlucoseStatus.put("delta", glucoseStatus.shortAvgDelta)
        } else {
            mGlucoseStatus.put("delta", glucoseStatus.delta)
        }

        mGlucoseStatus.put("short_avgdelta", glucoseStatus.shortAvgDelta)
        mGlucoseStatus.put("long_avgdelta", glucoseStatus.longAvgDelta)
        mGlucoseStatus.put("date", glucoseStatus.date)
        this.mealData.put("carbs", mealData.carbs)
        this.mealData.put("mealCOB", mealData.mealCOB)
        this.mealData.put("slopeFromMaxDeviation", mealData.slopeFromMaxDeviation)
        this.mealData.put("slopeFromMinDeviation", mealData.slopeFromMinDeviation)
        this.mealData.put("lastBolusTime", mealData.lastBolusTime)
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)

        val insulin = activePlugin.activeInsulin
        val insulinDivisor = when {
            insulin.peak > 65 -> 55 // lyumjev peak: 45
            insulin.peak > 50 -> 65 // ultra rapid peak: 55
            else              -> 75 // rapid peak: 75
        }
        val isf = isfCalculator.calculateAndSetToProfile(
            profile.getIsfMgdl(),
            if (profile is ProfileSealed.EPS) profile.value.originalPercentage else 100,
            targetBg,
            insulinDivisor, glucoseStatus, tempTargetSet, this.profile)

        if (sp.getBoolean(app.aaps.core.utils.R.string.key_dynamic_isf_adjust_sensitivity, false))
            autosensData.put("ratio", isf.ratio)
        else
            autosensData.put("ratio", 1.0)

        this.profile.put("normalTarget", 99)

        this.microBolusAllowed = microBolusAllowed
        smbAlwaysAllowed = advancedFiltering
        currentTime = now
        this.flatBGsDetected = flatBGsDetected
    }

    init {
        injector.androidInjector().inject(this)
    }
}