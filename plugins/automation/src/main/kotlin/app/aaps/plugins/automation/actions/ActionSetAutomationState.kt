package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSetAutomationState(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var automationState: AutomationStateInterface

    private var stateNameDropdown: InputDropdownMenu
    private var stateValueDropdown: InputDropdownMenu

    init {
        injector.androidInjector().inject(this)

        stateNameDropdown = InputDropdownMenu(rh) { stateName ->
            updateStateValueDropdown(stateName)
        }
        stateValueDropdown = InputDropdownMenu(rh)

        // Populate state names dropdown with all available states
        val allStates = automationState.getAllStates()
        val stateNames = allStates.map { it.first }.distinct().toMutableList()

        // Add all states that have defined values but may not have a current value
        automationState.getAllStates().forEach { (stateName, _) ->
            if (!stateNames.contains(stateName)) {
                stateNames.add(stateName)
            }
        }

        if (stateNames.isNotEmpty()) {
            stateNameDropdown.values = stateNames
            stateNameDropdown.updateAdapter()

            // Initialize state values dropdown if we have states
            updateStateValueDropdown(stateNameDropdown.value)
        }
    }

    private fun updateStateValueDropdown(stateName: String) {
        if (stateName.isNotEmpty()) {
            val stateValues = automationState.getStateValues(stateName)
            stateValueDropdown.values = stateValues
            stateValueDropdown.updateAdapter()
        } else {
            stateValueDropdown.values = emptyList()
            stateValueDropdown.updateAdapter()
        }
    }

    override fun friendlyName(): Int = R.string.set_state

    override fun shortDescription(): String = rh.gs(R.string.set_state_description, stateNameDropdown.value, stateValueDropdown.value)

    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_reorder_gray_24dp

    override fun isValid(): Boolean = stateNameDropdown.value.isNotEmpty() && stateValueDropdown.value.isNotEmpty()

    override fun doAction(callback: Callback) {
        automationState.setState(stateNameDropdown.value, stateValueDropdown.value)
        callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("inputStateName", stateNameDropdown.value)
            .put("inputState", stateValueDropdown.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        val stateName = JsonHelper.safeGetString(o, "inputStateName", "")
        val stateValue = JsonHelper.safeGetString(o, "inputState", "")

        stateNameDropdown.value = stateName

        // Make sure we have values loaded for this state
        if (automationState.hasStateValues(stateName)) {
            updateStateValueDropdown(stateName)
            stateValueDropdown.value = stateValue
        }

        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.state_name_label), "", stateNameDropdown))
            .add(LabelWithElement(rh, rh.gs(R.string.state_value_label), "", stateValueDropdown))
            .build(root)
    }
}