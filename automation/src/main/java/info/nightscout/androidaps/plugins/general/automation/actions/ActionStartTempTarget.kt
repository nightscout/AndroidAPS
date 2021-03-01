package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.InsertTemporaryTargetAndCancelCurrentTransaction
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTempTarget
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.JsonHelper.safeGetDouble
import info.nightscout.androidaps.utils.extensions.friendlyDescription
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ActionStartTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var nsUpload: NSUpload

    private val disposable = CompositeDisposable()

    var value = InputTempTarget(injector)
    var duration = InputDuration(injector, 30, InputDuration.TimeUnit.MINUTES)

    init {
        precondition = TriggerTempTarget(injector, ComparatorExists.Compare.NOT_EXISTS)
    }

    override fun friendlyName(): Int = R.string.starttemptarget
    override fun shortDescription(): String = resourceHelper.gs(R.string.starttemptarget) + ": " + tt().friendlyDescription(value.units, resourceHelper)
    @DrawableRes override fun icon(): Int = R.drawable.ic_temptarget_high

    override fun doAction(callback: Callback) {
        disposable += repository.runTransactionForResult(InsertTemporaryTargetAndCancelCurrentTransaction(tt()))
            .subscribe({ result ->
                result.inserted.forEach { nsUpload.uploadTempTarget(it) }
                result.updated.forEach { nsUpload.updateTempTarget(it) }
                callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
            }, {
                aapsLogger.error(LTag.BGSOURCE, "Error while saving temporary target", it)
                callback.result(PumpEnactResult(injector).success(false).comment(R.string.error))?.run()
            })
    }

    override fun generateDialog(root: LinearLayout) {
        val unitResId = if (value.units == Constants.MGDL) R.string.mgdl else R.string.mmol
        LayoutBuilder()
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.careportal_temporarytarget) + "\n[" + resourceHelper.gs(unitResId) + "]", "", value))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.duration_min_label), "", duration))
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

    fun tt() = TemporaryTarget(
        timestamp = DateUtil.now(),
        duration = TimeUnit.MINUTES.toMillis(duration.getMinutes().toLong()),
        reason = TemporaryTarget.Reason.AUTOMATION,
        lowTarget = Profile.toMgdl(value.value, value.units),
        highTarget = Profile.toMgdl(value.value, value.units)
    )

    override fun isValid(): Boolean =
        if (value.units == Constants.MMOL) { // mmol
            value.value >= Constants.MIN_TT_MMOL &&
                value.value <= Constants.MAX_TT_MMOL &&
                duration.value > 0
        } else { // mg/dL
            value.value >= Constants.MIN_TT_MGDL &&
                value.value <= Constants.MAX_TT_MGDL &&
                duration.value > 0
        }
}