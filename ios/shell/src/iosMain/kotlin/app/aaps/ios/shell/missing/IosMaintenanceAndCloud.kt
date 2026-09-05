package app.aaps.ios.shell.missing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/*
 * Maintenance and cloud export.
 *
 * Both sit on top of things iOS has no version of yet - the log directory and mail composer, and
 * Google Drive through its Android SDK - so both report "nothing here" rather than guessing.
 *
 * Screen usage statistics used to be here too. They moved to `platform` because a client is not
 * meant to collect them at all, which makes an empty answer correct rather than unfinished.
 */

/** Sending logs needs a log directory and a mail composer; neither is wired up on iOS. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosMaintenance @Inject constructor(
    private val aapsLogger: AAPSLogger
) : Maintenance {

    override suspend fun executeSendLogs(): ExportResult =
        aapsLogger.failNotOnIosYet("Maintenance.executeSendLogs")

    override fun deleteLogs(keep: Int) = aapsLogger.notOnIosYet("Maintenance.deleteLogs")
}
