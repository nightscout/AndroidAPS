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
import platform.Foundation.NSBundle
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
    override val VERSION_NAME: String = GeneratedBuildInfo.VERSION,
    override val APPLICATION_ID: String = NSBundle.mainBundle.bundleIdentifier ?: DEFAULT_BUNDLE_ID
) : Config {

    override val SUPPORTED_NS_VERSION: Int = 150000

    // No loop and no pump drivers on iOS: this is a follower.
    override val APS: Boolean = false
    override val PUMPCONTROL: Boolean = false
    override val PUMPDRIVERS: Boolean = false

    override val AAPSCLIENT: Boolean = true

    /**
     * Which client this build is, worked out from the bundle it was built into.
     *
     * Read rather than written down, for the same reason as [appName] and the app icon: one Kotlin
     * framework is linked into **both** AAPSClient and AAPSClient2, so a fixed answer here is wrong
     * in one of the two targets no matter which value is chosen. It used to say client 1 always, and
     * the overview tints itself by these flags - so both clients drew the same colour, which is the
     * one thing the tint exists to prevent.
     *
     * Derived through [FLAVOR] so the shape matches Android, where `ConfigImpl` compares
     * `BuildConfig.FLAVOR` against the same three names.
     */
    override val FLAVOR: String = clientFlavorFor(APPLICATION_ID)

    override val AAPSCLIENT1: Boolean = FLAVOR == "aapsclient"
    override val AAPSCLIENT2: Boolean = FLAVOR == "aapsclient2"
    override val AAPSCLIENT3: Boolean = FLAVOR == "aapsclient3"
    override val BUILD_TYPE: String = "debug"
    override val PLATFORM: String = GeneratedBuildInfo.PLATFORM
    override val VERSION: String = VERSION_NAME
    override val BUILD_VERSION: String = GeneratedBuildInfo.BUILD
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

    /**
     * What the app calls itself, taken from the bundle it was built into.
     *
     * Android sets `app_name` per flavour, and this build is a client, so the name is "AAPSClient" -
     * not "AAPS", which is the name of the master. A follower claiming to be the master is exactly
     * the confusion the flavour names exist to prevent.
     *
     * Read rather than written down, for the same reason as the app icon: one Kotlin framework is
     * linked into both AAPSClient and AAPSClient2, and each target already sets its own
     * `CFBundleDisplayName`. Asking the bundle therefore names the app that is really running, and
     * cannot disagree with the name under its icon on the home screen.
     *
     * The fallback matters only in a test binary, which has no app bundle to ask.
     */
    override val appName: TextRef = TextRef.Literal(
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String
            ?: NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleName") as? String
            ?: "AAPSClient"
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
     * Always false: the options are toggled by dropping a file next to the Android app's data.
     * iOS has no such directory, so nothing can turn one on.
     */
    override fun isEnabled(option: ExternalOptions): Boolean = false

    companion object {

        /** Used only where there is no app bundle to ask, which in practice means a test binary. */
        internal const val DEFAULT_BUNDLE_ID = "info.nightscout.aapsclient"
    }
}

/**
 * The Android flavour name this bundle identifier corresponds to.
 *
 * Separate and pure so it can be tested: the bundle identifier of a test binary is the test
 * binary's, so the real [IosClientConfig] can never observe anything but client 1 under test.
 *
 * Matched on the trailing `client<n>` rather than on a whole identifier, so it holds for both the
 * `app.aaps.client2` the targets use now and the older `info.nightscout.aapsclient2`. Client 1's
 * identifier is a prefix of the others, so the numbered ones are checked first. Anything
 * unrecognised is client 1, which keeps exactly one of the three flags true.
 */
internal fun clientFlavorFor(bundleId: String): String = when {
    bundleId.endsWith("client2") -> "aapsclient2"
    bundleId.endsWith("client3") -> "aapsclient3"
    else                         -> "aapsclient"
}
