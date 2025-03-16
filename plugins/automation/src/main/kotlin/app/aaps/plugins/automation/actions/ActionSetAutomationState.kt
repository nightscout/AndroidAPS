package info.nightscout.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.services.AutomationStateService
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.utils.JsonHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionSetAutomationState(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var automationState: AutomationStateService
    @Inject lateinit var uel: UserEntryLogger

    private var inputStateName = InputString()
    private var inputState = InputString()

    override fun friendlyName(): Int = R.string.set_state

    //TODO: make this display the value of state
    override fun shortDescription(): String = rh.gs(R.string.set_state_description, inputStateName.value, inputState.value)
    @DrawableRes override fun icon(): Int = info.nightscout.core.ui.R.drawable.ic_reorder_gray_24dp
    override fun isValid(): Boolean = true // empty alarm will show app name

    override fun doAction(callback: Callback) {
        automationState.setState(inputStateName.value, inputState.value)
        callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
    }

    override fun toJSON(): String {
        val data = JSONObject().put("inputStateName", inputStateName.value).put("inputState", inputState.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        inputState.value = JsonHelper.safeGetString(o, "inputState", "")
        inputStateName.value = JsonHelper.safeGetString(o, "inputStateName", "")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.set_state_state_name), "", inputStateName))
            .add(LabelWithElement(rh, rh.gs(R.string.set_state_state_val), "", inputState))
            .build(root)
    }
}