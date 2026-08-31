package app.aaps.ios.shell.config

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.configuration.InitProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIDevice

/**
 * What kind of build this is, answered for iOS.
 *
 * On Android these values come from the Gradle flavour: `full`, `pumpcontrol`, `aapsclient`. iOS
 * builds only the client today, so the answers are fixed rather than generated - the app has no
 * pump drivers and does not run the loop, which is exactly what `AAPSCLIENT` means.
 *
 * This is a real implementation, not a stand-in. Getting [APS] or [PUMPDRIVERS] wrong here would
 * make shared code offer features the app cannot perform.
 */
class IosClientConfig(
    override val VERSION_NAME: String = "0.0-ios",
    override val APPLICATION_ID: String = "info.nightscout.aapsclient"
) : Config {

    override val SUPPORTED_NS_VERSION: Int = 150000

    // No loop and no pump drivers on iOS: this is a follower.
    override val APS: Boolean = false
    override val PUMPCONTROL: Boolean = false
    override val PUMPDRIVERS: Boolean = false

    override val AAPSCLIENT: Boolean = true
    override val AAPSCLIENT1: Boolean = true
    override val AAPSCLIENT2: Boolean = false
    override val AAPSCLIENT3: Boolean = false

    override val FLAVOR: String = "aapsclient"
    override val BUILD_TYPE: String = "debug"
    override val VERSION: String = VERSION_NAME
    override val BUILD_VERSION: String = VERSION_NAME
    override val DEBUG: Boolean = true

    // Filled by the build on Android. iOS has no equivalent wired up yet, and saying so plainly is
    // better than inventing a commit hash.
    override val HEAD: String = "unknown"
    override val COMMITTED: Boolean = true
    override val REMOTE: String = "unknown"

    override val currentDeviceModelString: String =
        UIDevice.currentDevice.let { "${it.model} ${it.systemName} ${it.systemVersion}" }

    /** Nightscout stores this verbatim, so keep it to the device identity - no OS version. */
    override val deviceModelForUpload: String = UIDevice.currentDevice.model
    // Apple is the only maker of iOS devices, so there is nothing to look up.
    override val deviceManufacturer: String = "Apple"

    /** Android returns a string resource id here. iOS has no resource table, so nothing to name. */
    override val appName: TextRef = TextRef.Literal("AAPS")

    private val _initProgressFlow = MutableStateFlow(InitProgress(done = true))
    override val initProgressFlow: StateFlow<InitProgress> = _initProgressFlow.asStateFlow()

    private val _initSnackbarFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val initSnackbarFlow: SharedFlow<String> = _initSnackbarFlow.asSharedFlow()

    override fun updateInitProgress(step: String, current: Int, total: Int) {
        _initProgressFlow.value = InitProgress(step = step, current = current, total = total)
    }

    override fun initCompleted() {
        _initProgressFlow.value = InitProgress(done = true)
    }

    override fun initFailed(error: String) {
        _initProgressFlow.value = InitProgress(done = true, error = error)
    }

    override fun showInitSnackbar(message: String) {
        _initSnackbarFlow.tryEmit(message)
    }

    override fun isDev(): Boolean = DEBUG
    override fun isEngineeringMode(): Boolean = false
    override fun isEngineeringModeOrRelease(): Boolean = true

    /**
     * Always false: the options are toggled by dropping a file next to the Android app's data.
     * iOS has no such directory, so nothing can turn one on.
     */
    override fun isEnabled(option: ExternalOptions): Boolean = false
}
