package info.nightscout.androidaps.data

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject

class PumpEnactResult(injector: HasAndroidInjector) {

    @Inject lateinit var resourceHelper: ResourceHelper

    init {
        injector.androidInjector().inject(this)
    }

    var success = false // request was processed successfully (but possible no change was needed)
    var enacted = false // request was processed successfully and change has been made
    var comment = ""

    // Result of basal change
    var duration = -1 // duration set [minutes]
    var absolute = -1.0 // absolute rate [U/h] , isPercent = false
    var percent = -1 // percent of current basal [%] (100% = current basal), isPercent = true
    var isPercent = false // if true percent is used, otherwise absolute
    var isTempCancel = false // if true we are canceling temp basal

    // Result of treatment delivery
    var bolusDelivered = 0.0 // real value of delivered insulin
    var carbsDelivered = 0.0 // real value of delivered carbs
    var queued = false

    fun success(success: Boolean): PumpEnactResult = this.also { this.success = success }
    fun enacted(enacted: Boolean): PumpEnactResult = this.also { it.enacted = enacted }
    fun comment(comment: String): PumpEnactResult = this.also { it.comment = comment }
    fun comment(comment: Int): PumpEnactResult = this.also { it.comment = resourceHelper.gs(comment) }
    fun duration(duration: Int): PumpEnactResult = this.also { it.duration = duration }
    fun absolute(absolute: Double): PumpEnactResult = this.also { it.absolute = absolute }
    fun percent(percent: Int): PumpEnactResult = this.also { it.percent = percent }
    fun isPercent(isPercent: Boolean): PumpEnactResult = this.also { it.isPercent = isPercent }
    fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = this.also { it.isTempCancel = isTempCancel }
    fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = this.also { it.bolusDelivered = bolusDelivered }
    fun carbsDelivered(carbsDelivered: Double): PumpEnactResult = this.also { it.carbsDelivered = carbsDelivered }
    fun queued(queued: Boolean): PumpEnactResult = this.also { it.queued = queued }

    fun log(): String {
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

    override fun toString(): String {
        var ret = resourceHelper.gs(R.string.success) + ": " + success
        if (enacted) {
            when {
                bolusDelivered > 0 -> {
                    ret += "\n${resourceHelper.gs(R.string.enacted)}: $enacted"
                    ret += "\n${resourceHelper.gs(R.string.comment)}: $comment"
                    ret += "\n${resourceHelper.gs(R.string.configbuilder_insulin)}: $bolusDelivered ${resourceHelper.gs(R.string.insulin_unit_shortname)}"
                }

                isTempCancel       -> {
                    ret += "\n${resourceHelper.gs(R.string.enacted)}: $enacted"
                    if (comment.isNotEmpty()) ret += "\n${resourceHelper.gs(R.string.comment)}: $comment"
                    ret += "\n${resourceHelper.gs(R.string.canceltemp)}"
                }

                isPercent          -> {
                    ret += "\n${resourceHelper.gs(R.string.enacted)}: $enacted"
                    if (comment.isNotEmpty()) ret += "\n${resourceHelper.gs(R.string.comment)}: $comment"
                    ret += "\n${resourceHelper.gs(R.string.duration)}: $duration min"
                    ret += "\n${resourceHelper.gs(R.string.percent)}: $percent%"
                }

                else               -> {
                    ret += "\n${resourceHelper.gs(R.string.enacted)}: $enacted"
                    if (comment.isNotEmpty()) ret += "\n${resourceHelper.gs(R.string.comment)}: $comment"
                    ret += "\n${resourceHelper.gs(R.string.duration)}: $duration min"
                    ret += "\n${resourceHelper.gs(R.string.absolute)}: $absolute U/h"
                }
            }
        } else {
            ret += "\n${resourceHelper.gs(R.string.comment)}: $comment"
        }
        return ret
    }

    fun toHtml(): String {
        var ret = "<b>" + resourceHelper.gs(R.string.success) + "</b>: " + success
        if (queued) {
            ret = resourceHelper.gs(R.string.waitingforpumpresult)
        } else if (enacted) {
            when {
                bolusDelivered > 0         -> {
                    ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted
                    if (comment.isNotEmpty()) ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment
                    ret += "<br><b>" + resourceHelper.gs(R.string.smb_shortname) + "</b>: " + bolusDelivered + " " + resourceHelper.gs(R.string.insulin_unit_shortname)
                }

                isTempCancel               -> {
                    ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted
                    ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment +
                        "<br>" + resourceHelper.gs(R.string.canceltemp)
                }

                isPercent && percent != -1 -> {
                    ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted
                    if (comment.isNotEmpty()) ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment
                    ret += "<br><b>" + resourceHelper.gs(R.string.duration) + "</b>: " + duration + " min"
                    ret += "<br><b>" + resourceHelper.gs(R.string.percent) + "</b>: " + percent + "%"
                }

                absolute != -1.0           -> {
                    ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted
                    if (comment.isNotEmpty()) ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment
                    ret += "<br><b>" + resourceHelper.gs(R.string.duration) + "</b>: " + duration + " min"
                    ret += "<br><b>" + resourceHelper.gs(R.string.absolute) + "</b>: " + DecimalFormatter.to2Decimal(absolute) + " U/h"
                }
            }
        } else {
            if (comment.isNotEmpty()) ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment
        }
        return ret
    }

    fun json(profile: Profile): JSONObject {
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
                val abs = Round.roundTo(profile.getBasal() * percent / 100, 0.01)
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