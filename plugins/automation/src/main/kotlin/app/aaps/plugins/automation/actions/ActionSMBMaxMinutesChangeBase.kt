package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.elements.InputDuration
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

/**
 * Base for actions that write a "max minutes of basal to limit SMB" preference.
 * When the automation fires, the new value is stored in [key].
 */
abstract class ActionSMBMaxMinutesChangeBase(
    injector: HasAndroidInjector,
    val key: IntPreferenceKey,
    private val friendlyNameRes: Int,
    private val shortDescriptionRes: Int
) : Action(injector) {

    @Inject lateinit var preferences: Preferences

    val minutes = InputDuration(key.defaultValue, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = friendlyNameRes
    override fun shortDescription(): String = rh.gs(shortDescriptionRes, minutes.value)
    override fun composeIcon() = IcSmb
    override fun elementType() = ElementType.INSULIN

    override suspend fun doAction(): PumpEnactResult {
        preferences.put(key, minutes.value.coerceIn(key.min, key.max))
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("minutes", minutes.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        minutes.value = JsonHelper.safeGetInt(o, "minutes", key.defaultValue)
        return this
    }

    override fun isValid(): Boolean = minutes.value in key.min..key.max
}
