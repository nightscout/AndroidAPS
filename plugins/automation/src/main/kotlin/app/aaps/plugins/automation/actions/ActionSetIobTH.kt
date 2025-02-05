package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.elements.InputIobTH
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.R
import org.json.JSONObject
import javax.inject.Inject

class ActionSetIobTH(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var sp: SP

    var new_iobTH = InputIobTH ( )
    // new_weight.value = 1

    override fun friendlyName(): Int = R.string.autoisf_iobTH_percent
    override fun shortDescription(): String = rh.gs(R.string.automate_set_iobTH_percent, new_iobTH.value.toInt())
    @DrawableRes override fun icon(): Int = R.drawable.ic_iobth

    override fun doAction(callback: Callback) {
        val currentIobTH:Int = sp.getInt(R.string.iob_threshold_percent, 100)
        if (currentIobTH != new_iobTH.value) {
            uel.log(
                app.aaps.core.data.ue.Action.IOB_TH_SET,
                Sources.Automation,
                title + ": " + rh.gs(R.string.automate_set_iobTH_percent, new_iobTH.value.toInt()),
                ValueWithUnit.Percent(new_iobTH.value.toInt())
            )
            sp.putInt(R.string.iob_threshold_percent, new_iobTH.value)
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.weight_new)).run()
        } else {
            callback.result(instantiator.providePumpEnactResult().success(false).comment(R.string.weight_old)).run()
        }
    }

    override fun hasDialog(): Boolean {
        return true
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.autoisf_iobTH_percent), "%", new_iobTH))
            .build(root)
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("percentage", new_iobTH.value)
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        new_iobTH.value = JsonHelper.safeGetInt(o, "percentage")
        return this
    }

    override fun isValid(): Boolean = true
}