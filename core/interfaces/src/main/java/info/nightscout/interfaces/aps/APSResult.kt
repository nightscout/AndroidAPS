package info.nightscout.interfaces.aps

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.iob.IobTotal
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

    val predictions: MutableList<GlucoseValue>
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
    fun newAndClone(injector: HasAndroidInjector): APSResult
    fun json(): JSONObject?
}