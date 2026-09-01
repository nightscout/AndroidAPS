package app.aaps.core.interfaces.configuration

import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Represents the current initialization progress of the app.
 * @param step Human-readable description of the current step
 * @param current Current item being processed (for determinate progress)
 * @param total Total items to process (for determinate progress, 0 = indeterminate)
 * @param done True when initialization is complete
 */
data class InitProgress(
    val step: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val done: Boolean = false,
    val error: String? = null
)

enum class ExternalOptions(val filename: String) {
    ENGINEERING_MODE("engineering_mode"),
    UNFINISHED_MODE("unfinished_mode"),
    SHOW_USER_ACTIONS_ON_WATCH_ONLY("show_user_actions_on_watch_only"),
    IGNORE_NS_V3_ERRORS("ignore_nightscout_v3_errors"),
    DO_NOT_SEND_SMS_ON_PROFILE_CHANGE("do_not_send_sms_on_profile_change"),
    ENABLE_AUTOTUNE("enable_autotune"),
    DISABLE_LEAK_CANARY("disable_leakcanary"),
    EMULATE_DANA_RS_V1("emulate_dana_rs_v1"),
    EMULATE_DANA_RS_V3("emulate_dana_rs_v3"),
    EMULATE_DANA_BLE5("emulate_dana_ble5"),
    EMULATE_EQUIL("emulate_equil"),
    EMULATE_DANA_R("emulate_dana_r"),
    EMULATE_DANA_R_KOREAN("emulate_dana_r_korean"),
    EMULATE_DANA_R_V2("emulate_dana_r_v2"),
    ENABLE_OMNIPOD_DRIFT_COMPENSATION("omnipod_drift_compensation"),
}

@Suppress("PropertyName")
interface Config {

    val SUPPORTED_NS_VERSION: Int
    val APS: Boolean
    val AAPSCLIENT: Boolean // aapsclient || aapsclient2 || aapsclient3
    val AAPSCLIENT1: Boolean // aapsclient
    val AAPSCLIENT2: Boolean // aapsclient2
    val AAPSCLIENT3: Boolean // aapsclient3
    val PUMPCONTROL: Boolean
    val PUMPDRIVERS: Boolean
    val FLAVOR: String
    val VERSION_NAME: String
    val HEAD: String
    val COMMITTED: Boolean
    val BUILD_VERSION: String
    val REMOTE: String
    val BUILD_TYPE: String
    val VERSION: String

    /**
     * Which platform this build runs on - "Desktop", "iOS", or empty.
     *
     * Empty on Android, and the About dialog then shows no platform line: the same version string
     * now appears on three platforms, so the two that are not the original say which they are. A
     * default is given so that a Config implementation which predates this - a test double, say -
     * keeps compiling and behaves as Android does.
     */
    val PLATFORM: String get() = ""
    val APPLICATION_ID: String
    val DEBUG: Boolean
    val currentDeviceModelString: String

    /**
     * `"<manufacturer> <model>"` - the device name Nightscout stores, as `"openaps://$deviceModelForUpload"`.
     *
     * **A transmitted format: do not change it.** Deliberately not [currentDeviceModelString], which
     * appends `" (<device>)"` and is for the export metadata and the preference screen. The two have
     * always differed; sharing one would silently rewrite what every existing installation uploads.
     */
    val deviceModelForUpload: String

    /**
     * The device maker alone, as the platform reports it - `"Google"`, `"samsung"`, `"Xiaomi"`.
     *
     * Separate from [deviceModelForUpload] because that one is a transmitted format and must not be
     * taken apart by callers. Used to build the per-manufacturer battery-settings help link.
     */
    val deviceManufacturer: String
    val appName: TextRef

    val initProgressFlow: StateFlow<InitProgress>
    val initSnackbarFlow: SharedFlow<String>

    /** Whether the app has completed initialization. Derived from [initProgressFlow]. */
    val appInitialized: Boolean get() = initProgressFlow.value.done

    fun updateInitProgress(step: String, current: Int = 0, total: Int = 0)
    fun initCompleted()
    fun initFailed(error: String)
    fun showInitSnackbar(message: String)

    fun isDev(): Boolean
    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isEnabled(option: ExternalOptions): Boolean
}

/**
 * Suspends until app initialization completes, or [timeoutMs] elapses.
 * Returns true if init is (or became) complete; false on timeout.
 *
 * Use at the top of WorkManager workers (and any background entry point that
 * touches `lateinit` plugin state) to avoid a boot-time race: WorkManager
 * persists pending work across reboots, so a worker can fire before
 * `MainApp`'s background init scope has populated `pluginStore.plugins`.
 */
suspend fun Config.awaitInitialized(timeoutMs: Long = 30_000L): Boolean {
    if (appInitialized) return true
    return withTimeoutOrNull(timeoutMs) {
        initProgressFlow.first { it.done }
    } != null
}