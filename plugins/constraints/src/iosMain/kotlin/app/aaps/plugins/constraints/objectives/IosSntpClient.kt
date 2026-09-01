package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No network time check on iOS yet.
 *
 * Written from the Windows side so that moving the objectives to commonMain did not leave the iOS
 * graph without a binding. **It needs a real implementation**, more than most placeholders do: this
 * is the reference AAPS validates its own timestamps against, and every treatment it records is
 * stamped with the device clock. A phone whose clock is wrong writes history that cannot be
 * reconciled with a pump or with Nightscout.
 *
 * Reporting `success = false` with `networkConnected` as given is the safe direction - the objective
 * shows that the clock could not be checked, rather than that it was checked and found correct.
 *
 * The Android and desktop implementation is `JvmSntpClient`, sharing the SNTP exchange over
 * `DatagramSocket`. The protocol is small; on Apple it wants `NWConnection` or a POSIX socket.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSntpClient @Inject constructor(
    private val aapsLogger: AAPSLogger
) : SntpClient {

    override suspend fun ntpTime(isConnected: Boolean): SntpClient.NtpResult {
        aapsLogger.error(LTag.CORE, "Cannot check the clock against a time server on iOS yet")
        return SntpClient.NtpResult(success = false, networkConnected = isConnected, time = 0)
    }
}
