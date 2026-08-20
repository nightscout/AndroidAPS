package app.aaps.plugins.automation.actions

import javax.inject.Provider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.ui.compose.icons.IcPluginSms
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import org.json.JSONObject

class ActionSendSMS(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val smsCommunicator: SmsCommunicator
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var text = InputString()

    override fun friendlyName(): Int = R.string.sendsmsactiondescription
    override fun shortDescription(): String = rh.gs(R.string.sendsmsactionlabel, text.value)
    override fun composeIcon() = IcPluginSms
    override fun elementType() = ElementType.AUTOMATION

    override suspend fun doAction(): PumpEnactResult {
        val result = smsCommunicator.sendNotificationToAllNumbers(text.value)
        return pumpEnactResultProvider.get().success(result).comment(if (result) app.aaps.core.ui.R.string.ok else app.aaps.core.ui.R.string.error)
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