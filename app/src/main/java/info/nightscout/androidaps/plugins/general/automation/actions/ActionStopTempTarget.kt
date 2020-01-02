package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil

class ActionStopTempTarget(mainApp: MainApp) : Action(mainApp) {

    override fun friendlyName(): Int = R.string.stoptemptarget
    override fun shortDescription(): String = resourceHelper.gs(R.string.stoptemptarget)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        val tempTarget = TempTarget()
            .date(DateUtil.now())
            .duration(0)
            .reason("Automation")
            .source(Source.USER)
            .low(0.0).high(0.0)
        treatmentsPlugin.addToHistoryTempTarget(tempTarget)
        callback.result(PumpEnactResult().success(true).comment(R.string.ok))?.run()
    }
}