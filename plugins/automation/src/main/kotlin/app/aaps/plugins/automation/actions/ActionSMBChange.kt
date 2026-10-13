package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSMBChange(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var preferences: Preferences

    var smbState: InputDropdownOnOffMenu = InputDropdownOnOffMenu(rh, true)

    override fun friendlyName(): Int = R.string.changeSmbState
    override fun shortDescription(): String = rh.gs(R.string.changeSmbTo, smbState.toTextValue())
    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_running_mode

    override fun doAction(callback: Callback) {
        preferences.put(BooleanKey.ApsUseSmb, smbState.value)
        callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.newSmbMode), "", smbState))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("smbState", smbState.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        smbState.value = JsonHelper.safeGetBoolean(o, "smbState", true)
        return this
    }

    override fun isValid(): Boolean = true
}