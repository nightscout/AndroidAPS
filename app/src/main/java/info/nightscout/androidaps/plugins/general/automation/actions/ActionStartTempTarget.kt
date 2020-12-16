package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTempTarget
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.JsonHelper.safeGetDouble
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionStartTempTarget(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider

    var value = InputTempTarget(injector)
    var duration = InputDuration(injector, 0, InputDuration.TimeUnit.MINUTES)

    init {
        precondition = TriggerTempTarget(injector, ComparatorExists.Compare.NOT_EXISTS)
    }

    override fun friendlyName(): Int = R.string.starttemptarget
    override fun shortDescription(): String = resourceHelper.gs(R.string.starttemptarget) + ": " + tt().friendlyDescription(value.units, resourceHelper)
    @DrawableRes override fun icon(): Int = R.drawable.ic_temptarget_high

    override fun doAction(callback: Callback) {
        activePlugin.activeTreatments.addToHistoryTempTarget(tt())
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
    }

    override fun generateDialog(root: LinearLayout) {
        val unitResId = if (value.units == Constants.MGDL) R.string.mgdl else R.string.mmol
        LayoutBuilder()
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.careportal_temporarytarget) + "\n[" + resourceHelper.gs(unitResId) + "]", "", value))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.careportal_newnstreatment_duration_min_label), "", duration))
            .build(root)
    }

    override fun hasDialog(): Boolean {
        return true
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("value", value.value)
            .put("units", value.units)
            .put("durationInMinutes", duration.getMinutes())
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        value.units = JsonHelper.safeGetString(o, "units", Constants.MGDL)
        value.value = safeGetDouble(o, "value")
        duration.setMinutes(JsonHelper.safeGetInt(o, "durationInMinutes"))
        return this
    }

    fun tt(): TempTarget =
        TempTarget()
            .date(DateUtil.now())
            .duration(duration.getMinutes())
            .reason("Automation")
            .source(Source.USER)
            .low(Profile.toMgdl(value.value, value.units))
            .high(Profile.toMgdl(value.value, value.units))
}