package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandExtendedBolus constructor(
    injector: HasAndroidInjector,
    private val insulin: Double,
    private val durationInMinutes: Int,
    callback: Callback?
) : Command(injector, CommandType.EXTENDEDBOLUS, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val r = activePlugin.activePump.setExtendedBolus(insulin, durationInMinutes)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result rate: $insulin durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = "EXTENDEDBOLUS $insulin U $durationInMinutes min"
}