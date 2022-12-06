package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.Insight
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandInsightSetTBROverNotification constructor(
    injector: HasAndroidInjector,
    private val enabled: Boolean,
    callback: Callback?
) : Command(injector, CommandType.INSIGHT_SET_TBR_OVER_ALARM, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is Insight) {
            val result = pump.setTBROverNotification(enabled)
            callback?.result(result)?.run()
        }
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.insight_set_tbr_over_notification)

    @Suppress("SpellCheckingInspection")
    override fun log(): String = "INSIGHTSETTBROVERNOTIFICATION"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}