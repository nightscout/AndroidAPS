package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Insight
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandInsightSetTBROverNotification(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val enabled: Boolean,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.INSIGHT_SET_TBR_OVER_ALARM

    override suspend fun execute(): PumpEnactResult {
        val pump = activePlugin.activePumpInternal
        return if (pump is Insight) {
            pump.setTBROverNotification(enabled).also {
                aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${it.success} enacted: ${it.enacted}")
            }
        } else {
            pumpEnactResultProvider.get().success(true).enacted(false)
        }
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.insight_set_tbr_over_notification)

    @Suppress("SpellCheckingInspection")
    override fun log(): String = "INSIGHTSETTBROVERNOTIFICATION"
}
