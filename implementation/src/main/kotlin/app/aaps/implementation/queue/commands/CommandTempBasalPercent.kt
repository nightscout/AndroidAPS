package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Provider

class CommandTempBasalPercent(
    injector: HasAndroidInjector,
    private val percent: Int,
    private val durationInMinutes: Int,
    private val enforceNew: Boolean,
    private val profile: Profile,
    private val tbrType: PumpSync.TemporaryBasalType,
    override val callback: Callback?,
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin

    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    init {
        injector.androidInjector().inject(this)
    }

    override val commandType: Command.CommandType = Command.CommandType.TEMPBASAL

    override fun execute() {
        val r =
            if (percent == 100)
                activePlugin.activePump.cancelTempBasal(enforceNew)
            else
                activePlugin.activePump.setTempBasalPercent(percent, durationInMinutes, profile, enforceNew, tbrType)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result percent: $percent durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.temp_basal_percent, percent, durationInMinutes)

    override fun log(): String = "TEMP BASAL $percent% $durationInMinutes min"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}