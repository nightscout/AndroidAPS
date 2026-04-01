package app.aaps.core.interfaces.plugin

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.PreferenceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/**
 * Created by mike on 09.06.2016.
 */
abstract class PluginBase(
    val pluginDescription: PluginDescription,
    val aapsLogger: AAPSLogger,
    val rh: ResourceHelper
) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    enum class State {
        NOT_INITIALIZED, ENABLED, DISABLED
    }

    private var state = State.NOT_INITIALIZED
    private var fragmentVisible = false

    @Deprecated("use icon")
    open val menuIcon: Int
        get() = pluginDescription.pluginIcon
    @Deprecated("use icon2")
    open val menuIcon2: Int
        get() = pluginDescription.pluginIcon2

    open val name: String
        get() = if (pluginDescription.pluginName == -1) "UNKNOWN" else rh.gs(pluginDescription.pluginName)

    //only if translation exists
    // use long name as fallback
    val nameShort: String
        get() {
            if (pluginDescription.shortName == -1) return name
            val translatedName = rh.gs(pluginDescription.shortName)
            return if (translatedName.trim { it <= ' ' }.isNotEmpty()) translatedName else name
            // use long name as fallback
        }

    val description: String?
        get() = if (pluginDescription.description == -1) null else rh.gs(pluginDescription.description)

    fun getType(): PluginType = pluginDescription.mainType

    open val preferencesId: Int
        get() = pluginDescription.preferencesId

    open fun isEnabled() = isEnabled(pluginDescription.mainType)

    fun isEnabled(type: PluginType): Boolean {
        if (pluginDescription.alwaysEnabled && type == pluginDescription.mainType) return true
        if (pluginDescription.mainType == PluginType.CONSTRAINTS && type == PluginType.CONSTRAINTS) return true
        if (type == pluginDescription.mainType) return state == State.ENABLED && specialEnableCondition()
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.PUMP && isEnabled(PluginType.PUMP)) return true
        return type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.APS && isEnabled(PluginType.APS)
    }

    fun hasFragment(): Boolean {
        return pluginDescription.fragmentClass != null
    }

    fun hasComposeContent(): Boolean {
        return pluginDescription.composeContentProvider != null
    }

    /**
     * Returns the compose content provider for this plugin's main UI.
     *
     * @return ComposablePluginContent instance or null. Typed as Any? to avoid Compose dependency in core:interfaces.
     *         Caller should cast to ComposablePluginContent from core:ui module.
     */
    fun getComposeContent(): Any? {
        return pluginDescription.composeContentProvider?.invoke(this)
    }

    fun isDefault() = pluginDescription.defaultPlugin

    /**
     * So far plugin can have it's main type + ConstraintInterface
     * ConstraintInterface is enabled if main plugin is enabled
     */
    open fun setPluginEnabled(type: PluginType, newState: Boolean) {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    aapsLogger.debug(LTag.CORE, "Starting: $name")
                    scope.launch { onStart() }
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    scope.launch { onStop() }
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                }
            }
        }
    }

    /**
     * Version of setPluginEnabled used for testing only.
     * OnStart/OnStop is called directly.
     */
    @TestOnly
    fun setPluginEnabledBlocking(type: PluginType, newState: Boolean) {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    aapsLogger.debug(LTag.CORE, "Starting: $name")
                    onStart()
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    onStop()
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                }
            }
        }
    }

    open fun setFragmentVisible(type: PluginType, fragmentVisible: Boolean) {
        if (type == pluginDescription.mainType) {
            this.fragmentVisible = fragmentVisible && specialEnableCondition()
        }
    }

    fun isFragmentVisible(): Boolean {
        if (pluginDescription.alwaysVisible) return true
        return if (pluginDescription.neverVisible) false else fragmentVisible
    }

    fun showInList(type: PluginType): Boolean {
        if (pluginDescription.mainType == type) return pluginDescription.showInList.invoke() && specialShowInListCondition()
        return false
    }

    open fun specialEnableCondition(): Boolean {
        return true
    }

    open fun specialShowInListCondition(): Boolean {
        return true
    }

    open fun onStart() {}
    open fun onStop() {}
    protected open fun onStateChange(type: PluginType?, oldState: State?, newState: State?) {}
    open fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {}
    open fun updatePreferenceSummary(pref: Preference) {}

    /**
     * Add [PreferenceScreen] to preferences
     *
     * Plugin can provide either this method or [preferencesId] XML
     */
    open fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {}

    /**
     * Add compose preference screen content
     *
     * Plugin can override this to provide compose-based preference UI using PreferenceSubScreenDef.
     * This provides a declarative, type-safe way to define preference screens.
     *
     * @return PreferenceItem (typically PreferenceSubScreenDef) or null if not implemented
     */
    open fun getPreferenceScreenContent(): PreferenceItem? = null

    /**
     * Runtime permissions this plugin requires.
     * Override in subclasses to declare permissions.
     */
    open fun requiredPermissions(): List<PermissionGroup> = emptyList()

    /**
     * Returns [requiredPermissions] that are not yet granted.
     * Special permission groups are excluded — they need dedicated checks.
     */
    fun missingPermissions(context: Context): List<PermissionGroup> =
        requiredPermissions().filter { group ->
            !group.special && group.permissions.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
        }
}