package info.nightscout.interfaces.pump

import android.content.Context
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class PumpEnactResult(injector: HasAndroidInjector) {

    @Inject lateinit var context: Context

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
    var queued = false

    fun success(success: Boolean): PumpEnactResult = this.also { this.success = success }
    fun enacted(enacted: Boolean): PumpEnactResult = this.also { it.enacted = enacted }
    fun comment(comment: String): PumpEnactResult = this.also { it.comment = comment }
    fun comment(comment: Int): PumpEnactResult = this.also { it.comment = context.getString(comment) }
    fun duration(duration: Int): PumpEnactResult = this.also { it.duration = duration }
    fun absolute(absolute: Double): PumpEnactResult = this.also { it.absolute = absolute }
    fun percent(percent: Int): PumpEnactResult = this.also { it.percent = percent }
    fun isPercent(isPercent: Boolean): PumpEnactResult = this.also { it.isPercent = isPercent }
    fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = this.also { it.isTempCancel = isTempCancel }
    fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = this.also { it.bolusDelivered = bolusDelivered }
    fun queued(queued: Boolean): PumpEnactResult = this.also { it.queued = queued }
}