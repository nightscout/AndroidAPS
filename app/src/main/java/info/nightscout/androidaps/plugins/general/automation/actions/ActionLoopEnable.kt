package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ActionLoopEnable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var rxBus: RxBusWrapper

    override fun friendlyName(): Int = R.string.enableloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.enableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_play_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!loopPlugin.isEnabled(PluginType.LOOP)) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, true)
            configBuilderPlugin.storeSettings("ActionLoopEnable")
            rxBus.send(EventRefreshOverview("ActionLoopEnable"))
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadyenabled))?.run()
        }
    }

    override fun isValid(): Boolean = true
}