package app.aaps.implementation.pump

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Inject

class PumpEnactResultObject @Inject constructor(private val rh: ResourceHelper) : PumpEnactResult {

    override var success = false // request was processed successfully (but possible no change was needed)
    override var enacted = false // request was processed successfully and change has been made
    override var comment = ""

    // Result of basal change
    override var duration = -1 // duration set [minutes]
    override var absolute = -1.0 // absolute rate [U/h] , isPercent = false
    override var percent = -1 // percent of current basal [%] (100% = current basal), isPercent = true
    override var isPercent = false // if true percent is used, otherwise absolute
    override var isTempCancel = false // if true we are canceling temp basal

    // Result of treatment delivery
    override var bolusDelivered = 0.0 // real value of delivered insulin
    override var queued = false

    override fun toString() =
        "PumpEnactResultObject(success=$success,enacted=$enacted,comment=$comment,duration=$duration,absolute=$absolute,percent=$percent,isPercent=$isPercent,isTempCancel=$isTempCancel,bolusDelivered=$bolusDelivered,queued=$queued)"

    override fun success(success: Boolean): PumpEnactResultObject = this.also { this.success = success }
    override fun enacted(enacted: Boolean): PumpEnactResultObject = this.also { it.enacted = enacted }
    override fun comment(comment: String): PumpEnactResultObject = this.also { it.comment = comment }
    override fun comment(comment: Int): PumpEnactResultObject = this.also { it.comment = rh.gs(comment) }
    override fun duration(duration: Int): PumpEnactResultObject = this.also { it.duration = duration }
    override fun absolute(absolute: Double): PumpEnactResultObject = this.also { it.absolute = absolute }
    override fun percent(percent: Int): PumpEnactResultObject = this.also { it.percent = percent }
    override fun isPercent(isPercent: Boolean): PumpEnactResultObject = this.also { it.isPercent = isPercent }
    override fun isTempCancel(isTempCancel: Boolean): PumpEnactResultObject = this.also { it.isTempCancel = isTempCancel }
    override fun bolusDelivered(bolusDelivered: Double): PumpEnactResultObject = this.also { it.bolusDelivered = bolusDelivered }
    override fun queued(queued: Boolean): PumpEnactResultObject = this.also { it.queued = queued }
}