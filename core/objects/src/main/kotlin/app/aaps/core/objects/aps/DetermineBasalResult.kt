package app.aaps.core.objects.aps

import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.utils.DateUtil
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.max

class DetermineBasalResult @Inject constructor(injector: HasAndroidInjector) : APSResultObject(injector) {

    @Inject lateinit var dateUtil: DateUtil

    private var eventualBG = 0.0
    private var snoozeBG = 0.0
    override var variableSens: Double? = null

    lateinit var result: RT

    constructor(injector: HasAndroidInjector, result: RT) : this(injector) {
        this.result = result
        hasPredictions = true
        date = result.timestamp ?: dateUtil.now()

        reason = result.reason.toString()
        eventualBG = result.eventualBG ?: 0.0
        snoozeBG = result.snoozeBG ?: 0.0
        carbsReq = result.carbsReq ?: 0
        carbsReqWithin = result.carbsReqWithin ?: 0
        if (result.rate != null && result.duration != null) {
            isTempBasalRequested = true
            rate = max(0.0, result.rate ?: error("!"))
            duration = result.duration ?: error("!")
        }
        smb = result.units ?: 0.0
        targetBG = result.targetBG ?: 0.0
        deliverAt = result.deliverAt ?: 0L
        variableSens = result.variable_sens
    }

    override fun newAndClone(): DetermineBasalResult = DetermineBasalResult(injector, result)
    override fun json(): JSONObject = JSONObject(result.serialize())

    override fun predictions(): Predictions? = result.predBGs

}