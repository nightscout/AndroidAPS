package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional
import javax.inject.Inject

class TriggerAutomationState(injector: HasAndroidInjector) : Trigger(injector) {

    @Inject lateinit var automationStateService: AutomationStateInterface

    // Keep these for backwards compatibility with saved automations
    var stateName = InputString()
    var stateValue = InputString()

    private var stateNameDropdown: InputDropdownMenu
    private var stateValueDropdown: InputDropdownMenu

    private constructor(injector: HasAndroidInjector, stateName: String, stateValue: String) : this(injector) {
        injector.androidInjector().inject(this)

        this.stateName.value = stateName
        this.stateValue.value = stateValue
        this.stateNameDropdown.value = stateName
        updateStateValueDropdown(stateName)
        this.stateValueDropdown.value = stateValue
    }

    init {
        injector.androidInjector().inject(this)

        stateNameDropdown = InputDropdownMenu(rh) { stateName ->
            updateStateValueDropdown(stateName)
        }
        stateValueDropdown = InputDropdownMenu(rh)

        // Populate state names dropdown with all available states
        val allStates = automationStateService.getAllStates()
        val stateNames = allStates.map { it.first }.distinct().toMutableList()

        // Add all states that have defined values but may not have a current value
        automationStateService.getAllStates().forEach { (stateName, _) ->
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
            val stateValues = automationStateService.getStateValues(stateName)
            stateValueDropdown.values = stateValues
            stateValueDropdown.updateAdapter()
        } else {
            stateValueDropdown.values = emptyList()
            stateValueDropdown.updateAdapter()
        }
    }

    override fun shouldRun(): Boolean {
        val shouldExecute = automationStateService.inState(stateNameDropdown.value, stateValueDropdown.value)

        if (shouldExecute) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }

        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("stateName", stateNameDropdown.value)
            .put("stateValue", stateValueDropdown.value)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        val stateName = JsonHelper.safeGetString(d, "stateName", "")
        val stateValue = JsonHelper.safeGetString(d, "stateValue", "")

        // For backward compatibility
        this.stateName.value = stateName
        this.stateValue.value = stateValue

        // Set the dropdown values
        stateNameDropdown.value = stateName

        // Make sure we have values loaded for this state
        if (automationStateService.hasStateValues(stateName)) {
            updateStateValueDropdown(stateName)
            stateValueDropdown.value = stateValue
        }
        return this
    }

    override fun friendlyName(): Int = R.string.check_state_name

    override fun friendlyDescription(): String =
        rh.gs(R.string.check_state_description, stateNameDropdown.value, stateValueDropdown.value)

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.ui.R.drawable.ic_reorder_gray_24dp)

    override fun duplicate(): Trigger = TriggerAutomationState(injector, this.stateNameDropdown.value, this.stateValueDropdown.value)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.check_state_name, this))
            .add(LabelWithElement(rh, rh.gs(R.string.state_name_label), "", stateNameDropdown))
            .add(LabelWithElement(rh, rh.gs(R.string.state_value_label), "", stateValueDropdown))
            .build(root)
    }
}