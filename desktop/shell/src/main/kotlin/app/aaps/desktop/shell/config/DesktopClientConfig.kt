package app.aaps.desktop.shell.config

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * What kind of build this is, answered for desktop.
 *
 * On Android these values come from the Gradle flavour: `full`, `pumpcontrol`, `aapsclient`. Desktop
 * builds only the client, so the answers are fixed rather than generated - it has no pump drivers and
 * does not run the loop, which is exactly what `AAPSCLIENT` means.
 *
 * This is a real implementation, not a stand-in. Getting [APS] or [PUMPDRIVERS] wrong here would make
 * shared code offer features the app cannot perform.
 */
class DesktopClientConfig(
    override val VERSION_NAME: String = GeneratedBuildInfo.VERSION,
    override val APPLICATION_ID: String = "info.nightscout.aapsclient"
) : Config {

    override val SUPPORTED_NS_VERSION: Int = 150000

    // No loop and no pump drivers on desktop: this is a follower.
    override val APS: Boolean = false
    override val PUMPCONTROL: Boolean = false
    override val PUMPDRIVERS: Boolean = false

    override val AAPSCLIENT: Boolean = true
    override val AAPSCLIENT1: Boolean = true
    override val AAPSCLIENT2: Boolean = false
    override val AAPSCLIENT3: Boolean = false

    override val FLAVOR: String = "aapsclient"
    override val BUILD_TYPE: String = "debug"
    override val PLATFORM: String = GeneratedBuildInfo.PLATFORM
    override val platform: AppPlatform = AppPlatform.Desktop
    override val VERSION: String = VERSION_NAME
    override val BUILD_VERSION: String = GeneratedBuildInfo.BUILD
    override val DEBUG: Boolean = true

    // Filled by the build on Android. Desktop has no equivalent wired up yet, and saying so plainly
    // is better than inventing a commit hash.
    override val HEAD: String = "unknown"
    override val COMMITTED: Boolean = true
    override val REMOTE: String = "unknown"

    override val currentDeviceModelString: String =
        "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"

    /** Nightscout stores this verbatim, so keep it to the machine identity - no version. */
    override val deviceModelForUpload: String = System.getProperty("os.name") ?: "Desktop"
    override val deviceManufacturer: String = System.getProperty("os.name") ?: "Desktop"

    /**
     * What the app calls itself, by the same rule as the Android flavours.
     *
     * Android sets `app_name` per flavour in `app/build.gradle.kts`, and this build is a client, so
     * it is "AAPSClient" - not "AAPS", which is the name of the master. The About dialog and the
     * window title both show it, and a follower claiming to be the master is exactly the kind of
     * confusion the flavour names exist to prevent.
     *
     * A literal rather than a resource: desktop has no resource table, and the name is not
     * translated on Android either.
     */
    override val appName: TextRef = TextRef.Literal(
        when {
            AAPSCLIENT3 -> "AAPSClient3"
            AAPSCLIENT2 -> "AAPSClient2"
            AAPSCLIENT1 -> "AAPSClient"
            PUMPCONTROL -> "Pumpcontrol"
            else        -> "AAPS"
        }
    )

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
     * Read the same way Android does - by the presence of a file - because desktop does have a place
     * to put one. The file sits in the AAPS folder beside the database, so turning an option on is
     * `touch ~/.aaps/engineering_mode`.
     */
    override fun isEnabled(option: ExternalOptions): Boolean =
        File(File(System.getProperty("user.home"), ".aaps"), option.filename).exists()
}
