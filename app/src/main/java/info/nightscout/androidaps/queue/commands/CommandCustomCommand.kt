package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandCustomCommand(
    injector: HasAndroidInjector,
    val customCommand: CustomCommand,
    callback: Callback?
) : Command(injector, CommandType.CUSTOM_COMMAND, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val result = activePlugin.activePump.executeCustomCommand(customCommand)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${result?.success} enacted: ${result?.enacted}")
        callback?.result(result)?.run()
    }

    override fun status(): String {
        return customCommand.statusDescription
    }
}