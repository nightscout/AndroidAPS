package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Provider

class CommandLoadTDDs(
    injector: HasAndroidInjector,
    override val callback: Callback?,
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    init {
        injector.androidInjector().inject(this)
    }

    override val commandType: Command.CommandType = Command.CommandType.LOAD_TDD

    override fun execute() {
        val pump = activePlugin.activePump
        val r = pump.loadTDDs()
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.load_tdds)

    override fun log(): String = "LOAD TDDs"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}