package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.resources.ResourceHelper
import kotlin.math.min

/**
 * Custom status message and percent
 */
class EventOverviewBolusProgress(status: String, val id: Long? = null, percent: Int? = null, wearStatus: String? = null) : Event() {

    init {
        if (id == BolusProgressData.id || id == null) {
            BolusProgressData.status = status
            percent?.let { BolusProgressData.percent = it }
            BolusProgressData.wearStatus = wearStatus ?: status
        }
    }

    /**
     * Delivering %1$.2fU and percent is calculated
     */
    constructor(ch: ConcentrationHelper, delivered: PumpInsulin, id: Long? = null) :
        this(
            ch.bolusProgressString(delivered, BolusProgressData.isPriming),
            id = id,
            percent = min((ch.fromPump(delivered, BolusProgressData.isPriming) / BolusProgressData.insulin * 100).toInt(), 100),
            wearStatus = ch.bolusProgressString(delivered, BolusProgressData.insulin, BolusProgressData.isPriming)
        )

    /**
     * For 100%: Bolus %1$.2fU delivered successfully.
     * else: Delivering %1$.2fU.
     */
    constructor(rh: ResourceHelper, percent: Int, id: Long? = null) :
        this(
            status =
                if (percent == 100) rh.gs(R.string.bolus_delivered_successfully, BolusProgressData.insulin)
                else rh.gs(R.string.bolus_delivering, BolusProgressData.insulin * percent / 100.0),
            id = id,
            percent = min(percent, 100),
            wearStatus =
                if (percent == 100) rh.gs(R.string.bolus_delivered_successfully, BolusProgressData.insulin)
                else rh.gs(R.string.bolus_delivered_so_far, BolusProgressData.insulin * percent / 100.0, BolusProgressData.insulin)
    )
}