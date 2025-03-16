package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.rx.logging.LTag
import org.json.JSONObject

class TriggerAutomationState(injector: HasAndroidInjector) : Trigger(injector) {

    var stateName = InputString()
    var stateValue = InputString()

    private constructor(injector: HasAndroidInjector, stateName: String, stateValue: String) : this(injector) {
        this.stateName = InputString(stateName)
        this.stateValue = InputString(stateValue)
    }

    override fun shouldRun(): Boolean {
        val shouldExecute = automationStateService.inState(stateName.value, stateValue.value)

        if (shouldExecute) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }

        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("stateName", stateName.value)
            .put("stateValue", stateValue.value)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        stateName.value = JsonHelper.safeGetString(d, "stateName", "")
        stateValue.value = JsonHelper.safeGetString(d, "stateValue", "")
        return this
    }

    override fun friendlyName(): Int = R.string.check_state_name

    override fun friendlyDescription(): String =
        rh.gs(R.string.check_state_description, stateName.value, stateValue.value)

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.core.ui.R.drawable.ic_reorder_gray_24dp)

    override fun duplicate(): Trigger = TriggerAutomationState(injector, this.stateName.value, this.stateValue.value)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.check_state_name, this))
            .add(LabelWithElement(rh, "State name:", "", stateName))
            .add(LabelWithElement(rh, "State value:", "", stateValue))
            .build(root)
    }
}