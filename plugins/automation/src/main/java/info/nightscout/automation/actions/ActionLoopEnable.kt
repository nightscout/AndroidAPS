package info.nightscout.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshOverview
import javax.inject.Inject

class ActionLoopEnable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.enableloop
    override fun shortDescription(): String = rh.gs(info.nightscout.core.ui.R.string.enableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_play_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!loopPlugin.isEnabled()) {
            (loopPlugin as PluginBase).setPluginEnabled(PluginType.LOOP, true)
            configBuilder.storeSettings("ActionLoopEnable")
            rxBus.send(EventRefreshOverview("ActionLoopEnable"))
            uel.log(UserEntry.Action.LOOP_ENABLED, Sources.Automation, title)
            callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadyenabled)).run()
        }
    }

    override fun isValid(): Boolean = true
}