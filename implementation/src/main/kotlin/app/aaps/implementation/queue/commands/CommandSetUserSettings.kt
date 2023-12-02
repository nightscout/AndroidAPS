package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.Diaconn
import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandSetUserSettings(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.SET_USER_SETTINGS, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is Dana) {
            val r = pump.setUserOptions()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }

        if (pump is Diaconn) {
            val r = pump.setUserOptions()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }

        if (pump is Medtrum) {
            val r = pump.setUserOptions()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.set_user_settings)

    override fun log(): String = "SET USER SETTINGS"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}