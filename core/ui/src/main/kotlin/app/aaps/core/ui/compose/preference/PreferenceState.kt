/*
 * Preference State Support for Jetpack Compose
 * Provides state classes and visibility calculation for preferences
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.LocalRxBus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Data class holding visibility and enabled state for a preference
 */
data class PreferenceVisibilityState(
    val visible: Boolean,
    val enabled: Boolean
)

/**
 * Wrapper around PreferenceVisibilityContext that reads IntKeys through
 * the shared state map, making visibility checks reactive.
 */
private class ReactiveVisibilityContext(
    private val delegate: PreferenceVisibilityContext,
    private val delegatePreferences: Preferences
) : PreferenceVisibilityContext {

    override val isPatchPump: Boolean get() = delegate.isPatchPump
    override val isBatteryReplaceable: Boolean get() = delegate.isBatteryReplaceable
    override val isBatteryChangeLoggingEnabled: Boolean get() = delegate.isBatteryChangeLoggingEnabled
    override val advancedFilteringSupported: Boolean get() = delegate.advancedFilteringSupported
    override val isPumpPaired: Boolean get() = delegate.isPumpPaired
    override val isPumpInitialized: Boolean get() = delegate.isPumpInitialized

    // Return a reactive preferences wrapper
    override val preferences: Preferences get() = ReactivePreferencesWrapper(delegatePreferences)

    /**
     * Wrapper that reads keys through the shared state map for reactivity.
     */
    private class ReactivePreferencesWrapper(
        private val delegate: Preferences
    ) : Preferences by delegate {

        override fun get(key: IntPreferenceKey): Int {
            // Read through shared state for reactivity
            return getSharedIntState(key.key, delegate.get(key))
        }

        override fun get(key: StringPreferenceKey): String {
            // Read through shared state for reactivity
            return getSharedStringState(key.key, delegate.get(key))
        }
    }
}

/**
 * Calculates visibility and enabled state for a preference based on mode settings and dependencies.
 * This is a @Composable function to enable reactive updates when dependency preferences change.
 * Uses reactive state for simpleMode and dependencies so UI recomposes automatically on changes.
 */
@Composable
fun calculatePreferenceVisibility(
    preferenceKey: PreferenceKey,
    engineeringModeOnly: Boolean = false,
    visibilityContext: PreferenceVisibilityContext? = null
): PreferenceVisibilityState {
    val preferences = LocalPreferences.current
    val config = LocalConfig.current
    // Use reactive state for simpleMode - this triggers recomposition when it changes
    val simpleModeState = rememberPreferenceBooleanState(app.aaps.core.keys.BooleanKey.GeneralSimpleMode)

    // Use reactive state for dependency checks
    val dependencyState = preferenceKey.dependency?.let {
        rememberPreferenceBooleanState(it)
    }
    val negativeDependencyState = preferenceKey.negativeDependency?.let {
        rememberPreferenceBooleanState(it)
    }

    var visible = true
    var enabled = true

    // Check simple mode - now reactive
    if (simpleModeState.value && preferenceKey.defaultedBySM) {
        visible = false
    }

    // Check APS mode
    if (preferences.apsMode && !preferenceKey.showInApsMode) {
        visible = false
        enabled = false
    }

    // Check NSClient mode
    if (preferences.nsclientMode && !preferenceKey.showInNsClientMode) {
        visible = false
        enabled = false
    }

    // Check PumpControl mode
    if (preferences.pumpControlMode && !preferenceKey.showInPumpControlMode) {
        visible = false
        enabled = false
    }

    // Check engineering mode
    if (!config.isEngineeringMode() && engineeringModeOnly) {
        visible = false
        enabled = false
    }

    // Check dependency - now reactive
    dependencyState?.let {
        if (!it.value) {
            visible = false
        }
    }

    // Check negative dependency - now reactive
    negativeDependencyState?.let {
        if (it.value) {
            visible = false
        }
    }

    // Check runtime visibility condition
    // For reactive visibility, we need to read dependent IntKeys through shared state
    visibilityContext?.let { context ->
        // Create a reactive visibility context that reads through shared state
        val reactiveContext = ReactiveVisibilityContext(context, preferences)
        if (!preferenceKey.visibility.isVisible(reactiveContext)) {
            visible = false
        }
        // Check runtime enabled condition
        if (!preferenceKey.enabledCondition.isEnabled(reactiveContext)) {
            enabled = false
        }
    }

    return PreferenceVisibilityState(visible, enabled)
}

/**
 * Calculates visibility and enabled state for an intent preference based on mode settings and dependencies.
 * This is a @Composable function to enable reactive updates when dependency preferences change.
 * Uses reactive state for simpleMode and dependencies so UI recomposes automatically on changes.
 */
