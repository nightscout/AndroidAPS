package info.nightscout.androidaps.data

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.interfaces.utils.Round
import info.nightscout.interfaces.data.PumpEnactResult
import org.json.JSONObject
import javax.inject.Inject

class PumpEnactResultImpl(injector: HasAndroidInjector) : PumpEnactResult {

    @Inject lateinit var rh: ResourceHelper

    init {
        injector.androidInjector().inject(this)
    }

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
    override var carbsDelivered = 0.0 // real value of delivered carbs
    override var queued = false

    override fun success(success: Boolean): PumpEnactResultImpl = this.also { this.success = success }
    override fun enacted(enacted: Boolean): PumpEnactResultImpl = this.also { it.enacted = enacted }
    override fun comment(comment: String): PumpEnactResultImpl = this.also { it.comment = comment }
    override fun comment(comment: Int): PumpEnactResultImpl = this.also { it.comment = rh.gs(comment) }
    override fun duration(duration: Int): PumpEnactResultImpl = this.also { it.duration = duration }
    override fun absolute(absolute: Double): PumpEnactResultImpl = this.also { it.absolute = absolute }
    override fun percent(percent: Int): PumpEnactResultImpl = this.also { it.percent = percent }
    override fun isPercent(isPercent: Boolean): PumpEnactResultImpl = this.also { it.isPercent = isPercent }
    override fun isTempCancel(isTempCancel: Boolean): PumpEnactResultImpl = this.also { it.isTempCancel = isTempCancel }
    override fun bolusDelivered(bolusDelivered: Double): PumpEnactResultImpl = this.also { it.bolusDelivered = bolusDelivered }
    override fun carbsDelivered(carbsDelivered: Double): PumpEnactResultImpl = this.also { it.carbsDelivered = carbsDelivered }
    override fun queued(queued: Boolean): PumpEnactResultImpl = this.also { it.queued = queued }

    override fun log(): String {
        return "Success: " + success +
            " Enacted: " + enacted +
            " Comment: " + comment +
            " Duration: " + duration +
            " Absolute: " + absolute +
            " Percent: " + percent +
            " IsPercent: " + isPercent +
            " IsTempCancel: " + isTempCancel +
            " bolusDelivered: " + bolusDelivered +
            " carbsDelivered: " + carbsDelivered +
            " Queued: " + queued
    }

    override fun toString(): String = toText()

    override fun toText(): String {
        var ret = rh.gs(R.string.success) + ": " + success
        if (enacted) {
            when {
                bolusDelivered > 0 -> {
                    ret += "\n${rh.gs(R.string.enacted)}: $enacted"
                    ret += "\n${rh.gs(R.string.comment)}: $comment"
                    ret += "\n${rh.gs(R.string.configbuilder_insulin)}: $bolusDelivered ${rh.gs(R.string.insulin_unit_shortname)}"
                }

                isTempCancel       -> {
                    ret += "\n${rh.gs(R.string.enacted)}: $enacted"
                    if (comment.isNotEmpty()) ret += "\n${rh.gs(R.string.comment)}: $comment"
                    ret += "\n${rh.gs(R.string.canceltemp)}"
                }

                isPercent          -> {
                    ret += "\n${rh.gs(R.string.enacted)}: $enacted"
                    if (comment.isNotEmpty()) ret += "\n${rh.gs(R.string.comment)}: $comment"
                    ret += "\n${rh.gs(R.string.duration)}: $duration min"
                    ret += "\n${rh.gs(R.string.percent)}: $percent%"
                }

                else               -> {
                    ret += "\n${rh.gs(R.string.enacted)}: $enacted"
                    if (comment.isNotEmpty()) ret += "\n${rh.gs(R.string.comment)}: $comment"
                    ret += "\n${rh.gs(R.string.duration)}: $duration min"
                    ret += "\n${rh.gs(R.string.absolute)}: $absolute U/h"
                }
            }
        } else {
            ret += "\n${rh.gs(R.string.comment)}: $comment"
        }
        return ret
    }

    override fun toHtml(): String {
        var ret = "<b>" + rh.gs(R.string.success) + "</b>: " + success
        if (queued) {
            ret = rh.gs(R.string.waitingforpumpresult)
        } else if (enacted) {
            when {
                bolusDelivered > 0         -> {
                    ret += "<br><b>" + rh.gs(R.string.enacted) + "</b>: " + enacted
                    if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(R.string.comment) + "</b>: " + comment
                    ret += "<br><b>" + rh.gs(R.string.smb_shortname) + "</b>: " + bolusDelivered + " " + rh.gs(R.string.insulin_unit_shortname)
                }

                isTempCancel               -> {
                    ret += "<br><b>" + rh.gs(R.string.enacted) + "</b>: " + enacted
                    ret += "<br><b>" + rh.gs(R.string.comment) + "</b>: " + comment +
                        "<br>" + rh.gs(R.string.canceltemp)
                }

                isPercent && percent != -1 -> {
                    ret += "<br><b>" + rh.gs(R.string.enacted) + "</b>: " + enacted
                    if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(R.string.comment) + "</b>: " + comment
                    ret += "<br><b>" + rh.gs(R.string.duration) + "</b>: " + duration + " min"
                    ret += "<br><b>" + rh.gs(R.string.percent) + "</b>: " + percent + "%"
                }

                absolute != -1.0           -> {
                    ret += "<br><b>" + rh.gs(R.string.enacted) + "</b>: " + enacted
                    if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(R.string.comment) + "</b>: " + comment
                    ret += "<br><b>" + rh.gs(R.string.duration) + "</b>: " + duration + " min"
                    ret += "<br><b>" + rh.gs(R.string.absolute) + "</b>: " + DecimalFormatter.to2Decimal(absolute) + " U/h"
                }
            }
        } else {
            if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(R.string.comment) + "</b>: " + comment
        }
        return ret
    }

    override fun json(baseBasal: Double): JSONObject {
        val result = JSONObject()
        when {
            bolusDelivered > 0 -> {
                result.put("smb", bolusDelivered)
            }

            isTempCancel       -> {
                result.put("rate", 0)
                result.put("duration", 0)
            }

            isPercent          -> {
                // Nightscout is expecting absolute value
                val abs = Round.roundTo(baseBasal * percent / 100, 0.01)
                result.put("rate", abs)
                result.put("duration", duration)
            }

            else               -> {
                result.put("rate", absolute)
                result.put("duration", duration)
            }
        }
        return result
    }
}