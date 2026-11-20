package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.utils.lenientInt
import app.aaps.plugins.automation.elements.InputDuration
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Base for actions that write a "max minutes of basal to limit SMB" preference.
 * When the automation fires, the new value is stored in [key].
 */
abstract class ActionSMBMaxMinutesChangeBase(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val preferences: Preferences,
    val key: IntPreferenceKey,
    private val friendlyNameRef: TextRef,
    private val shortDescriptionRef: TextRef
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    val minutes = InputDuration(key.defaultValue, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): TextRef = friendlyNameRef
    override fun shortDescription(): String = rh.gs(shortDescriptionRef, minutes.value)
    override fun composeIcon() = IcSmb
    override fun elementType() = ElementType.INSULIN

    override suspend fun doAction(): PumpEnactResult {
        preferences.put(key, minutes.value.coerceIn(key.min, key.max))
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject { put("minutes", minutes.value) }
        return buildJsonObject {
            put("type", this@ActionSMBMaxMinutesChangeBase::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        minutes.value = o.lenientInt("minutes", key.defaultValue)
        return this
    }

    override fun isValid(): Boolean = minutes.value in key.min..key.max
}
