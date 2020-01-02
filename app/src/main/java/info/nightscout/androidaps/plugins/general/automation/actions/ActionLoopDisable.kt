package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.queue.Callback

class ActionLoopDisable(mainApp: MainApp) : Action(mainApp) {

    override fun friendlyName(): Int = R.string.disableloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.disableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        if (loopPlugin.isEnabled(PluginType.LOOP)) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, false)
            configBuilderPlugin.storeSettings("ActionLoopDisable")
            configBuilderPlugin.commandQueue.cancelTempBasal(true, object : Callback() {
                override fun run() {
                    rxBus.send(EventRefreshOverview("ActionLoopDisable"))
                    callback.result(result).run()
                }
            })
        } else {
            callback.result(PumpEnactResult().success(true).comment(R.string.alreadydisabled)).run()
        }
    }
}
