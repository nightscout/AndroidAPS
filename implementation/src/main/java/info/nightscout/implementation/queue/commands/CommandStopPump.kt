package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResultObject
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.implementation.R
import info.nightscout.interfaces.pump.Insight
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandStopPump(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.STOP_PUMP, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is Insight) {
            val result = pump.stopPump()
            callback?.result(result)?.run()
        }
    }

    override fun status(): String = rh.gs(R.string.stop_pump)

    override fun log(): String = "STOP PUMP"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResultObject(injector).success(false).comment(info.nightscout.core.main.R.string.connectiontimedout))?.run()
    }
}