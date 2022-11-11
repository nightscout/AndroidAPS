package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResultObject
import info.nightscout.implementation.R
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandExtendedBolus constructor(
    injector: HasAndroidInjector,
    private val insulin: Double,
    private val durationInMinutes: Int,
    callback: Callback?
) : Command(injector, CommandType.EXTENDEDBOLUS, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r = activePlugin.activePump.setExtendedBolus(insulin, durationInMinutes)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result rate: $insulin durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(R.string.extended_bolus_u_min, insulin, durationInMinutes)

    override fun log(): String = "EXTENDEDBOLUS $insulin U $durationInMinutes min"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResultObject(injector).success(false).comment(info.nightscout.core.main.R.string.connectiontimedout))?.run()
    }
}