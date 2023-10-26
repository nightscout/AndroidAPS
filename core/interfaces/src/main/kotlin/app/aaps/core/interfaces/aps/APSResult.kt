package app.aaps.core.interfaces.aps

import android.text.Spanned
import app.aaps.core.data.iob.IobTotal
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.constraints.Constraint
import org.json.JSONObject

interface APSResult {

    var date: Long
    var json: JSONObject?
    var reason: String
    var rate: Double
    var percent: Int
    var duration: Int
    var smb: Double
    var iob: IobTotal?
    var usePercent: Boolean
    var carbsReq: Int
    var carbsReqWithin: Int
    var deliverAt: Long
    var targetBG: Double
    var hasPredictions: Boolean

    val predictions: MutableList<GV>
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

    fun toSpanned(): Spanned
    fun newAndClone(): APSResult
    fun json(): JSONObject?

    fun doClone(newResult: APSResult) {
        newResult.date = date
        newResult.reason = reason
        newResult.rate = rate
        newResult.duration = duration
        newResult.isTempBasalRequested = isTempBasalRequested
        newResult.iob = iob
        newResult.json = JSONObject(json.toString())
        newResult.hasPredictions = hasPredictions
        newResult.smb = smb
        newResult.deliverAt = deliverAt
        newResult.rateConstraint = rateConstraint
        newResult.smbConstraint = smbConstraint
        newResult.percent = percent
        newResult.usePercent = usePercent
        newResult.carbsReq = carbsReq
        newResult.carbsReqWithin = carbsReqWithin
        newResult.targetBG = targetBG
    }
}