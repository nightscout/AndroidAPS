package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.posix.arc4random_uniform

/**
 * Checks the clock against a time server on iOS, as `JvmSntpClient` does on Android and the desktop.
 *
 * This matters more than it looks. The objectives check their minimum durations against this time,
 * which is what stops a user unlocking closed loop and SMB by moving the phone clock forward. And
 * until it worked, iOS could not start objective 1 at all, so the loop was always refused with
 * "Objective 1 not started".
 *
 * The same server as on Android ([LocaleDependentSetting.ntpServer]) and the same arithmetic: the
 * shared [SntpPacket], unchanged. Only the exchange on the network is iOS code, in [SntpTransport].
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosSntpClient(
    private val aapsLogger: AAPSLogger,
    private val localeDependentSetting: LocaleDependentSetting,
    private val transport: SntpTransport
) : SntpClient {

    override suspend fun ntpTime(isConnected: Boolean): SntpClient.NtpResult {
        if (!isConnected) return SntpClient.NtpResult(success = false, networkConnected = false, time = 0)

        val host = localeDependentSetting.ntpServer
        aapsLogger.debug(LTag.CORE, "Time detection started with $host")
        val exchange = transport.exchange(host, SntpPacket.NTP_PORT, TIMEOUT_MS) { requestTime ->
            // The protocol wants random data in the last byte. Android uses SecureRandom for it.
            SntpPacket.buildRequest(requestTime, arc4random_uniform(256u).toInt())
        }
        if (exchange == null) {
            aapsLogger.warn(LTag.CORE, "No answer from time server $host")
            return SntpClient.NtpResult(success = false, networkConnected = true, time = 0)
        }

        val timing = SntpPacket.parseResponse(exchange.response, exchange.requestTime, exchange.requestTicks, exchange.responseTicks)
        // The server time at the answer, moved on by the time since then, as JvmSntpClient does.
        val time = timing.ntpTime + monotonicMillis() - timing.ntpTimeReference
        aapsLogger.debug(LTag.CORE, "Time detection ended: $time, round trip ${timing.roundTripTime} ms")
        return SntpClient.NtpResult(success = true, networkConnected = true, time = time)
    }

    companion object {

        /** How long to wait for the answer, the same 5 s as on Android. */
        const val TIMEOUT_MS = 5_000L
    }
}