@Composable
fun calculateIntentPreferenceVisibility(
    intentKey: IntentPreferenceKey,
    visibilityContext: PreferenceVisibilityContext? = null
): PreferenceVisibilityState {
    val preferences = LocalPreferences.current
    // Use reactive state for simpleMode - this triggers recomposition when it changes
    val simpleModeState = rememberPreferenceBooleanState(app.aaps.core.keys.BooleanKey.GeneralSimpleMode)

    // Use reactive state for dependency checks
    val dependencyState = intentKey.dependency?.let {
        rememberPreferenceBooleanState(it)
    }
    val negativeDependencyState = intentKey.negativeDependency?.let {
        rememberPreferenceBooleanState(it)
    }

    var visible = true
    var enabled = true

    // Check simple mode - now reactive
    if (simpleModeState.value && intentKey.defaultedBySM) {
        visible = false
    }

    // Check APS mode
    if (preferences.apsMode && !intentKey.showInApsMode) {
        visible = false
        enabled = false
    }

    // Check NSClient mode
    if (preferences.nsclientMode && !intentKey.showInNsClientMode) {
        visible = false
        enabled = false
    }

    // Check PumpControl mode
    if (preferences.pumpControlMode && !intentKey.showInPumpControlMode) {
        visible = false
        enabled = false
    }

    // Check dependency - now reactive
    dependencyState?.let {
        if (!it.value) {
            visible = false
        }
    }

    // Check negative dependency - now reactive
    negativeDependencyState?.let {
        if (it.value) {
            visible = false
        }
    }

    // Check runtime visibility condition
    // For reactive visibility, we need to read dependent IntKeys through shared state
    visibilityContext?.let { context ->
        // Create a reactive visibility context that reads through shared state
        val reactiveContext = ReactiveVisibilityContext(context, preferences)
        if (!intentKey.visibility.isVisible(reactiveContext)) {
            visible = false
        }
        // Check runtime enabled condition
        if (!intentKey.enabledCondition.isEnabled(reactiveContext)) {
            enabled = false
        }
    }

    return PreferenceVisibilityState(visible, enabled)
}

// =================================
// Global Shared State Registry
// =================================

/**
 * Global registry for preference states. Ensures all components observing the same
 * preference key share the same state, enabling proper reactivity across the UI.
 *
 * Key format: "type:key" (e.g., "string:sms_allowed_numbers", "boolean:use_smb")
 */
private val sharedPreferenceStates = mutableStateMapOf<String, Any?>()

private fun getSharedBooleanState(key: String, initialValue: Boolean): Boolean {
    return sharedPreferenceStates.getOrPut("boolean:$key") { initialValue } as Boolean
}

private fun setSharedBooleanState(key: String, value: Boolean) {
    sharedPreferenceStates["boolean:$key"] = value
}

private fun getSharedStringState(key: String, initialValue: String): String {
    return sharedPreferenceStates.getOrPut("string:$key") { initialValue } as String
}

private fun setSharedStringState(key: String, value: String) {
    sharedPreferenceStates["string:$key"] = value
}

private fun getSharedIntState(key: String, initialValue: Int): Int {
    return sharedPreferenceStates.getOrPut("int:$key") { initialValue } as Int
}

private fun setSharedIntState(key: String, value: Int) {
    sharedPreferenceStates["int:$key"] = value
}

private fun getSharedDoubleState(key: String, initialValue: Double): Double {
    return sharedPreferenceStates.getOrPut("double:$key") { initialValue } as Double
}

private fun setSharedDoubleState(key: String, value: Double) {
    sharedPreferenceStates["double:$key"] = value
}

// =================================
// Remember State Functions
// =================================

/**
 * Remembers a MutableState for a BooleanPreferenceKey backed by Preferences.
 */
@Composable
fun rememberPreferenceBooleanState(
    key: BooleanPreferenceKey
): MutableState<Boolean> {
    val preferences = LocalPreferences.current
    val rxBus = LocalRxBus.current
    return remember(key, preferences) {
        PreferenceBooleanState(preferences, key, rxBus)
    }
}

/**
 * Remembers a MutableState for a StringPreferenceKey backed by Preferences.
 */
@Composable
fun rememberPreferenceStringState(
    key: StringPreferenceKey
): MutableState<String> {
    val preferences = LocalPreferences.current
    val rxBus = LocalRxBus.current
    return remember(key, preferences) {
        PreferenceStringState(preferences, key, rxBus)
    }
}

/**
 * Remembers a MutableState for an IntPreferenceKey backed by Preferences.
 */
@Composable
fun rememberPreferenceIntState(
    key: IntPreferenceKey
): MutableState<Int> {
    val preferences = LocalPreferences.current
    val rxBus = LocalRxBus.current
    return remember(key, preferences) {
        PreferenceIntState(preferences, key, rxBus)
    }
}

/**
 * Remembers a MutableState for a DoublePreferenceKey backed by Preferences.
 */
