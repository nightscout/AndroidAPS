package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.DanaRInterface
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandLoadHistory(
    injector: HasAndroidInjector,
    private val type: Byte,
    callback: Callback?
) : Command(injector, CommandType.LOAD_HISTORY, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is DanaRInterface) {
            val danaPump = pump as DanaRInterface
            val r = danaPump.loadHistory(type)
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = "LOAD HISTORY $type"
}