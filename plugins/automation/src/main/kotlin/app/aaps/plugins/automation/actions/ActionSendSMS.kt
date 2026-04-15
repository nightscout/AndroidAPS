package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.ui.compose.icons.IcPluginSms
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputString
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSendSMS(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var smsCommunicator: SmsCommunicator

    var text = InputString()

    override fun friendlyName(): Int = R.string.sendsmsactiondescription
    override fun shortDescription(): String = rh.gs(R.string.sendsmsactionlabel, text.value)
    override fun composeIcon() = IcPluginSms
    override fun composeIconTint() = IconTint.Sms

    override suspend fun doAction(callback: Callback) {
        val result = smsCommunicator.sendNotificationToAllNumbers(text.value)
        callback.result(pumpEnactResultProvider.get().success(result).comment(if (result) app.aaps.core.ui.R.string.ok else app.aaps.core.ui.R.string.error)).run()
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

}