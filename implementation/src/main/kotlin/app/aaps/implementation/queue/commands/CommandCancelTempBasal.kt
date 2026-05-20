package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import javax.inject.Provider

class CommandCancelTempBasal(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val enforceNew: Boolean,
    /** true if called by detection of pump in suspend mode */
    private val autoForced: Boolean,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.TEMPBASAL

    override suspend fun execute(): PumpEnactResult {
        val r = activePlugin.activePump.cancelTempBasal(enforceNew)
        /*
            If this command is auto-forced, it means pump is in suspended mode
            and may not be able to accept temp basal.
            In this case ignore error reported by pump and
            cancel tbr anyway
         */
        if (autoForced && !r.success) {
            if (pumpSync.expectedPumpState().temporaryBasal != null) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    activePlugin.activePump.pumpDescription.pumpType,
                    activePlugin.activePump.serialNumber(),
                    ignorePumpIds = true
                )
                aapsLogger.debug(LTag.PUMPQUEUE, "Stopping TBR from suspended pump (auto-forced)")
            }
            r.success(true).enacted(false)
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        return r
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.uel_accepts_temp_basal)

    override fun log(): String = "CANCEL TEMPBASAL"
}
