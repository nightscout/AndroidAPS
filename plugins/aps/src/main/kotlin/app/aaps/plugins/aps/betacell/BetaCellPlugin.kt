package app.aaps.plugins.aps.betacell

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import javax.inject.Provider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class BetaCellPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB,
    private val apsResultProvider: Provider<APSResult>,
    private val rxBus: RxBus,
    private val iobCobCalculator: IobCobCalculator
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginName(R.string.betacell_plugin_name)
        .shortName(R.string.betacell_short_name)
        .preferencesId(R.xml.pref_betacell)
        .description(R.string.betacell_description),
    aapsLogger,
    rh
), APS {

    override val algorithm: APSResult.Algorithm = APSResult.Algorithm.SMB
    override var lastAPSResult: APSResult? = null
    override var lastAPSRun: Long = 0L
    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorSMB.getGlucoseStatusData(allowOldData)
    override fun isEnabled(): Boolean = isEnabled(PluginType.APS)

    override fun configuration(): JSONObject = JSONObject().apply {
        put(BooleanKey.BetaCellOpenLoop.key,   preferences.get(BooleanKey.BetaCellOpenLoop))
        put(DoubleKey.BetaCellTargetBg.key,    preferences.get(DoubleKey.BetaCellTargetBg))
        put(DoubleKey.BetaCellHypo.key,        preferences.get(DoubleKey.BetaCellHypo))
        put(DoubleKey.BetaCellHyper.key,       preferences.get(DoubleKey.BetaCellHyper))
        put(DoubleKey.BetaCellBasalPhysio.key, preferences.get(DoubleKey.BetaCellBasalPhysio))
        put(DoubleKey.BetaCellHepatic.key,     preferences.get(DoubleKey.BetaCellHepatic))
        put(DoubleKey.BetaCellIobTau.key,      preferences.get(DoubleKey.BetaCellIobTau))
    }

    override fun applyConfiguration(configuration: JSONObject) {
        if (configuration.has(BooleanKey.BetaCellOpenLoop.key))
            preferences.put(BooleanKey.BetaCellOpenLoop, configuration.getBoolean(BooleanKey.BetaCellOpenLoop.key))
        if (configuration.has(DoubleKey.BetaCellTargetBg.key))
            preferences.put(DoubleKey.BetaCellTargetBg, configuration.getDouble(DoubleKey.BetaCellTargetBg.key))
        if (configuration.has(DoubleKey.BetaCellHypo.key))
            preferences.put(DoubleKey.BetaCellHypo, configuration.getDouble(DoubleKey.BetaCellHypo.key))
        if (configuration.has(DoubleKey.BetaCellHyper.key))
            preferences.put(DoubleKey.BetaCellHyper, configuration.getDouble(DoubleKey.BetaCellHyper.key))
        if (configuration.has(DoubleKey.BetaCellBasalPhysio.key))
            preferences.put(DoubleKey.BetaCellBasalPhysio, configuration.getDouble(DoubleKey.BetaCellBasalPhysio.key))
        if (configuration.has(DoubleKey.BetaCellHepatic.key))
            preferences.put(DoubleKey.BetaCellHepatic, configuration.getDouble(DoubleKey.BetaCellHepatic.key))
        if (configuration.has(DoubleKey.BetaCellIobTau.key))
            preferences.put(DoubleKey.BetaCellIobTau, configuration.getDouble(DoubleKey.BetaCellIobTau.key))
    }

    internal fun prefs(): BetaCellPrefs = BetaCellPrefs(
        targetBg     = preferences.get(DoubleKey.BetaCellTargetBg),
        hypoBg       = preferences.get(DoubleKey.BetaCellHypo).coerceAtLeast(55.0),
        hyperBg      = preferences.get(DoubleKey.BetaCellHyper),
        basalPhysio  = preferences.get(DoubleKey.BetaCellBasalPhysio),
        hepatic      = preferences.get(DoubleKey.BetaCellHepatic),
        iobTauMin    = preferences.get(DoubleKey.BetaCellIobTau),
        isfMin       = preferences.get(DoubleKey.BetaCellIsfMin),
        isfMax       = preferences.get(DoubleKey.BetaCellIsfMax),
        isfWindowH   = preferences.get(IntKey.BetaCellIsfWindowH),
        slopeBrakeT  = preferences.get(DoubleKey.BetaCellSlopeBrakeT),
        slopeBrakeF  = preferences.get(DoubleKey.BetaCellSlopeBrakeF),
        smbEnabled   = preferences.get(BooleanKey.BetaCellSmbEnabled),
        smbMax       = preferences.get(DoubleKey.BetaCellSmbMax),
        smbOffset    = preferences.get(DoubleKey.BetaCellSmbOffset),
        openLoopOnly = preferences.get(BooleanKey.BetaCellOpenLoop),
        debugMode    = preferences.get(BooleanKey.BetaCellDebug)
    )

    override fun invoke(initiator: String, tempBasalFallback: Boolean) {
        val p = prefs()
        aapsLogger.debug(LTag.APS, "BetaCell prefs: $p")
        aapsLogger.debug(LTag.APS, "BetaCellPlugin [$initiator]")

        profileFunction.getProfile() ?: run {
            aapsLogger.error(LTag.APS, "No profile — aborting"); return
        }

        val gs = glucoseStatusCalculatorSMB.getGlucoseStatusData(false) ?: run {
            aapsLogger.warn(LTag.APS, "No CGM data"); return
        }

        val windowMs = p.isfWindowH * 60 * 60 * 1000L
        val cutoff   = System.currentTimeMillis() - windowMs
        val bgHistory: List<Double> = iobCobCalculator.ads.bgReadings
            .filter { it.timestamp >= cutoff && it.value > 39.0 }
            .map    { it.value }

        val calibratedIsf = IsfCalibrator(aapsLogger).calibrate(bgHistory)
        val iobTotal      = iobCobCalculator.calculateIobFromBolus().iob

        val result = calcBetaSecretion(
            bg = gs.glucose, bgDelta = gs.delta, dtMin = 5.0,
            isf = calibratedIsf, iob = iobTotal, p = p
        )
        val rt = RT(
            algorithm        = APSResult.Algorithm.SMB,
            runningDynamicIsf = false,
            timestamp        = System.currentTimeMillis(),
            bg               = gs.glucose,
            rate             = result.rate,
            units            = result.smb,
            duration         = result.duration,
            deliverAt        = System.currentTimeMillis(),
            reason           = StringBuilder(result.reason)
        )
        val apsResult = apsResultProvider.get().with(rt)
        lastAPSResult = apsResult
        lastAPSRun    = System.currentTimeMillis()
        rxBus.send(EventAPSCalculationFinished())
        rxBus.send(EventOpenAPSUpdateGui())

        if (p.openLoopOnly) {
            aapsLogger.info(LTag.APS, "[OPEN LOOP] rate=${result.rate} smb=${result.smb} zone=${result.zone}")
            return
        }
        aapsLogger.info(LTag.APS, "BetaCell → rate=${result.rate} U/h | smb=${result.smb} U | zone=${result.zone}")
    }

    internal fun calcBetaSecretion(
        bg: Double, bgDelta: Double, dtMin: Double,
        isf: Double, iob: Double, p: BetaCellPrefs
    ): BetaCellApsResult {
        if (bg < p.hypoBg) {
            aapsLogger.warn(LTag.APS, "HYPO guard: BG=${bg.roundToInt()} → 0 U")
            return BetaCellApsResult().also { r ->
                r.rate = 0.0; r.smb = 0.0
                r.reason = "HYPO guard: ${bg.roundToInt()} < ${p.hypoBg.roundToInt()} mg/dL"
                r.isf_used = isf; r.zone = GlucoseZone.HYPO
                r.isTempBasalRequested = false
            }
        }

        val slope  = bgDelta / dtMin
        var beta   = if (bg > p.targetBg) ((bg - p.targetBg) / isf) * (dtMin / 60.0) else 0.0
        val braked = slope < p.slopeBrakeT
        if (braked) beta *= p.slopeBrakeF
        beta += p.basalPhysio * (dtMin / 60.0)
        val systemicInsulin = beta * (1.0 - p.hepatic)
        val rate = max(0.0, systemicInsulin / (dtMin / 60.0))
        val smb  = if (p.smbEnabled && bg > p.targetBg + p.smbOffset)
            min(0.3 * systemicInsulin, p.smbMax) else 0.0
        val zone = when {
            bg < p.hypoBg  -> GlucoseZone.HYPO
            bg > p.hyperBg -> GlucoseZone.HYPER
            else           -> GlucoseZone.TARGET
        }

        return BetaCellApsResult().also { r ->
            r.rate = rate; r.smb = smb
            r.isTempBasalRequested = rate > 0.0
            r.duration = 30
            r.betaSecretion = beta; r.systemicInsulin = systemicInsulin
            r.isf_used = isf; r.slope_used = slope; r.zone = zone
            r.reason = buildReason(bg, slope, isf, beta, systemicInsulin, p, braked)
        }
    }

    private fun buildReason(
        bg: Double, slope: Double, isf: Double, beta: Double, systemic: Double,
        p: BetaCellPrefs, braked: Boolean
    ): String = buildString {
        append("BG=${bg.roundToInt()} tgt=${p.targetBg.roundToInt()} ")
        append("ISF=${"%.1f".format(isf)} slope=${"%.2f".format(slope)} ")
        if (braked) append("[brake×${p.slopeBrakeF}] ")
        append("β=${"%.3f".format(beta)}U sys=${"%.3f".format(systemic)}U ")
        if (p.openLoopOnly) append("[OPEN_LOOP]")
    }

    override fun addPreferenceScreen(
        preferenceManager: PreferenceManager,
        parent: PreferenceScreen,
        context: Context,
        requiredKey: String?
    ) {
        if (requiredKey != null &&
            requiredKey != DoubleKey.BetaCellTargetBg.key &&
            requiredKey != BooleanKey.BetaCellOpenLoop.key) return
        with(parent) {
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellTargetBg, title = R.string.betacell_pref_target_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellHypo, title = R.string.betacell_pref_hypo_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellHyper, title = R.string.betacell_pref_hyper_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellBasalPhysio, title = R.string.betacell_pref_basal_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellHepatic, title = R.string.betacell_pref_hepatic_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellIobTau, title = R.string.betacell_pref_iob_tau_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellIsfMin, title = R.string.betacell_pref_isf_min_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellIsfMax, title = R.string.betacell_pref_isf_max_title))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.BetaCellIsfWindowH, title = R.string.betacell_pref_isf_window_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellSlopeBrakeT, title = R.string.betacell_pref_brake_threshold_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellSlopeBrakeF, title = R.string.betacell_pref_brake_factor_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.BetaCellSmbEnabled, title = R.string.betacell_pref_smb_enabled_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellSmbMax, title = R.string.betacell_pref_smb_max_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.BetaCellSmbOffset, title = R.string.betacell_pref_smb_offset_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.BetaCellOpenLoop, title = R.string.betacell_pref_open_loop_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.BetaCellDebug, title = R.string.betacell_pref_debug_title))
        }
    }
}
