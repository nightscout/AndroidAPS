package app.aaps.core.interfaces.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.PreferenceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Created by mike on 09.06.2016.
 */
abstract class PluginBase(
    val pluginDescription: PluginDescription,
    val aapsLogger: AAPSLogger,
    val rh: ResourceHelper
) {

    protected val pluginScope = CoroutineScope(Dispatchers.Default + Job())

    enum class State {
        NOT_INITIALIZED, ENABLED, DISABLED
    }

    private var state = State.NOT_INITIALIZED

    open val name: String
        get() = pluginDescription.pluginName?.let { rh.gs(it) } ?: "UNKNOWN"

    /**
     * Stable identity for syncing the active-plugin selection (see the `ActivePlugin*` keys). Defaults to
     * the class simple name — matching the legacy `RunningConfiguration` encoding, so dual-write stays
     * consistent. Override to decouple from the class name (survive rename / R8) when a durable id is needed.
     */
    // this::class.simpleName rather than javaClass.simpleName, so this file is not tied to the JVM. It
    // gives the same string: a plugin is always a named class. simpleName is only null for an anonymous
    // one, which would be a bug worth failing on rather than syncing an empty id.
    open val pluginId: String get() = this::class.simpleName!!

    //only if translation exists
    // use long name as fallback
    val nameShort: String
        get() {
            val shortNameRef = pluginDescription.shortName ?: return name
            val translatedName = rh.gs(shortNameRef)
            return if (translatedName.trim { it <= ' ' }.isNotEmpty()) translatedName else name
            // use long name as fallback
        }

    val description: String?
        get() = pluginDescription.description?.let { rh.gs(it) }

    fun getType(): PluginType = pluginDescription.mainType

    open fun isEnabled() = isEnabled(pluginDescription.mainType)

    fun isEnabled(type: PluginType): Boolean {
        if (pluginDescription.alwaysEnabled && type == pluginDescription.mainType) return true
        if (pluginDescription.mainType == PluginType.CONSTRAINTS && type == PluginType.CONSTRAINTS) return true
        if (type == pluginDescription.mainType) return state == State.ENABLED && specialEnableCondition()
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.PUMP && isEnabled(PluginType.PUMP)) return true
        return type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.APS && isEnabled(PluginType.APS)
    }

    fun hasComposeContent(): Boolean {
        return pluginDescription.composeContentProvider != null
    }

    /**
     * Whether this plugin exposes a preferences screen via [getPreferenceScreenContent].
     * Cached after the first call — the existence bit is stable for a plugin instance,
     * while [getPreferenceScreenContent] itself is still invoked on demand when the screen is rendered.
     * Override to `true` eagerly when [getPreferenceScreenContent] does a runtime lookup that might not be
     * resolved at first call (see SensitivityWeightedAveragePlugin).
     */
    open fun hasPreferences(): Boolean = hasPreferencesLazy
    private val hasPreferencesLazy: Boolean by lazy { getPreferenceScreenContent() != null }

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
                    pluginScope.launch { onStart() }
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    pluginScope.launch { onStop() }
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                }
            }
        }
    }

    /**
     * Version of setPluginEnabled used for testing only.
     * OnStart/OnStop is called directly.
     */
    fun setPluginEnabledBlocking(type: PluginType, newState: Boolean) {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    aapsLogger.debug(LTag.CORE, "Starting: $name")
                    runBlocking { onStart() }
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    runBlocking { onStop() }
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                }
            }
        }
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

    open suspend fun onStart() {}
    open suspend fun onStop() {}
    protected open fun onStateChange(type: PluginType?, oldState: State?, newState: State?) {}

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
}