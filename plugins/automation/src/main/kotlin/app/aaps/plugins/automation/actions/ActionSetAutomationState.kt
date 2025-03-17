package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.services.AutomationStateService
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSetAutomationState(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var automationState: AutomationStateService

    private var inputStateName = InputString()
    private var inputState = InputString()

    override fun friendlyName(): Int = R.string.set_state

    //TODO: make this display the value of state
    override fun shortDescription(): String = rh.gs(R.string.set_state_description, inputStateName.value, inputState.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_reorder_gray_24dp
    override fun isValid(): Boolean = true // empty alarm will show app name

    override fun doAction(callback: Callback) {
        automationState.setState(inputStateName.value, inputState.value)
        callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
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