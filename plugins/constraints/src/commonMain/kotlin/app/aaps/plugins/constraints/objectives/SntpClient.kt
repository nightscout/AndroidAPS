package app.aaps.plugins.constraints.objectives

/**
 * Asks a time server what the time really is, so an objective can check the device clock.
 *
 * The clock matters more here than it looks: AAPS timestamps every treatment, and a device whose
 * clock is wrong writes history that cannot be reconciled with a pump or with Nightscout. One of the
 * objectives verifies it, and this is how.
 *
 * ## Why this is an interface
 *
 * The objectives themselves are arithmetic and preference reads, and they are shared. The one part
 * that is not is talking to an NTP server: the Android implementation uses `DatagramSocket` and
 * `SystemClock.elapsedRealtime()`, neither of which exists on every platform. Lifting just that out
 * is what let `ObjectivesPlugin` and all eleven objectives move to commonMain.
 *
 * The sockets are plain JVM, so Android and a desktop could share the protocol code if the module
 * grew a shared JVM source set. That is worth doing when a desktop needs to verify its clock for
 * real; until then each platform answers for itself.
 */
interface SntpClient {

    /**
     * The network time, or a result saying why there is none.
     *
     * @param isConnected whether the caller believes there is a network. Passed in rather than
     *   checked here, because the caller already knows and a second answer could disagree with it.
     */
    suspend fun ntpTime(isConnected: Boolean): NtpResult

    /**
     * What the lookup found.
     *
     * [success] false with [networkConnected] true means the server did not answer; both false means
     * it was never asked. The two are kept apart so a screen can say which happened rather than
     * reporting a bad clock when there was simply no network.
     */
    data class NtpResult(
        val success: Boolean,
        val networkConnected: Boolean,
        val time: Long
    )
}
