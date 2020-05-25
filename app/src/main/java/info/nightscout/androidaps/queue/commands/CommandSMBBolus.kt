package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import javax.inject.Inject

class CommandSMBBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?
) : Command(injector, CommandType.SMB_BOLUS, callback) {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val r: PumpEnactResult
        val lastBolusTime = activePlugin.activeTreatments.lastBolusTime
        if (lastBolusTime != 0L && lastBolusTime + T.mins(3).msecs() > DateUtil.now()) {
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB requested but still in 3 min interval")
            r = PumpEnactResult(injector).enacted(false).success(false).comment("SMB requested but still in 3 min interval")
        } else if (detailedBolusInfo.deliverAt != 0L && detailedBolusInfo.deliverAt + T.mins(1).msecs() > System.currentTimeMillis()) {
            r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        } else {
            r = PumpEnactResult(injector).enacted(false).success(false).comment("SMB request too old")
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB bolus canceled. deliverAt: " + dateUtil.dateAndTimeString(detailedBolusInfo.deliverAt))
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = "SMB BOLUS ${resourceHelper.gs(R.string.formatinsulinunits, detailedBolusInfo.insulin)}"
}