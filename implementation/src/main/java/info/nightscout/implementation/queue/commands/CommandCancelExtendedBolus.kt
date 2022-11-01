package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.implementation.R
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class CommandCancelExtendedBolus constructor(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.EXTENDEDBOLUS, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r = activePlugin.activePump.cancelExtendedBolus()
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(R.string.uel_cancel_extended_bolus)

    override fun log(): String = "CANCEL EXTENDEDBOLUS"
}