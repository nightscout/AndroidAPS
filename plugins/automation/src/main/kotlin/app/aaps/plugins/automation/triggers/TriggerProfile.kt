package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputProfileName
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.rx.logging.LTag
import org.json.JSONObject

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

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.interfaces.R.drawable.ic_actions_profileswitch)

    override fun duplicate(): Trigger = TriggerProfile(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.profilecheck, this))
            .add(LabelWithElement(rh, "Name:", "", profileName))
            .build(root)
    }
}