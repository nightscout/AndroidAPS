package info.nightscout.automation.actions

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.interfaces.utils.JsonHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionSendSMS(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var smsCommunicator: SmsCommunicator

    var text = InputString()

    override fun friendlyName(): Int = R.string.sendsmsactiondescription
    override fun shortDescription(): String = rh.gs(R.string.sendsmsactionlabel, text.value)
    override fun icon(): Int = R.drawable.ic_notifications

    override fun doAction(callback: Callback) {
        val result = smsCommunicator.sendNotificationToAllNumbers(text.value)
        callback.result(PumpEnactResult(injector).success(result).comment(if (result) info.nightscout.core.ui.R.string.ok else info.nightscout.core.ui.R.string.error)).run()
    }

    override fun isValid(): Boolean = text.value.isNotEmpty()

    override fun toJSON(): String {
        val data = JSONObject().put("text", text.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        text.value = JsonHelper.safeGetString(o, "text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.sendsmsactiontext), "", text))
            .build(root)
    }
}