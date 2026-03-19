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
    fun isUnfinishedMode(): Boolean

    fun showUserActionsOnWatchOnly(): Boolean
    fun ignoreNightscoutV3Errors(): Boolean
    fun doNotSendSmsOnProfileChange(): Boolean
    fun enableAutotune(): Boolean

    /**
     * Disable LeakCanary (memory leaks detection). By default it's enabled in DEBUG builds.
     */
    fun disableLeakCanary(): Boolean

    /**
     * Dana BLE emulation
     */
    fun emulateDanaRSv1(): Boolean
    fun emulateDanaRSv3(): Boolean
    fun emulateDanaBLE5(): Boolean
}