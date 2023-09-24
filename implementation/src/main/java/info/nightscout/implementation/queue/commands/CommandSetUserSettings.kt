package info.nightscout.implementation.queue.commands

import app.aaps.interfaces.logging.LTag
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.pump.Dana
import app.aaps.interfaces.pump.Diaconn
import app.aaps.interfaces.pump.Medtrum
import app.aaps.interfaces.pump.PumpEnactResult
import app.aaps.interfaces.queue.Callback
import app.aaps.interfaces.queue.Command
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

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.set_user_settings)

    override fun log(): String = "SET USER SETTINGS"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}