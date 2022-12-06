package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CustomCommand
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandCustomCommand(
    injector: HasAndroidInjector,
    val customCommand: CustomCommand,
    callback: Callback?
) : Command(injector, CommandType.CUSTOM_COMMAND, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        activePlugin.activePump.executeCustomCommand(customCommand)?.let {
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${it.success} enacted: ${it.enacted}")
            callback?.result(it)?.run()
        }
    }

    override fun status(): String = customCommand.statusDescription

    override fun log(): String = customCommand.statusDescription
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}