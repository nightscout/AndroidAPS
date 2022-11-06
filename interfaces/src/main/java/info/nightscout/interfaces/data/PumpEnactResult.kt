package info.nightscout.interfaces.data

import org.json.JSONObject

interface PumpEnactResult {

    var success: Boolean // request was processed successfully (but possible no change was needed)
    var enacted: Boolean // request was processed successfully and change has been made
    var comment: String

    // Result of basal change
    var duration: Int // duration set [minutes]
    var absolute: Double // absolute rate [U/h] , isPercent = false
    var percent: Int // percent of current basal [%] (100% = current basal), isPercent = true
    var isPercent: Boolean // if true percent is used, otherwise absolute
    var isTempCancel: Boolean // if true we are canceling temp basal

    // Result of treatment delivery
    var bolusDelivered: Double // real value of delivered insulin
    var carbsDelivered: Double // real value of delivered carbs
    var queued: Boolean

    fun success(success: Boolean): PumpEnactResult
    fun enacted(enacted: Boolean): PumpEnactResult
    fun comment(comment: String): PumpEnactResult
    fun comment(comment: Int): PumpEnactResult
    fun duration(duration: Int): PumpEnactResult
    fun absolute(absolute: Double): PumpEnactResult
    fun percent(percent: Int): PumpEnactResult
    fun isPercent(isPercent: Boolean): PumpEnactResult
    fun isTempCancel(isTempCancel: Boolean): PumpEnactResult
    fun bolusDelivered(bolusDelivered: Double): PumpEnactResult
    fun carbsDelivered(carbsDelivered: Double): PumpEnactResult
    fun queued(queued: Boolean): PumpEnactResult

    fun log(): String
    fun toHtml(): String
    fun toText(): String
    fun json(baseBasal: Double): JSONObject
}