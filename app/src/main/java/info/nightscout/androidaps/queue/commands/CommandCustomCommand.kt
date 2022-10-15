package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.queue.Callback
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
}