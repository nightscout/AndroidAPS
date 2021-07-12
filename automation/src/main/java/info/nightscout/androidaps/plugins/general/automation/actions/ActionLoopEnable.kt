package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ActionLoopEnable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = R.string.enableloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.enableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_play_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!(loopPlugin as PluginBase).isEnabled()) {
            (loopPlugin as PluginBase).setPluginEnabled(PluginType.LOOP, true)
            configBuilder.storeSettings("ActionLoopEnable")
            rxBus.send(EventRefreshOverview("ActionLoopEnable"))
            uel.log(UserEntry.Action.LOOP_ENABLED, Sources.Automation, title)
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadyenabled))?.run()
        }
    }

    override fun isValid(): Boolean = true
}