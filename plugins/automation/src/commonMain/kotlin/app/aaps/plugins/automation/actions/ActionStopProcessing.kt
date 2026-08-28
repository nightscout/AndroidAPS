package app.aaps.plugins.automation.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import dev.zacsweers.metro.Provider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionStopProcessing(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: Provider<PumpEnactResult>
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    override fun friendlyName(): TextRef = AutomationStrings.stop_processing
    override fun shortDescription(): String = rh.gs(AutomationStrings.stop_processing)
    override fun composeIcon() = Icons.Filled.Stop
    override fun elementType() = ElementType.BG_CHECK

    override fun isValid(): Boolean = true

    override suspend fun doAction(): PumpEnactResult {
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun toJSON(): String {
        return buildJsonObject {
            put("type", this@ActionStopProcessing::class.simpleName)
            put("data", buildJsonObject { })
        }.toString()
    }

    override fun fromJSON(data: String): Action = this

    override fun hasDialog(): Boolean = false
}