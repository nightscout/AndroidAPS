package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandCustomCommand(
    injector: HasAndroidInjector,
    val customCommand: CustomCommand,
    override val callback: Callback?,
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var instantiator: Instantiator

    init {
        injector.androidInjector().inject(this)
    }

    override val commandType: Command.CommandType = Command.CommandType.CUSTOM_COMMAND

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
        callback?.result(instantiator.providePumpEnactResult().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}