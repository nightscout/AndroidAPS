package app.aaps.core.interfaces.aps

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.constraints.Constraint
import kotlinx.serialization.json.JsonObject

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

    /**
     * The algorithm stopped at its first checks, because the CGM data could not be used: too old, in the
     * future, flat for 45 minutes, 38 mg/dL or noisy. It may still replace a high temp with a neutral one or
     * shorten a long zero temp, and it still goes to Nightscout with its reason. But it calculated nothing:
     * no eventual BG, no predictions, no variable sensitivity.
     *
     * Such a result is not stored, on the master or on a client. The stored results are read as
     * calculations, and the ISF for carbs (`IsfForCarbs`) took one without a variable sensitivity as
     * "dynamic ISF off" and switched to the profile ISF (#5413).
     */
    val stoppedEarly: Boolean get() = false

    val predictionsAsGv: MutableList<GV>
    val latestPredictionsTime: Long
    suspend fun isChangeRequested(): Boolean
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

    suspend fun resultAsString(): String
    fun newAndClone(): APSResult
    fun json(): JsonObject?
    fun predictions(): Predictions?
    fun rawData(): Any

    enum class Algorithm {
        UNKNOWN,
        AMA,
        SMB,
        AUTO_ISF
    }
}