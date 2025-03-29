package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputProfileName
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional

class TriggerProfile(injector: HasAndroidInjector) : Trigger(injector) {

    var profileName: InputProfileName = InputProfileName(rh, activePlugin, "")

    constructor(injector: HasAndroidInjector, triggerProfilePercent: TriggerProfile) : this(injector) {
        profileName = InputProfileName(rh, activePlugin, triggerProfilePercent.profileName.value)
    }

    fun setValue(value: String): TriggerProfile {
        this.profileName.value = value
        return this
    }

    override fun shouldRun(): Boolean {
        val currentName = profileFunction.getProfileName()

        if (currentName == profileName.value) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }

        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("profileName", profileName.value)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        profileName.value = JsonHelper.safeGetString(d, "profileName", "")
        return this
    }

    override fun friendlyName(): Int = R.string.profilecheck

    override fun friendlyDescription(): String =
        "${rh.gs(R.string.profilecheck)}: ${profileName.value}"

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.ui.R.drawable.ic_actions_profileswitch)

    override fun duplicate(): Trigger = TriggerProfile(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.profilecheck, this))
            .add(LabelWithElement(rh, "Name:", "", profileName))
            .build(root)
    }
}