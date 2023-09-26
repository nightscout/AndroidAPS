package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.extensions.friendlyDescription
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputTempTarget
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.triggers.TriggerTempTarget
import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ActionStartTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var profileUtil: ProfileUtil

    private val disposable = CompositeDisposable()

    var value = InputTempTarget(profileFunction)
    var duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    init {
        precondition = TriggerTempTarget(injector, ComparatorExists.Compare.NOT_EXISTS)
    }

    override fun friendlyName(): Int = R.string.starttemptarget
    override fun shortDescription(): String = rh.gs(R.string.starttemptarget) + ": " + tt().friendlyDescription(value.units, rh, profileUtil)
    @DrawableRes override fun icon(): Int = app.aaps.core.main.R.drawable.ic_temptarget_high

    override fun doAction(callback: Callback) {
        disposable += repository.runTransactionForResult(InsertAndCancelCurrentTemporaryTargetTransaction(tt()))
            .subscribe({ result ->
                           result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                           uel.log(
                               UserEntry.Action.TT, Sources.Automation, title,
                               ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.AUTOMATION),
                               ValueWithUnit.Mgdl(tt().lowTarget),
                               ValueWithUnit.Mgdl(tt().highTarget).takeIf { tt().lowTarget != tt().highTarget },
                               ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt().duration).toInt())
                           )
                           callback.result(PumpEnactResult(injector).success(true).comment(app.aaps.core.ui.R.string.ok)).run()
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                           callback.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.error)).run()
                       })
    }

    override fun generateDialog(root: LinearLayout) {
        val unitResId = if (value.units == GlucoseUnit.MGDL) app.aaps.core.ui.R.string.mgdl else app.aaps.core.ui.R.string.mmol
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.temporary_target) + "\n[" + rh.gs(unitResId) + "]", "", value))
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.duration_min_label), "", duration))
            .build(root)
    }

    override fun hasDialog(): Boolean {
        return true
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("value", value.value)
            .put("units", value.units.asText)
            .put("durationInMinutes", duration.getMinutes())
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        value.units = GlucoseUnit.fromText(JsonHelper.safeGetString(o, "units", GlucoseUnit.MGDL.asText))
        value.value = safeGetDouble(o, "value")
        duration.setMinutes(JsonHelper.safeGetInt(o, "durationInMinutes"))
        return this
    }

    private fun tt() = TemporaryTarget(
        timestamp = dateUtil.now(),
        duration = TimeUnit.MINUTES.toMillis(duration.getMinutes().toLong()),
        reason = TemporaryTarget.Reason.AUTOMATION,
        lowTarget = profileUtil.convertToMgdl(value.value, value.units),
        highTarget = profileUtil.convertToMgdl(value.value, value.units)
    )

    override fun isValid(): Boolean =
        if (value.units == GlucoseUnit.MMOL) { // mmol
            value.value >= Constants.MIN_TT_MMOL &&
                value.value <= Constants.MAX_TT_MMOL &&
                duration.value > 0
        } else { // mg/dL
            value.value >= Constants.MIN_TT_MGDL &&
                value.value <= Constants.MAX_TT_MGDL &&
                duration.value > 0
        }
}