package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandStopPump(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.STOP_PUMP, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is LocalInsightPlugin) {
            val result = pump.stopPump()
            callback?.result(result)?.run()
        }
    }

    override fun status(): String = "STOP PUMP"
}