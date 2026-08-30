package app.aaps.plugins.constraints.objectives

import android.os.SystemClock
import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

/**
 * {@hide}
 *
 *
 * Simple SNTP client class for retrieving network time.
 *
 *
 * Sample usage:
 * <pre>SntpClient client = new SntpClient();
 * if (client.requestTime("time.foo.com")) {
 * long now = client.getNtpTime() + SystemClock.elapsedRealtime() - client.getNtpTimeReference();
 * }
</pre> *
 */
@SingleIn(AppScope::class)
@OpenForTesting
class SntpClient @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val localeDependentSetting: LocaleDependentSetting
) {

    /**
     * Returns the time computed from the NTP transaction.
     *
     * @return time value computed from NTP server response.
     */
    // system time computed from NTP server response
    protected var ntpTime: Long = 0

    /**
     * Returns the reference clock value (value of SystemClock.elapsedRealtime())
     * corresponding to the NTP time.
     *
     * @return reference clock corresponding to the NTP time.
     */
    // value of SystemClock.elapsedRealtime() corresponding to mNtpTime
    protected var ntpTimeReference: Long = 0

    /**
     * Returns the round trip time of the NTP transaction
     *
     * @return round trip time in milliseconds.
     */
    // round trip time in milliseconds
    private var roundTripTime: Long = 0

    data class NtpResult(
        val success: Boolean,
        val networkConnected: Boolean,
        val time: Long
    )

    /**
     * Suspend version of ntpTime for use in coroutines.
     */
    suspend fun ntpTime(isConnected: Boolean): NtpResult = withContext(Dispatchers.IO) {
        if (!isConnected) {
            NtpResult(success = false, networkConnected = false, time = 0)
        } else {
            aapsLogger.debug("Time detection started")
            val success = requestTime(localeDependentSetting.ntpServer, 5000)
            val time = ntpTime + SystemClock.elapsedRealtime() - ntpTimeReference
            aapsLogger.debug("Time detection ended: $success ${dateUtil.dateAndTimeString(ntpTime)}")
            NtpResult(success = success, networkConnected = true, time = time)
        }
    }

    /**
     * Sends an SNTP request to the given host and processes the response.
     *
     * @param host    host name of the server.
     * @param timeout network timeout in milliseconds.
     * @return true if the transaction was successful.
     */
    @Suppress("SameParameterValue")
    @Synchronized protected fun requestTime(host: String, timeout: Int): Boolean {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = timeout
            val address = InetAddress.getByName(host)

            // Read both clocks together: the wall clock goes into the packet, the monotonic one is
            // what the latency correction is measured against.
            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            val buffer = SntpPacket.buildRequest(requestTime, SecureRandom().nextInt(256))
            socket.send(DatagramPacket(buffer, buffer.size, address, SntpPacket.NTP_PORT))

            // The response is read back into the same array the request was sent from, as before.
            socket.receive(DatagramPacket(buffer, buffer.size))
            val responseTicks = SystemClock.elapsedRealtime()
            socket.close()

            val timing = SntpPacket.parseResponse(buffer, requestTime, requestTicks, responseTicks)
            ntpTime = timing.ntpTime
            ntpTimeReference = timing.ntpTimeReference
            this.roundTripTime = timing.roundTripTime
        } catch (e: Exception) {
            aapsLogger.debug("request time failed: $e")
            return false
        }
        return true
    }
}