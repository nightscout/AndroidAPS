package app.aaps.ios.shell.missing

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
 * Three integrations that are Android by their nature, not by accident.
 *
 * xDrip and the Dexcom app talk to AAPS through Android broadcast intents - one app sending a
 * message to another, which iOS has no equivalent of. An iOS build would have to reach these
 * services a different way entirely, or not at all, so these are not "not ported yet": they are
 * answers about what this platform can do.
 *
 * All three therefore report themselves disabled, which is true, and every call is logged.
 */

/** xDrip as a glucose source: it broadcasts readings to other apps, and iOS has no such channel. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosXDripSource @Inject constructor(
    private val aapsLogger: AAPSLogger
) : XDripSource {

    override fun isEnabled(): Boolean {
        aapsLogger.notOnIosYet("XDripSource.isEnabled - no broadcast channel on iOS")
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
        aapsLogger.notOnIosYet("XDripBroadcast.sendCalibration")
        return false
    }

    override fun sendToXdrip(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) =
        aapsLogger.notOnIosYet("XDripBroadcast.sendToXdrip")

    override fun sendToXdrip(collection: String, dataPairs: List<DataSyncSelector.DataPair>, progress: String) =
        aapsLogger.notOnIosYet("XDripBroadcast.sendToXdrip")
}

/** The upload queue for xDrip. Nothing is queued, because nothing can be sent. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosDataSyncSelectorXdrip @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DataSyncSelectorXdrip {

    override fun queueSize(): Long = 0

    override suspend fun resetToNextFullSync() = aapsLogger.notOnIosYet("DataSyncSelectorXdrip.resetToNextFullSync")

    override suspend fun doUpload() = aapsLogger.notOnIosYet("DataSyncSelectorXdrip.doUpload")

    override fun profileReceived(timestamp: Long) = aapsLogger.notOnIosYet("DataSyncSelectorXdrip.profileReceived")
}

/** The Dexcom app's own build of the receiver - an Android app, reached by an Android permission. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosDexcomBoyda @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DexcomBoyda {

    override fun isEnabled(): Boolean {
        aapsLogger.notOnIosYet("DexcomBoyda.isEnabled - the Dexcom app is Android only")
        return false
    }

    override fun requestPermissionIfNeeded() = aapsLogger.notOnIosYet("DexcomBoyda.requestPermissionIfNeeded")

    override fun dexcomPackages(): List<String> = emptyList()
}
