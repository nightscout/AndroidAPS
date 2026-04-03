package app.aaps.implementation.queue.commands

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Provider

class CommandBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    override val callback: Callback?,
    type: Command.CommandType,
    private val carbsRunnable: Runnable
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>
    @Inject lateinit var bolusProgressData: BolusProgressData

    override var commandType: Command.CommandType

    init {
        injector.androidInjector().inject(this)
        this.commandType = type
    }

    override fun execute() {
        val r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        if (r.success) carbsRunnable.run()
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        if (r.success) {
            bolusProgressData.complete()
            callback?.result(r)?.run()
            // Delay clear so UI can show completion state without blocking queue
            val gen = bolusProgressData.currentGeneration
            Thread {
                SystemClock.sleep(5000)
                bolusProgressData.clearIfSameGeneration(gen)
            }.start()
        } else {
            callback?.result(r)?.run()
            bolusProgressData.clear()
        }
    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) rh.gs(app.aaps.core.ui.R.string.bolus_u_min, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) rh.gs(app.aaps.core.ui.R.string.carbs_g, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun log(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(app.aaps.core.objects.R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
        bolusProgressData.clear()
    }
}
