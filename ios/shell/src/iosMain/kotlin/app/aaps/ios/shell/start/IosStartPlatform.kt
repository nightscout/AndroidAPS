package app.aaps.ios.shell.start

import app.aaps.appshell.start.AppStartPlatform
import app.aaps.appshell.start.AppStartSequence
import app.aaps.core.interfaces.configuration.Config
import app.aaps.implementation.maintenance.PeriodicMaintenance
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSBundle

/**
 * What only iOS can do while the app starts. [AppStartSequence] decides when each part runs.
 *
 * Deliberately thin. Android's counterpart registers broadcast receivers in [startServices]; on iOS
 * the equivalent watchers are installed by `startApp` **before** the sequence runs, and they stay
 * there rather than moving here - see [startServices].
 */
class IosStartPlatform(
    private val config: Config,
    private val periodicMaintenance: PeriodicMaintenance,
    private val scope: CoroutineScope,
    /**
     * The build number, for the version record in the database.
     *
     * `CFBundleVersion` is what `:ios:shell:generateIosVersionXcconfig` feeds into the target, so this
     * is the same number App Store Connect sees. A test binary has no app bundle, hence the fallback -
     * the same arrangement [app.aaps.ios.shell.config.IosClientConfig] uses for the app name.
     */
    override val versionCode: Int = bundleVersion()
) : AppStartPlatform {

    /**
     * The device for the "AAPS started" note, read from [Config] rather than from UIKit again.
     *
     * Android reports manufacturer and model, so "Apple iPhone" is the matching answer here. Note that
     * `UIDevice.model` behind it says only "iPhone" - it does not tell an iPhone 12 from an iPhone 16,
     * which Apple exposes only as a machine identifier like "iPhone16,2". Changing that would change
     * what Nightscout stores, so it is left as it is.
     */
    override val deviceName: String = "${config.deviceManufacturer} ${config.deviceModelForUpload}"

    /**
     * Nothing, on purpose.
     *
     * The iOS-only watchers - the battery watch, the protection lifecycle, the time zone watcher, the
     * foreground watcher and the settings flush on backgrounding - are installed in `startApp`, before
     * the sequence is launched, and that is where they belong:
     *
     * - they touch UIKit, so they cannot live in the graph or in shared code anyway;
     * - they are observers, and one installed late misses whatever happened meanwhile;
     * - `startBatteryWatch` gates Nightscout sync, and the sequence can take seconds over the
     *   migrations and the database vacuum. Waiting for that would hold the first sync back.
     *
     * So this is empty because the work is already done, not because iOS has nothing to start.
     */
    override fun startServices() = Unit

    /**
     * The 5-minute checks. A follower runs the shared housekeeping on a timer of its own.
     *
     * `PeriodicMaintenance.start` runs its first pass at once. That is safe here because the sequence
     * pre-snoozes the alarms immediately before calling this, which is what keeps a cold start from
     * raising "BG readings missed" for its own downtime (nightscout/AndroidAPS#5495).
     *
     * **Not enough for the full app, which drives a pump.** It needs `KeepAliveRunner.runOnce` on every
     * wake rather than a timer, because iOS keeps nothing running between wakes - and until that is
     * wired, the pump status check, the pump-unreachable alarm and the zero temp basal retry do not run
     * on iOS at all. See the keep-alive section of `_docs/ios_blockers.md`.
     */
    override fun startKeepAlive() {
        periodicMaintenance.start(scope)
    }

    override suspend fun beforeReady() = Unit
}

/** `CFBundleVersion` as a number, or 0 where there is no app bundle to ask. */
private fun bundleVersion(): Int =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 0
