package app.aaps.plugins.automation.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.ui.compose.icons.IcPluginSms
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSendSMS(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val smsCommunicator: SmsCommunicator
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var text = InputString()

    override fun friendlyName(): TextRef = AutomationStrings.sendsmsactiondescription
    override fun shortDescription(): String = rh.gs(AutomationStrings.sendsmsactionlabel, text.value)
    override fun composeIcon() = IcPluginSms
    override fun elementType() = ElementType.AUTOMATION

    override suspend fun doAction(): PumpEnactResult {
        val result = smsCommunicator.sendNotificationToAllNumbers(text.value)
        return pumpEnactResultProvider().success(result).comment(if (result) CoreUiStrings.ok else CoreUiStrings.error)
    }

    override fun isValid(): Boolean = text.value.isNotEmpty()

    override fun toJSON(): String {
        val data = buildJsonObject { put("text", text.value) }
        return buildJsonObject {
            put("type", this@ActionSendSMS::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        text.value = o.lenientString("text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

}