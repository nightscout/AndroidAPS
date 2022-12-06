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
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshOverview
import javax.inject.Inject

class ActionLoopDisable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.disableloop
    override fun shortDescription(): String = rh.gs(info.nightscout.core.ui.R.string.disableloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        if ((loopPlugin as PluginBase).isEnabled()) {
            (loopPlugin as PluginBase).setPluginEnabled(PluginType.LOOP, false)
            configBuilder.storeSettings("ActionLoopDisable")
            uel.log(UserEntry.Action.LOOP_DISABLED, Sources.Automation, title)
            commandQueue.cancelTempBasal(true, object : Callback() {
                override fun run() {
                    rxBus.send(EventRefreshOverview("ActionLoopDisable"))
                    callback.result(result).run()
                }
            })
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadydisabled)).run()
        }
    }

    override fun isValid(): Boolean = true
}
