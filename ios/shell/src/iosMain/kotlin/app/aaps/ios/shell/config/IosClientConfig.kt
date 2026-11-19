package app.aaps.ios.shell.config

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

/**
 * What kind of build this is, answered for iOS.
 *
 * On Android these values come from the Gradle flavour: `full`, `pumpcontrol`, `aapsclient`. iOS has
 * no flavours - one Kotlin framework is linked into every target - so the answer is read from the
 * bundle identifier instead, through [flavorFor]. The `AAPS` target is the full app: it runs the loop
 * and drives a pump. `AAPSClient` and `AAPSClient2` are followers. There is no `pumpcontrol` target.
 *
 * This is a real implementation, not a stand-in. Getting [APS] or [PUMPDRIVERS] wrong here would
 * make shared code offer features the app cannot perform - or, the dangerous way round, let a
 * follower run the loop.
 */
class IosClientConfig(
    override val VERSION_NAME: String = GeneratedBuildInfo.VERSION,
    override val APPLICATION_ID: String = NSBundle.mainBundle.bundleIdentifier ?: DEFAULT_BUNDLE_ID,
    /**
     * Where the option marker files are looked for. A test points it elsewhere.
     *
     * `extra` inside the app's Documents directory, which the Files app shows as
     * **On My iPhone → AAPSClient**. That is the whole reason this can work at all: the folder is
     * reachable by the person using the phone, so `extra/engineering_mode` can be created by hand
     * there exactly as it is created in `AAPS/extra` on Android.
     */
    private val extraDir: String? = defaultExtraDir()
) : Config {

    override val SUPPORTED_NS_VERSION: Int = 150000

    /**
     * Which build this is, worked out from the bundle it was built into.
     *
     * Read rather than written down, for the same reason as [appName] and the app icon: one Kotlin
     * framework is linked into **both** AAPSClient and AAPSClient2, so a fixed answer here is wrong
     * in one of the two targets no matter which value is chosen. It used to say client 1 always, and
     * the overview tints itself by these flags - so both clients drew the same colour, which is the
     * one thing the tint exists to prevent.
     *
     * Derived through [FLAVOR] so the shape matches Android, where `ConfigImpl` compares
     * `BuildConfig.FLAVOR` against the same names.
     */
    override val FLAVOR: String = flavorFor(APPLICATION_ID)

    // The same expressions as Android's `ConfigImpl`. They must stay below [FLAVOR]: Kotlin sets
    // properties in the order they are written, so above it they would read FLAVOR before it is set
    // and every one of them would quietly be false.
    override val APS: Boolean = FLAVOR == "full"
    override val PUMPCONTROL: Boolean = FLAVOR == "pumpcontrol"
    override val PUMPDRIVERS: Boolean = FLAVOR == "full" || FLAVOR == "pumpcontrol"
    override val AAPSCLIENT: Boolean = FLAVOR == "aapsclient" || FLAVOR == "aapsclient2" || FLAVOR == "aapsclient3"

    override val AAPSCLIENT1: Boolean = FLAVOR == "aapsclient"
    override val AAPSCLIENT2: Boolean = FLAVOR == "aapsclient2"
    override val AAPSCLIENT3: Boolean = FLAVOR == "aapsclient3"
    /**
     * Which configuration built this, from the bundle - the same trick as [appName] and the client
     * number, and for the same reason: one Kotlin framework serves every configuration, so it cannot
     * know on its own. Pinned to "debug" before, which made a release build describe itself as a
     * development one.
     */
    override val BUILD_TYPE: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("AAPSBuildType") as? String)?.lowercase() ?: "debug"
    override val PLATFORM: String = GeneratedBuildInfo.PLATFORM
    override val platform: AppPlatform = AppPlatform.Ios
    override val VERSION: String = VERSION_NAME
    override val BUILD_VERSION: String = GeneratedBuildInfo.BUILD
    /** Anything that is not a release build is treated as a debug one, which is the safe way round. */
    override val DEBUG: Boolean = BUILD_TYPE != "release"

    // Filled by the build, the same three facts Android has sent since 2019: which commit, which
    // repository, and whether the tree was clean. Together they are what tells a crash report from
    // upstream apart from one out of a fork. A build with no git says "NoGitSystemAvailable" and
    // reports not-committed, rather than inventing a commit or claiming a clean tree.
    override val HEAD: String = GeneratedBuildInfo.HEAD
    override val COMMITTED: Boolean = GeneratedBuildInfo.COMMITTED
    override val REMOTE: String = GeneratedBuildInfo.REMOTE

    override val currentDeviceModelString: String =
        UIDevice.currentDevice.let { "${it.model} ${it.systemName} ${it.systemVersion}" }

    /** Nightscout stores this verbatim, so keep it to the device identity - no OS version. */
    override val deviceModelForUpload: String = UIDevice.currentDevice.model
    // Apple is the only maker of iOS devices, so there is nothing to look up.
    override val deviceManufacturer: String = "Apple"

    /**
     * What the app calls itself, taken from the bundle it was built into.
     *
     * Android sets `app_name` per flavour: "AAPS" for the full app and "AAPSClient" for a follower. A
     * follower claiming to be the master is exactly the confusion the flavour names exist to prevent.
     *
     * Read rather than written down, for the same reason as the app icon: one Kotlin framework is
     * linked into AAPS, AAPSClient and AAPSClient2, and each target already sets its own
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

    /**
     * Not done yet, which is what makes the splash screen appear.
     *
     * `AapsAppRoot` composes the app itself only once this reports done, so nothing that reads the
     * active pump is built while `AppStartSequence` is still running the preference migrations, the
     * database vacuum and the plugin start. That gate is the whole protection: a view model built
     * while the plugin list is still empty throws from its own constructor, and on Kotlin/Native an
     * exception thrown there is unhandled and **aborts the process**.
     *
     * This started at `done = true` before, because the shell registered its plugins itself, on the
     * main thread, before the first screen - and then had no start sequence for a splash to cover.
     * It runs the shared sequence now, so the splash has to cover it, exactly as on Android.
     */
    private val _initProgressFlow = MutableStateFlow(InitProgress(done = false))
    override val initProgressFlow: StateFlow<InitProgress> = _initProgressFlow.asStateFlow()

    private val _initSnackbarFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val initSnackbarFlow: SharedFlow<String> = _initSnackbarFlow.asSharedFlow()

    // The progress and the completion replace the whole value, which is what the start-up progress
    // wants. A failure copies it instead, the same as Android, so that `done` stays false - see
    // [initFailed]. The one field that must survive is the reconfigure depth:
    // it belongs to whoever opened the window, not to init progress, and clobbering it here would
    // close a window that `whileReconfiguring` still believes is open - reopening the app to readers
    // in the middle of an import. This used to matter more on iOS than anywhere else, when the
    // platform started at `done = true` and had no start sequence to put the state right again. It
    // runs the shared sequence now, so iOS is no different here - the depth still has to survive.
    override fun updateInitProgress(step: String, current: Int, total: Int) {
        _initProgressFlow.update { InitProgress(step = step, current = current, total = total, reconfiguringDepth = it.reconfiguringDepth) }
    }

    override fun initCompleted() {
        _initProgressFlow.update { InitProgress(done = true, reconfiguringDepth = it.reconfiguringDepth) }
    }

    /**
     * Keeps the splash up and shows the reason on it, the same as Android's `ConfigImpl`.
     *
     * `done` must stay false here. `AapsAppRoot` draws the failure - "Initialization failed", the reason
     * and Close - only inside the splash, which it shows only while not done. This set `done = true`
     * before, which hid that message and built the app's screens on top of a start that never finished;
     * the first view model that read the active pump then threw "No pump selected" and, on Kotlin/Native,
     * took the process down. A copy also keeps the reconfigure depth.
     */
    override fun initFailed(error: String) {
        _initProgressFlow.update { it.copy(error = error) }
    }

    override fun beginReconfiguring() {
        _initProgressFlow.update { it.enteringReconfigure() }
    }

    override fun endReconfiguring() {
        _initProgressFlow.update { it.leavingReconfigure() }
    }

    override fun showInitSnackbar(message: String) {
        _initSnackbarFlow.tryEmit(message)
    }

    override fun isDev(): Boolean = DEBUG

    /** Read from the file, the same as Android and the desktop do. */
    override fun isEngineeringMode(): Boolean = isEnabled(ExternalOptions.ENGINEERING_MODE)

    /**
     * The same expression as Android, and a safety gate rather than a detail.
     *
     * `SafetyPlugin.isClosedLoopAllowed` reads it: a development build of the full app may close the
     * loop only in engineering mode. A follower has no loop to close, so it is always true there.
     *
     * This was a fixed `true` while iOS built followers only. Left that way, a Debug build of the full
     * app would have closed the loop with that gate silently skipped.
     */
    override fun isEngineeringModeOrRelease(): Boolean = if (!APS) true else isEngineeringMode() || !isDev()

    /**
     * Read the same way Android does - by the presence of a file in the `extra` folder.
     *
     * This answered a flat `false` before, on the grounds that iOS had nowhere for a user to put such
     * a file. That was true when it was written and stopped being true when the app's Documents
     * directory was exposed to the Files app: a person can now create
     * **On My iPhone → AAPSClient → extra → engineering_mode** with no computer and no cable, which
     * is the same gesture as `touch AAPS/extra/engineering_mode` on the other two platforms.
     *
     * Checked on each call rather than cached, because the file appears while the app is running -
     * the user has no way to restart it first, and a value read once at start up would ignore them
     * until the next launch.
     */
    override fun isEnabled(option: ExternalOptions): Boolean {
        val directory = extraDir ?: return false
        return NSFileManager.defaultManager.fileExistsAtPath("$directory/${option.filename}")
    }

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
 * Matched on the end of the identifier only, never the whole of it. The full app is
 * `app.aaps.<teamid>.full`, and a client is `app.aaps.client2` when published or
 * `app.aaps.<teamid>.client2` when somebody builds it themselves - the team id in the middle must
 * not change the answer. The older `info.nightscout.aapsclient2` still matches too.
 *
 * The full app is matched strictly, on `.full` with the dot, and checked first. Getting this wrong
 * the other way round - a follower taking itself for the full app - would let it run the loop and
 * drive a pump, so anything that is not clearly the full app is a client. Client 1's identifier is a
 * prefix of the others, so the numbered ones come before it, and anything unrecognised is client 1,
 * which keeps exactly one of the flags true.
 */
internal fun flavorFor(bundleId: String): String = when {
    bundleId.endsWith(".full")   -> "full"
    bundleId.endsWith("client2") -> "aapsclient2"
    bundleId.endsWith("client3") -> "aapsclient3"
    else                         -> "aapsclient"
}

/**
 * `extra` inside the app's own Documents directory, created if it is not there yet.
 *
 * Made rather than merely looked for, so the folder is visible in the Files app before anyone needs
 * it - a user told to create `extra/engineering_mode` should find `extra` already waiting rather
 * than have to guess the name and spelling.
 */
@OptIn(ExperimentalForeignApi::class)
private fun defaultExtraDir(): String? {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
        ?: return null
    val extra = "$documents/extra"
    NSFileManager.defaultManager.createDirectoryAtPath(extra, withIntermediateDirectories = true, attributes = null, error = null)
    return extra
}
