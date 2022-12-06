package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandCancelTempBasal(
    injector: HasAndroidInjector,
    private val enforceNew: Boolean,
    callback: Callback?
) : Command(injector, CommandType.TEMPBASAL, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r = activePlugin.activePump.cancelTempBasal(enforceNew)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.uel_accepts_temp_basal)

    override fun log(): String = "CANCEL TEMPBASAL"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}