package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissBolusProgressIfRunning
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?,
    type: CommandType,
    private val carbsRunnable: Runnable
) : Command(injector, type, callback) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        if (r.success) carbsRunnable.run()
        BolusProgressData.bolusEnded = true
        rxBus.send(EventDismissBolusProgressIfRunning(r.success, detailedBolusInfo.id))
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) rh.gs(app.aaps.core.ui.R.string.bolus_u_min, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) rh.gs(app.aaps.core.ui.R.string.carbs_g, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun log(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(app.aaps.core.main.R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}