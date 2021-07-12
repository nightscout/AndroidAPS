package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Dana
import info.nightscout.androidaps.interfaces.Diaconn
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandLoadHistory(
    injector: HasAndroidInjector,
    private val type: Byte,
    callback: Callback?
) : Command(injector, CommandType.LOAD_HISTORY, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is Dana) {
            val danaPump = pump as Dana
            val r = danaPump.loadHistory(type)
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
            callback?.result(r)?.run()
        }

        if (pump is Diaconn) {
            val diaconnG8Pump = pump as Diaconn
            val r = diaconnG8Pump.loadHistory()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = "LOAD HISTORY $type"
}