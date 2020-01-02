package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.Callback

class ActionLoopResume(mainApp: MainApp) : Action(mainApp) {

    override fun friendlyName(): Int = R.string.resumeloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.resumeloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_replay_24dp

    override fun doAction(callback: Callback) {
        if (loopPlugin.isSuspended) {
            loopPlugin.suspendTo(0)
            configBuilderPlugin.storeSettings("ActionLoopResume")
            NSUpload.uploadOpenAPSOffline(0.0)
            rxBus.send(EventRefreshOverview("ActionLoopResume"))
            callback.result(PumpEnactResult().success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult().success(true).comment(R.string.notsuspended))?.run()
        }
    }
}