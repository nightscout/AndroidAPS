package app.aaps.plugins.constraints.objectives

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.AF_UNSPEC
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.addrinfo
import platform.posix.connect
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.timeval
import kotlin.time.Clock
import platform.posix.close as closeSocket

/**
 * SNTP over a plain UDP socket, the same shape as `JvmSntpClient`: look the name up, send one datagram,
 * wait for one answer with a time limit.
 *
 * ## Why not Apple's Network framework
 *
 * Apple recommends `nw_connection` over BSD sockets, and this was written with it first. Kotlin/Native
 * cannot use it safely:
 * - `NW_PARAMETERS_DISABLE_PROTOCOL` is a marker block that the framework recognises by identity.
 *   Kotlin/Native passes it on as a new block, so DTLS was not switched off and a DTLS hello went to
 *   the time server instead of the request.
 * - Reading `NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT` from Kotlin stops the process with "Converting
 *   Obj-C blocks with non-reference-typed return value to kotlin.Any is not supported".
 * - The `nw_error_t` that every handler gets on a failure stops the process the same way, before any
 *   Kotlin code runs. So no DNS or no route would have killed the app instead of failing the check.
 *
 * A small C wrapper compiled through cinterop could hide all three. Nothing in the project uses
 * cinterop yet, so it was not added for this.
 *
 * ## What a BSD socket costs
 *
 * Apple says a BSD socket does not bring up the cellular radio or a VPN on demand by itself. On a phone
 * with only mobile data and the radio asleep, the first check may fail. It fails visibly - the
 * objectives screen says it could not get the time - and a second try usually works, because the name
 * lookup has woken the radio by then.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PosixSntpTransport : SntpTransport {

    // The calls below block, as `DatagramSocket` does on Android, so they run off the main thread.
    override suspend fun exchange(host: String, port: Int, timeoutMs: Long, buildRequest: (requestTime: Long) -> ByteArray): SntpExchange? =
        withContext(Dispatchers.IO) { exchangeBlocking(host, port, timeoutMs, buildRequest) }

    private fun exchangeBlocking(host: String, port: Int, timeoutMs: Long, buildRequest: (requestTime: Long) -> ByteArray): SntpExchange? = memScoped {
        val hints = alloc<addrinfo>()
        // IPv4 or IPv6, whichever the name has. A phone on an IPv6 only network has no IPv4 route.
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_DGRAM
        val found = allocPointerTo<addrinfo>()
        // Not limited in time, as on Android: InetAddress.getByName has no limit either.
        if (getaddrinfo(host, port.toString(), hints.ptr, found.ptr) != 0) return@memScoped null
        try {
            var address = found.value
            while (address != null) {
                val entry = address.pointed
                val fd = socket(entry.ai_family, entry.ai_socktype, entry.ai_protocol)
                if (fd >= 0) {
                    try {
                        // A UDP connect sends nothing. It fails at once when there is no route to this
                        // address, and then the next one is tried. After it, only the server can answer.
                        if (connect(fd, entry.ai_addr, entry.ai_addrlen) == 0)
                            return@memScoped requestAndWait(fd, timeoutMs, buildRequest)
                    } finally {
                        closeSocket(fd)
                    }
                }
                address = entry.ai_next
            }
            null
        } finally {
            freeaddrinfo(found.value)
        }
    }

    /** Sends the request on the connected socket [fd] and waits up to [timeoutMs] for the answer. */
    private fun requestAndWait(fd: Int, timeoutMs: Long, buildRequest: (requestTime: Long) -> ByteArray): SntpExchange? = memScoped {
        val timeout = alloc<timeval>()
        timeout.tv_sec = timeoutMs / 1000
        timeout.tv_usec = ((timeoutMs % 1000) * 1000).toInt()
        if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().toUInt()) != 0) return@memScoped null

        // Both clocks are read together, right before the request goes out, as on Android.
        val requestTime = Clock.System.now().toEpochMilliseconds()
        val requestTicks = monotonicMillis()
        val request = buildRequest(requestTime)
        val sent = request.usePinned { send(fd, it.addressOf(0), request.size.toULong(), 0) }
        if (sent != request.size.toLong()) return@memScoped null

        // Larger than a packet, so an answer that is too long is not cut down to look right.
        val buffer = ByteArray(512)
        val received = buffer.usePinned { recv(fd, it.addressOf(0), buffer.size.toULong(), 0) }
        val responseTicks = monotonicMillis()
        // Nothing within the limit, or an error. A short answer would be read past its end.
        if (received < SntpPacket.NTP_PACKET_SIZE) return@memScoped null
        SntpExchange(buffer.copyOf(received.toInt()), requestTime, requestTicks, responseTicks)
    }
}
