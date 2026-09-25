package app.aaps.core.interfaces.configuration

import app.aaps.core.keys.interfaces.AppPlatform
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
 * @param reconfiguringDepth How many reconfigurations are in progress; see [reconfiguring]
 */
data class InitProgress(
    val step: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
    val reconfiguringDepth: Int = 0
) {

    /**
     * Plugin state is being rebuilt right now, so nothing may read it.
     *
     * Kept HERE, beside [done], rather than as a second flow, for one reason: [done] must not be
     * cleared to express this. It is also the splash gate - `AapsAppRoot` renders the splash under
     * `AnimatedVisibility(visible = !initProgress.done)` and the app content under
     * `AnimatedVisibility(visible = initProgress.done)`, and `AnimatedVisibility` REMOVES the subtree
     * from composition. Clearing [done] would put the splash over a running app and take the import
     * screen performing the apply off the display, mid-apply. So [done] stays true and this says the
     * rest.
     *
     * A depth rather than a flag because the windows nest: an import brackets the preference rewrite
     * and then the apply, and a caller must never be able to end someone else's window early.
     */
    val reconfiguring: Boolean get() = reconfiguringDepth > 0

    /** One more reconfiguration in progress. See [reconfiguring]. */
    fun enteringReconfigure(): InitProgress = copy(reconfiguringDepth = reconfiguringDepth + 1)

    /**
     * One fewer reconfiguration in progress, never below zero.
     *
     * The clamp matters: an unbalanced end that drove this negative would make the next real
     * [enteringReconfigure] look like "still not reconfiguring", and the window would silently stop
     * working. Failing open here is the safe direction - the worst case is a window that closes early
     * once, not one that never opens again.
     */
    fun leavingReconfigure(): InitProgress = copy(reconfiguringDepth = maxOf(0, reconfiguringDepth - 1))
}

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
    EMULATE_CARELEVO("emulate_carelevo"),
    // Critical tier — the driver resolves these to a WARNING-severity cause and auto-discards the patch.
    EMULATE_CARELEVO_LOW_BATTERY("emulate_carelevo_low_battery"),
    EMULATE_CARELEVO_OCCLUSION("emulate_carelevo_occlusion"),
    // Advisory tier — resolves to the ALERT-severity cause of the same condition, a plain user-clearable alarm.
    EMULATE_CARELEVO_LOW_BATTERY_ALERT("emulate_carelevo_low_battery_alert"),
    EMULATE_CARELEVO_INVALID_TEMPERATURE("emulate_carelevo_invalid_temperature"),
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
     * Which platform this build runs on - "Android", "Desktop" or "iOS".
     *
     * Shown as its own line in the About dialog. The same version string now appears on three
     * platforms, so a bug report has to say which one it came from.
     *
     * The default is empty rather than a guess: a Config implementation that predates this - a test
     * double, say - keeps compiling, and the dialog simply omits the line rather than claiming a
     * platform the build never declared.
     */
    val PLATFORM: String get() = ""

    /**
     * The same fact as [PLATFORM], as something code can branch on.
     *
     * [PLATFORM] is a string shown to the user in the About dialog, and a display string should not
     * become load bearing - it was briefly used as an "is this Android" test, which was already
     * wrong because `ConfigImpl` sets it to "Android" rather than leaving it empty.
     *
     * This is what decides whether a platform-specific piece of UI is drawn, and what
     * [app.aaps.core.keys.interfaces.PreferenceKey.platforms] is matched against. Deliberately
     * without a default: a new shell has to say what it is rather than inherit someone else's answer.
     */
    val platform: AppPlatform
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

    /**
     * Whether the app is initialized AND its plugin state is valid to read right now.
     *
     * Derived from [initProgressFlow]: initialization has finished once and no reconfiguration is in
     * progress. The second half matters because a settings import rebuilds plugin state on a running
     * app, and during that rebuild there is a moment with no pump elected - reading the active plugin
     * then hits `PluginStore`'s deliberate "No pump selected" assertion. Roughly 35 call sites already
     * guard on this property, so they all close during an import with no change of their own.
     */
    val appInitialized: Boolean get() = initProgressFlow.value.run { done && !reconfiguring }

    fun updateInitProgress(step: String, current: Int = 0, total: Int = 0)
    fun initCompleted()
    fun initFailed(error: String)
    fun showInitSnackbar(message: String)

    /**
     * Open a window in which plugin state is being rebuilt and must not be read.
     *
     * Prefer [whileReconfiguring] - it pairs this with [endReconfiguring] in a `finally`. Calling this
     * without a guaranteed matching end leaves [appInitialized] false for the rest of the process, and
     * `WizardBolusExecutorImpl` refuses on that in two places: the user could not bolus. That is worse
     * than the window this closes.
     */
    fun beginReconfiguring()

    /** Close one window opened by [beginReconfiguring]. Never drops below zero. */
    fun endReconfiguring()

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
        // `done && !reconfiguring`, not `done` alone. During an import `done` is already true, so
        // waiting on it would return instantly and let the caller read plugin state in exactly the
        // window it was trying to avoid - KeepAliveWorker fires every five minutes and then calls
        // checkPump().
        initProgressFlow.first { it.done && !it.reconfiguring }
    } != null
}

/**
 * Run [block] with plugin state marked as being rebuilt, and always unmark it afterwards.
 *
 * The `finally` is the point. Neither `executeImport` implementation has one today, so a throw while
 * rewriting the preferences would otherwise leave the window open forever - see [Config.beginReconfiguring].
 */
suspend fun <T> Config.whileReconfiguring(block: suspend () -> T): T {
    beginReconfiguring()
    try {
        return block()
    } finally {
        endReconfiguring()
    }
}