package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.queue.Callback

class ActionLoopEnable(mainApp: MainApp) : Action(mainApp) {

    override fun friendlyName(): Int = R.string.enableloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.enableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_play_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!loopPlugin.isEnabled(PluginType.LOOP)) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, true)
            configBuilderPlugin.storeSettings("ActionLoopEnable")
            rxBus.send(EventRefreshOverview("ActionLoopEnable"))
            callback.result(PumpEnactResult().success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult().success(true).comment(R.string.alreadyenabled))?.run()
        }
    }

}