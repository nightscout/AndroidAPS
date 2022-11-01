package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Dana
import info.nightscout.androidaps.interfaces.Diaconn
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.implementation.R
import info.nightscout.shared.logging.LTag
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
    }

    override fun status(): String = rh.gs(R.string.set_user_settings)

    override fun log(): String = "SET USER SETTINGS"
}