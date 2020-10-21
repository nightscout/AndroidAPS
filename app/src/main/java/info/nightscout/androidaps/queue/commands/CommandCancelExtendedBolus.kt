package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandCancelExtendedBolus constructor(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.EXTENDEDBOLUS, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val r = activePlugin.activePump.cancelExtendedBolus()
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = "CANCEL EXTENDEDBOLUS"
}