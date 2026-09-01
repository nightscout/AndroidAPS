package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.ui.activityMonitor.ActivityStats
import app.aaps.ui.activityMonitor.ActivityStatsProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Integrations that are Android apps talking to Android apps.
 *
 * xDrip+ and Dexcom are phone applications reached through broadcast intents. There is no desktop
 * equivalent to point at, so these report being switched off rather than pretending to send. Every
 * one of them answers a capability question first - `isEnabled()` - which is what keeps this honest:
 * the feature is visibly absent instead of present and dropping data.
 */

/**
 * xDrip+ cannot be broadcast to from a desktop.
 *
 * [isEnabled] false is the important one, because callers check it before sending. If something
 * sends anyway, the send methods report failure rather than success - a dropped upload that reported
 * success would look like data reached xDrip when it never left the machine.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopXDripBroadcast @Inject constructor(
    private val aapsLogger: AAPSLogger
) : XDripBroadcast {

    override fun isEnabled(): Boolean = false

    override fun sendCalibration(bg: Double): Boolean {
        aapsLogger.error(LTag.CORE, "Desktop cannot broadcast a calibration to xDrip+")
        return false
    }

    override fun sendToXdrip(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) {
        aapsLogger.error(LTag.CORE, "Desktop cannot broadcast $collection to xDrip+")
    }

    override fun sendToXdrip(collection: String, dataPairs: List<DataSyncSelector.DataPair>, progress: String) {
        aapsLogger.error(LTag.CORE, "Desktop cannot broadcast $collection to xDrip+ (${dataPairs.size} items)")
    }
}

/** xDrip+ as a glucose source needs the xDrip+ app, which is an Android one. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopXDripSource @Inject constructor() : XDripSource {

    override fun isEnabled(): Boolean = false
}

/**
 * The Dexcom app is Android only, so there is nothing to ask permission of.
 *
 * [dexcomPackages] returns an empty list rather than the Android package names: nothing here can
 * check whether one is installed, and returning names would invite a caller to believe otherwise.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopDexcomBoyda @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DexcomBoyda {

    override fun isEnabled(): Boolean = false

    override fun requestPermissionIfNeeded() {
        aapsLogger.debug(LTag.CORE, "No Dexcom app on desktop, so no permission to request")
    }

    override fun dexcomPackages(): List<String> = emptyList()
}

/**
 * The xDrip upload queue, with nothing to upload to.
 *
 * [queueSize] is zero honestly: nothing is ever queued because the broadcast above is off, so this
 * is not a stub hiding a backlog. [doUpload] records the attempt so a caller that reached here
 * despite the disabled broadcast is visible.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopDataSyncSelectorXdrip @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DataSyncSelectorXdrip {

    override fun queueSize(): Long = 0L

    override suspend fun resetToNextFullSync() {
        aapsLogger.debug(LTag.CORE, "No xDrip sync on desktop; nothing to reset")
    }

    override suspend fun doUpload() {
        aapsLogger.error(LTag.CORE, "Desktop has no xDrip+ to upload to")
    }

    override fun profileReceived(timestamp: Long) {
        aapsLogger.debug(LTag.CORE, "No xDrip sync on desktop; profile at $timestamp ignored")
    }
}

/**
 * How long each screen was looked at, which is an Android usage feature.
 *
 * Empty is truthful rather than convenient: nothing on desktop collects these, so there are no stats
 * to report - as opposed to stats existing and being withheld. The screen shows an empty list, which
 * is what "nothing recorded" should look like.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopActivityStatsProvider @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ActivityStatsProvider {

    override fun getActivityStats(): List<ActivityStats> = emptyList()

    override fun reset() {
        aapsLogger.debug(LTag.CORE, "No activity stats are collected on desktop; nothing to reset")
    }
}
