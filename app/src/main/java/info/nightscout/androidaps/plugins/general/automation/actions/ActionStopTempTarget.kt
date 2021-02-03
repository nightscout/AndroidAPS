package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider

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
        activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
    }

    override fun isValid(): Boolean = true
}