package app.aaps.core.interfaces.aps

import android.text.Spanned
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.constraints.Constraint
import org.json.JSONObject

interface APSResult {

    fun with(result: RT): APSResult

    var date: Long
    var reason: String
    var rate: Double
    var percent: Int
    var duration: Int
    var smb: Double
    var usePercent: Boolean
    var carbsReq: Int
    var carbsReqWithin: Int
    var deliverAt: Long
    var targetBG: Double
    var hasPredictions: Boolean
    var variableSens: Double?
    var isfMgdlForCarbs: Double? // used only to pass to AAPS client
    var scriptDebug: List<String>?

    val predictionsAsGv: MutableList<GV>
    val latestPredictionsTime: Long
    val isChangeRequested: Boolean
    var isTempBasalRequested: Boolean
    val isCarbsRequired: Boolean get() = carbsReq > 0
    val isBolusRequested: Boolean get() = smb > 0.0

    val carbsRequiredText: String

    var inputConstraints: Constraint<Double>?
    var rateConstraint: Constraint<Double>?
    var percentConstraint: Constraint<Int>?
    var smbConstraint: Constraint<Double>?

    // Inputs
    var algorithm: Algorithm
    var autosensResult: AutosensResult?
    var iobData: Array<IobTotal>?
    var glucoseStatus: GlucoseStatus?
    var currentTemp: CurrentTemp?
    var oapsProfile: OapsProfile?
    var oapsProfileAutoIsf: OapsProfileAutoIsf?
    var mealData: MealData?

    val iob: IobTotal? get() = iobData?.get(0)

    fun resultAsString(): String
    fun resultAsSpanned(): Spanned
    fun resultAsHtmlString(): String
    fun newAndClone(): APSResult
    fun json(): JSONObject?
    fun predictions(): Predictions?
    fun rawData(): Any

    enum class Algorithm {
        UNKNOWN,
        AMA,
        SMB,
        AUTO_ISF
    }
}