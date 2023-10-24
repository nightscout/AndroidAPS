package app.aaps.plugins.automation.actions

import androidx.annotation.DrawableRes
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ActionLoopEnable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.enableloop
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.enableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_play_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!loopPlugin.isEnabled()) {
            (loopPlugin as PluginBase).setPluginEnabled(PluginType.LOOP, true)
            configBuilder.storeSettings("ActionLoopEnable")
            rxBus.send(EventRefreshOverview("ActionLoopEnable"))
            uel.log(app.aaps.core.data.ue.Action.LOOP_ENABLED, Sources.Automation, title)
            callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.alreadyenabled)).run()
        }
    }

    override fun isValid(): Boolean = true
}