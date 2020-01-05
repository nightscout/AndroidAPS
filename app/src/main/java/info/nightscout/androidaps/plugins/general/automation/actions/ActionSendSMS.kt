package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class ActionSendSMS(mainApp: MainApp) : Action(mainApp) {
    var text = InputString(mainApp)

    override fun friendlyName(): Int = R.string.sendsmsactiondescription
    override fun shortDescription(): String = resourceHelper.gs(R.string.sendsmsactionlabel, text.value)
    override fun icon(): Int = R.drawable.ic_notifications

    override fun doAction(callback: Callback) {
        val result = smsCommunicatorPlugin.sendNotificationToAllNumbers(text.value)
        callback.result(PumpEnactResult().success(result).comment(if (result) R.string.ok else R.string.danar_error))?.run()
    }

    override fun toJSON(): String {
        val data = JSONObject().put("text", text.value)
        return JSONObject()
            .put("type", this.javaClass.name)
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
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.sendsmsactiontext), "", text))
            .build(root)
    }
}