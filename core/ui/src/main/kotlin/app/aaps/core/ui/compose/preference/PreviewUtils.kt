package app.aaps.core.ui.compose.preference

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps content in MaterialTheme + ProvidePreferenceTheme + fake Preferences for @Preview functions.
 * Only for use in previews — not for production code.
 */
@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        ProvidePreferenceTheme {
            CompositionLocalProvider(
                LocalPreferences provides PreviewPreferences,
                LocalConfig provides PreviewConfig
            ) {
                content()
            }
        }
    }
}

/**
 * Minimal Preferences implementation that returns default values from keys.
 * Only for use in Compose @Preview functions.
 */
private object PreviewPreferences : Preferences {

    override val simpleMode: Boolean = false
    override val apsMode: Boolean = true
    override val nsclientMode: Boolean = false
    override val pumpControlMode: Boolean = false

    // Return false for GeneralSimpleMode so defaultedBySM preferences are visible in previews
    private fun booleanDefault(key: BooleanNonPreferenceKey): Boolean =
        if (key.key == "simple_mode") false else key.defaultValue

    override fun get(key: BooleanNonPreferenceKey): Boolean = booleanDefault(key)
    override fun getIfExists(key: BooleanNonPreferenceKey): Boolean = booleanDefault(key)
    override fun put(key: BooleanNonPreferenceKey, value: Boolean) {}
    override fun observe(key: BooleanNonPreferenceKey): StateFlow<Boolean> = MutableStateFlow(booleanDefault(key))
    override fun get(key: BooleanPreferenceKey): Boolean = booleanDefault(key)
    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean = key.defaultValue
    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, defaultValue: Boolean): Boolean = defaultValue
    override fun getIfExists(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean = key.defaultValue
    override fun put(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, value: Boolean) {}
    override fun observe(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Boolean> = MutableStateFlow(key.defaultValue)
    override fun remove(key: ComposedKey, vararg arguments: Any) {}

    override fun get(key: StringNonPreferenceKey): String = key.defaultValue
    override fun getIfExists(key: StringNonPreferenceKey): String = key.defaultValue
    override fun put(key: StringNonPreferenceKey, value: String) {}
    override fun observe(key: StringNonPreferenceKey): StateFlow<String> = MutableStateFlow(key.defaultValue)
    override fun get(key: StringPreferenceKey): String = key.defaultValue
    override fun get(key: StringComposedNonPreferenceKey, vararg arguments: Any): String = key.defaultValue
    override fun getIfExists(key: StringComposedNonPreferenceKey, vararg arguments: Any): String = key.defaultValue
    override fun put(key: StringComposedNonPreferenceKey, vararg arguments: Any, value: String) {}
    override fun observe(key: StringComposedNonPreferenceKey, vararg arguments: Any): StateFlow<String> = MutableStateFlow(key.defaultValue)

    override fun get(key: DoubleNonPreferenceKey): Double = key.defaultValue
    override fun get(key: DoublePreferenceKey): Double = key.defaultValue
    override fun getIfExists(key: DoublePreferenceKey): Double = key.defaultValue
    override fun put(key: DoubleNonPreferenceKey, value: Double) {}
    override fun observe(key: DoubleNonPreferenceKey): StateFlow<Double> = MutableStateFlow(key.defaultValue)
    override fun get(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double = key.defaultValue
    override fun getIfExists(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double = key.defaultValue
    override fun put(key: DoubleComposedNonPreferenceKey, vararg arguments: Any, value: Double) {}
    override fun observe(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Double> = MutableStateFlow(key.defaultValue)

    override fun get(key: UnitDoublePreferenceKey): Double = key.defaultValue
    override fun getIfExists(key: UnitDoublePreferenceKey): Double = key.defaultValue
    override fun put(key: UnitDoublePreferenceKey, value: Double) {}
    override fun observe(key: UnitDoublePreferenceKey): StateFlow<Double> = MutableStateFlow(key.defaultValue)

    override fun get(key: IntNonPreferenceKey): Int = key.defaultValue
    override fun getIfExists(key: IntNonPreferenceKey): Int = key.defaultValue
    override fun put(key: IntComposedNonPreferenceKey, vararg arguments: Any, value: Int) {}
    override fun put(key: IntNonPreferenceKey, value: Int) {}
    override fun observe(key: IntNonPreferenceKey): StateFlow<Int> = MutableStateFlow(key.defaultValue)
    override fun inc(key: IntNonPreferenceKey) {}
    override fun get(key: IntComposedNonPreferenceKey, vararg arguments: Any): Int = key.defaultValue
    override fun observe(key: IntComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Int> = MutableStateFlow(key.defaultValue)
    override fun get(key: IntPreferenceKey): Int = key.defaultValue

    override fun get(key: LongNonPreferenceKey): Long = key.defaultValue
    override fun getIfExists(key: LongNonPreferenceKey): Long = key.defaultValue
    override fun put(key: LongNonPreferenceKey, value: Long) {}
    override fun observe(key: LongNonPreferenceKey): StateFlow<Long> = MutableStateFlow(key.defaultValue)
    override fun get(key: LongPreferenceKey): Long = key.defaultValue
    override fun inc(key: LongNonPreferenceKey) {}
    override fun get(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long = key.defaultValue
    override fun getIfExists(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long = key.defaultValue
    override fun put(key: LongComposedNonPreferenceKey, vararg arguments: Any, value: Long) {}
    override fun observe(key: LongComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Long> = MutableStateFlow(key.defaultValue)

    override fun remove(key: NonPreferenceKey) {}
    override fun isUnitDependent(key: String): Boolean = false
    override fun get(key: String): NonPreferenceKey? = null
    override fun getIfExists(key: String): NonPreferenceKey? = null
    override fun getDependingOn(key: String): List<PreferenceKey> = emptyList()
    override fun registerPreferences(clazz: Class<out NonPreferenceKey>) {}
    override fun allMatchingStrings(key: ComposedKey): List<String> = emptyList()
    override fun allMatchingInts(key: ComposedKey): List<Int> = emptyList()
    override fun isExportableKey(key: String): Boolean = false
    override fun getAllPreferenceKeys(): List<PreferenceKey> = emptyList()
}

/**
 * Minimal Config implementation for Compose @Preview functions.
 */
private object PreviewConfig : Config {

    override val SUPPORTED_NS_VERSION: Int = 0
    override val APS: Boolean = true
    override val AAPSCLIENT: Boolean = false
    override val AAPSCLIENT1: Boolean = false
    override val AAPSCLIENT2: Boolean = false
    override val AAPSCLIENT3: Boolean = false
    override val PUMPCONTROL: Boolean = false
    override val PUMPDRIVERS: Boolean = true
    override val FLAVOR: String = "full"
    override val VERSION_NAME: String = "preview"
    override val HEAD: String = ""
    override val COMMITTED: Boolean = true
    override val BUILD_VERSION: String = "0"
    override val REMOTE: String = ""
    override val BUILD_TYPE: String = "debug"
    override val VERSION: String = "0.0.0"
    override val APPLICATION_ID: String = "preview"
    override val DEBUG: Boolean = true
    override val currentDeviceModelString: String = "Preview"
    override val appName: Int = 0
    override val initProgressFlow: StateFlow<InitProgress> = MutableStateFlow(InitProgress(done = true))
    override val initSnackbarFlow: SharedFlow<String> = MutableSharedFlow()
    override fun updateInitProgress(step: String, current: Int, total: Int) {}
    override fun initCompleted() {}
    override fun initFailed(error: String) {}
    override fun showInitSnackbar(message: String) {}

    override fun isDev(): Boolean = false
    override fun isEngineeringModeOrRelease(): Boolean = true
    override fun isEngineeringMode(): Boolean = false
    override fun isEnabled(option: ExternalOptions): Boolean = false
}

