package info.nightscout.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResultImpl
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.interfaces.PluginType
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.interfaces.queue.Callback
import info.nightscout.automation.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshOverview
import javax.inject.Inject

class ActionLoopDisable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = R.string.disableloop
    override fun shortDescription(): String = rh.gs(R.string.disableloop)
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
            callback.result(PumpEnactResultImpl(injector).success(true).comment(R.string.alreadydisabled)).run()
        }
    }

    override fun isValid(): Boolean = true
}