@Composable
fun rememberPreferenceDoubleState(
    key: DoublePreferenceKey
): MutableState<Double> {
    val preferences = LocalPreferences.current
    val rxBus = LocalRxBus.current
    return remember(key, preferences) {
        PreferenceDoubleState(preferences, key, rxBus)
    }
}

// =================================
// State Classes
// =================================

@Stable
internal class PreferenceBooleanState(
    private val preferences: Preferences,
    private val key: BooleanPreferenceKey,
    private val rxBus: RxBus
) : MutableState<Boolean> {

    init {
        // Initialize shared state if not present
        getSharedBooleanState(key.key, preferences.get(key))
    }

    override var value: Boolean
        get() = getSharedBooleanState(key.key, preferences.get(key))
        set(value) {
            setSharedBooleanState(key.key, value)
            preferences.put(key, value)
            rxBus.send(EventPreferenceChange(key.key))
        }

    override fun component1(): Boolean = value
    override fun component2(): (Boolean) -> Unit = { value = it }
}

@Stable
internal class PreferenceStringState(
    private val preferences: Preferences,
    private val key: StringPreferenceKey,
    private val rxBus: RxBus
) : MutableState<String> {

    init {
        // Initialize shared state if not present
        getSharedStringState(key.key, preferences.get(key))
    }

    override var value: String
        get() = getSharedStringState(key.key, preferences.get(key))
        set(value) {
            setSharedStringState(key.key, value)
            preferences.put(key, value)
            rxBus.send(EventPreferenceChange(key.key))
        }

    override fun component1(): String = value
    override fun component2(): (String) -> Unit = { value = it }
}

@Stable
internal class PreferenceIntState(
    private val preferences: Preferences,
    private val key: IntPreferenceKey,
    private val rxBus: RxBus
) : MutableState<Int> {

    init {
        // Initialize shared state if not present
        getSharedIntState(key.key, preferences.get(key))
    }

    override var value: Int
        get() = getSharedIntState(key.key, preferences.get(key))
        set(value) {
            // Clamp to min/max
            val clampedValue = value.coerceIn(key.min, key.max)
            setSharedIntState(key.key, clampedValue)
            preferences.put(key, clampedValue)
            rxBus.send(EventPreferenceChange(key.key))
        }

    override fun component1(): Int = value
    override fun component2(): (Int) -> Unit = { value = it }
}

@Stable
internal class PreferenceDoubleState(
    private val preferences: Preferences,
    private val key: DoublePreferenceKey,
    private val rxBus: RxBus
) : MutableState<Double> {

    init {
        // Initialize shared state if not present
        getSharedDoubleState(key.key, preferences.get(key))
    }

    override var value: Double
        get() = getSharedDoubleState(key.key, preferences.get(key))
        set(value) {
            // Clamp to min/max
            val clampedValue = value.coerceIn(key.min, key.max)
            setSharedDoubleState(key.key, clampedValue)
            preferences.put(key, clampedValue)
            rxBus.send(EventPreferenceChange(key.key))
        }

    override fun component1(): Double = value
    override fun component2(): (Double) -> Unit = { value = it }
}

// =================================
// Unit Double State
// =================================

/**
 * State wrapper for unit double preferences that handles unit conversion.
 */
@Stable
class UnitDoublePreferenceState(
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val key: UnitDoublePreferenceKey,
    private val _displayValue: MutableState<String>,
    private val rxBus: RxBus
) {

    val displayValue: String
        get() = _displayValue.value

    fun updateDisplayValue(newValue: String) {
        _displayValue.value = newValue
        // Convert from display units back to mg/dL for storage
        val displayDouble = newValue.toDoubleOrNull() ?: return
        val mgdlValue = profileUtil.convertToMgdlDetect(displayDouble)
        preferences.put(key, mgdlValue)
        rxBus.send(EventPreferenceChange(key.key))
    }
}

@Composable
fun rememberUnitDoublePreferenceState(
    key: UnitDoublePreferenceKey
): UnitDoublePreferenceState {
    val preferences = LocalPreferences.current
    val profileUtil = LocalProfileUtil.current
    val rxBus = LocalRxBus.current
    // Get stored value (in mg/dL) and convert to display units
    val storedValue = preferences.get(key)
    val displayValue = profileUtil.valueInCurrentUnitsDetect(storedValue)
    // Check if using mg/dL by comparing converted values (mg/dL stays the same, mmol/L gets divided)
    val isMgdl = displayValue == storedValue || (storedValue > 0 && displayValue / storedValue > 0.9)
    val precision = if (isMgdl) 0 else 1
    val formatted = BigDecimal(displayValue).setScale(precision, RoundingMode.HALF_UP).toPlainString()

    val displayState = remember { mutableStateOf(formatted) }

    return remember(key) {
        UnitDoublePreferenceState(preferences, profileUtil, key, displayState, rxBus)
    }
}
