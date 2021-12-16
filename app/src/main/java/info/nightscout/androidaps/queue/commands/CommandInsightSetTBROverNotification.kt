package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandInsightSetTBROverNotification constructor(
    injector: HasAndroidInjector,
    private val enabled: Boolean,
    callback: Callback?
) : Command(injector, CommandType.INSIGHT_SET_TBR_OVER_ALARM, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is LocalInsightPlugin) {
            val result = pump.setTBROverNotification(enabled)
            callback?.result(result)?.run()
        }
    }

    @Suppress("SpellCheckingInspection")
    override fun status(): String = rh.gs(R.string.insight_set_tbr_over_notification)

    override fun log(): String = "INSIGHTSETTBROVERNOTIFICATION"
}