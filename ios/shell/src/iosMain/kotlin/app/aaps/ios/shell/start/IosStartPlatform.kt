package app.aaps.ios.shell.start

import app.aaps.appshell.start.AppStartPlatform
import app.aaps.appshell.start.AppStartSequence
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.delayUntil
import app.aaps.implementation.maintenance.KeepAliveRunner
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val keepAliveRunner: KeepAliveRunner,
    private val aapsLogger: AAPSLogger,
    private val scope: CoroutineScope,
    private val clock: Clock,
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
     * The 5-minute checks: the same [KeepAliveRunner] pass Android's `KeepAliveWorker` runs, for the full
     * app and a client alike - the pump status check, the pump unreachable alarm, the zero temp basal
     * retry, the status upload and the shared housekeeping. Until this ran it, iOS ran only the
     * housekeeping, so none of the pump checks happened at all.
     *
     * iOS keeps nothing running while the app is suspended, so this is a loop that waits on the wall
     * clock in short steps ([delayUntil]): when the app runs again, after a Bluetooth wake or in the
     * front, a pass that is due runs within [STEP]. A plain `delay` counts only awake time, so after the
     * phone slept, the next pass could be hours late. `minInterval` skips a pass that comes too soon.
     *
     * The first pass runs at once. That is safe here because the sequence pre-snoozes the alarms
     * immediately before calling this, which is what keeps a cold start from raising "BG readings
     * missed" for its own downtime (nightscout/AndroidAPS#5495).
     */
    override fun startKeepAlive() {
        scope.launch {
            while (isActive) {
                try {
                    keepAliveRunner.runOnce(minInterval = MIN_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad pass must not stop the checks for the rest of the session.
                    aapsLogger.error(LTag.CORE, "Keep alive pass failed", e)
                }
                clock.delayUntil(clock.now().toEpochMilliseconds() + INTERVAL.inWholeMilliseconds, step = STEP)
            }
        }
    }

    override suspend fun beforeReady() = Unit

    private companion object {

        /** How often a pass runs, as `KeepAliveWorker` schedules it on Android. */
        val INTERVAL = 5.minutes

        /** The same skip `KeepAliveWorker` uses for a pass that comes too soon after the last one. */
        val MIN_INTERVAL = 4.minutes

        /** Short, because a Bluetooth wake gives the app only a few seconds to run. */
        val STEP = 10.seconds
    }
}

/** `CFBundleVersion` as a number, or 0 where there is no app bundle to ask. */
private fun bundleVersion(): Int =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 0
