package app.aaps.plugins.aps.betacell

import android.text.Html
import android.text.Spanned
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.constraints.Constraint
import org.json.JSONObject

class BetaCellApsResult : APSResult {

    // ── Champs β-cell spécifiques ─────────────────────────────────────────
    var betaSecretion: Double = 0.0
    var systemicInsulin: Double = 0.0
    var isf_used: Double = 0.0
    var slope_used: Double = 0.0
    var zone: GlucoseZone = GlucoseZone.TARGET

    // ── APSResult — champs ────────────────────────────────────────────────
    override var rate: Double = 0.0
    override var smb: Double = 0.0
    override var reason: String = ""
    override var deliverAt: Long = 0L
    override var isTempBasalRequested: Boolean = false
    override var duration: Int = 30
    override var date: Long = 0L
    override var targetBG: Double = 0.0
    override var percent: Int = 0
    override var usePercent: Boolean = false
    override var carbsReq: Int = 0
    override var carbsReqWithin: Int = 0
    override var hasPredictions: Boolean = false
    override var variableSens: Double? = null
    override var isfMgdlForCarbs: Double? = null
    override var scriptDebug: List<String>? = null
    override var inputConstraints: Constraint<Double>? = null
    override var rateConstraint: Constraint<Double>? = null
    override var percentConstraint: Constraint<Int>? = null
    override var smbConstraint: Constraint<Double>? = null
    override var algorithm: APSResult.Algorithm = APSResult.Algorithm.SMB
    override var autosensResult: AutosensResult? = null
    override var iobData: Array<IobTotal>? = null
    override var glucoseStatus: GlucoseStatus? = null
    override var currentTemp: CurrentTemp? = null
    override var oapsProfile: OapsProfile? = null
    override var oapsProfileAutoIsf: OapsProfileAutoIsf? = null
    override var mealData: MealData? = null

    override val predictionsAsGv: MutableList<GV> get() = mutableListOf()
    override val latestPredictionsTime: Long get() = 0L
    override val isChangeRequested: Boolean get() = isTempBasalRequested || smb > 0.0
    override val carbsRequiredText: String get() = ""

    override fun with(result: RT): APSResult = this
    override fun resultAsString(): String = toString()
    override fun resultAsSpanned(): Spanned =
        Html.fromHtml(reason.replace("\n", "<br/>"), Html.FROM_HTML_MODE_COMPACT)
    override fun newAndClone(): APSResult = BetaCellApsResult().also { c ->
        c.rate = rate; c.smb = smb; c.reason = reason
        c.deliverAt = deliverAt; c.isTempBasalRequested = isTempBasalRequested
        c.duration = duration; c.betaSecretion = betaSecretion
        c.systemicInsulin = systemicInsulin; c.isf_used = isf_used
        c.slope_used = slope_used; c.zone = zone
    }
    override fun json(): JSONObject = JSONObject().apply {
        put("rate", rate); put("smb", smb); put("reason", reason)
        put("duration", duration); put("betaSecretion", betaSecretion)
        put("systemicInsulin", systemicInsulin)
        put("isf", isf_used); put("slope", slope_used); put("zone", zone.name)
    }
    override fun predictions(): Predictions? = null
    override fun rawData(): Any = this

    override fun toString(): String = buildString {
        append("BetaCellApsResult {")
        append(" rate=${"%.2f".format(rate)} U/h")
        append(" smb=${"%.3f".format(smb)} U")
        append(" β=${"%.3f".format(betaSecretion)} U")
        append(" sys=${"%.3f".format(systemicInsulin)} U")
        append(" ISF=${"%.1f".format(isf_used)}")
        append(" slope=${"%.2f".format(slope_used)}")
        append(" zone=$zone | $reason }")
    }
}

enum class GlucoseZone { HYPO, TARGET, HYPER }
