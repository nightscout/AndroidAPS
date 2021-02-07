package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ActionLoopResume(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var rxBus: RxBusWrapper

    override fun friendlyName(): Int = R.string.resumeloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.resumeloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_replay_24dp

    override fun doAction(callback: Callback) {
        if (loopPlugin.isSuspended) {
            loopPlugin.suspendTo(0)
            configBuilderPlugin.storeSettings("ActionLoopResume")
            loopPlugin.createOfflineEvent(0)
            rxBus.send(EventRefreshOverview("ActionLoopResume"))
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.notsuspended))?.run()
        }
    }

    override fun isValid(): Boolean = true
}