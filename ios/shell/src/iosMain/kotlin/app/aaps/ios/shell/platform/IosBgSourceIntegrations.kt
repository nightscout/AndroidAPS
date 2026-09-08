package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.XDripBroadcast
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/*
 * The glucose-source integrations a client does not have, answered rather than left missing.
 *
 * On iOS the only glucose source is `NSClientSourcePlugin` - it is the one source plugin in
 * `commonMain`, and every other (xDrip, Dexcom, Glimp, Aidex and the rest) is androidMain. That is
 * not a porting gap: xDrip and the Dexcom app talk to AAPS through Android broadcast intents, one
 * app messaging another, and iOS has no equivalent channel. Reaching those services from iOS would
 * be a different design, not a translation of this one.
 *
 * These bindings still have to exist, because **shared UI asks about them**:
 * `CalibrationDialogViewModel` decides whether to offer sending a calibration,
 * `ElementAvailability` and `TreatmentViewModel` decide which entries to show, and
 * `MaintenanceViewModel` reads the xDrip upload queue. Each of them asks "is this enabled?" and
 * hides the option when the answer is no - which is exactly right for a client whose glucose comes
 * from Nightscout.
 *
 * So all four report themselves disabled, which is true, and every call is logged. They are answers
 * about what this platform is, not placeholders waiting to be finished.
 */

/** xDrip as a glucose source: it broadcasts readings to other apps, and iOS has no such channel. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosXDripSource @Inject constructor(
    private val aapsLogger: AAPSLogger
) : XDripSource {

    override fun isEnabled(): Boolean {
        aapsLogger.notOnThisPlatform("XDripSource.isEnabled - no broadcast channel on iOS")
        return false
    }
}

/** Sending back to xDrip, the other direction of the same missing channel. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosXDripBroadcast @Inject constructor(
    private val aapsLogger: AAPSLogger
) : XDripBroadcast {

    override fun isEnabled(): Boolean = false

    override fun sendCalibration(bg: Double): Boolean {
        aapsLogger.notOnThisPlatform("XDripBroadcast.sendCalibration")
        return false
    }

    override fun sendToXdrip(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) =
        aapsLogger.notOnThisPlatform("XDripBroadcast.sendToXdrip")

    override fun sendToXdrip(collection: String, dataPairs: List<DataSyncSelector.DataPair>, progress: String) =
        aapsLogger.notOnThisPlatform("XDripBroadcast.sendToXdrip")
}

/** The upload queue for xDrip. Nothing is queued, because nothing can be sent. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosDataSyncSelectorXdrip @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DataSyncSelectorXdrip {

    override fun queueSize(): Long = 0

    override suspend fun resetToNextFullSync() = aapsLogger.notOnThisPlatform("DataSyncSelectorXdrip.resetToNextFullSync")

    override suspend fun doUpload() = aapsLogger.notOnThisPlatform("DataSyncSelectorXdrip.doUpload")

    override fun profileReceived(timestamp: Long) = aapsLogger.notOnThisPlatform("DataSyncSelectorXdrip.profileReceived")
}

/** The Dexcom app's own build of the receiver - an Android app, reached by an Android permission. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosDexcomBoyda @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DexcomBoyda {

    override fun isEnabled(): Boolean {
        aapsLogger.notOnThisPlatform("DexcomBoyda.isEnabled - the Dexcom app is Android only")
        return false
    }

    override fun requestPermissionIfNeeded() = aapsLogger.notOnThisPlatform("DexcomBoyda.requestPermissionIfNeeded")

    override fun dexcomPackages(): List<String> = emptyList()
}
