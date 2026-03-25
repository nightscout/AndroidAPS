package app.aaps.core.interfaces.configuration

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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
    val APPLICATION_ID: String
    val DEBUG: Boolean
    val currentDeviceModelString: String
    val appName: Int

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