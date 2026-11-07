package app.aaps.core.interfaces.pump

import app.aaps.core.keys.interfaces.TextRef

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
    var queued: Boolean

    /**
     * The command was dropped on purpose - the queue was cleared, an import replaced the settings,
     * a newer command of the same kind superseded it - instead of being tried and failing.
     *
     * The caller is still told it did not happen ([success] is false for that), but this is not an
     * error to alarm about, in the same way a bolus the user stopped is not.
     */
    var cancelled: Boolean

    fun success(success: Boolean): PumpEnactResult
    fun cancelled(cancelled: Boolean): PumpEnactResult
    fun enacted(enacted: Boolean): PumpEnactResult
    fun comment(comment: String): PumpEnactResult
    /**
     * The comment as a [TextRef], resolved by the implementation.
     *
     * An Android string resource id form exists as an extension in androidMain, so a driver written
     * against resource ids needs no resolver of its own.
     */
    fun comment(ref: TextRef): PumpEnactResult
    fun duration(duration: Int): PumpEnactResult
    fun absolute(absolute: Double): PumpEnactResult
    fun percent(percent: Int): PumpEnactResult
    fun isPercent(isPercent: Boolean): PumpEnactResult
    fun isTempCancel(isTempCancel: Boolean): PumpEnactResult
    fun bolusDelivered(bolusDelivered: Double): PumpEnactResult
    fun queued(queued: Boolean): PumpEnactResult
}