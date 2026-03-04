package app.aaps.plugins.main.general.themes

import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventThemeSwitch
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.UiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Deprecated("Not used in compose")
class ThemeSwitcherPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val preferences: Preferences,
    private val rxBus: RxBus,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(app.aaps.core.keys.R.string.theme_switcher),
    aapsLogger, rh
) {

    private var scope: CoroutineScope? = null

    override fun onStart() {
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        preferences.observe(StringKey.GeneralDarkMode).drop(1).onEach {
            withContext(Dispatchers.Main) {
                setThemeMode()
            }
            rxBus.send(EventThemeSwitch())
        }.launchIn(newScope)
    }

    fun setThemeMode() {
        val mode = try {
            when (UiMode.fromString(preferences.get(StringKey.GeneralDarkMode))) {
                UiMode.DARK   -> MODE_NIGHT_YES
                UiMode.LIGHT  -> MODE_NIGHT_NO
                UiMode.SYSTEM -> MODE_NIGHT_FOLLOW_SYSTEM
            }
        } catch (_: Exception) {
            MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
    }
}
