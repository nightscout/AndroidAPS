package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?,
    type: CommandType
) : Command(injector, type, callback) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        BolusProgressDialog.bolusEnded = true
        rxBus.send(EventDismissBolusProgressIfRunning(r))
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(R.string.formatinsulinunits, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }
}