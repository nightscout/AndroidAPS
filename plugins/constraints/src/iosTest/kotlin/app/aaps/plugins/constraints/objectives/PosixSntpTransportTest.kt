package app.aaps.plugins.constraints.objectives

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import platform.posix.AF_INET
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.bind
import platform.posix.getsockname
import platform.posix.recvfrom
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import platform.posix.close as closeSocket

/**
 * The real exchange on a real socket, against a UDP server on 127.0.0.1 that the test runs itself. So
 * it needs no internet and no time server, and still sends and receives real datagrams - which is the
 * part a reading of the code cannot prove. A first version built on Apple's Network framework failed
 * here before it ever reached a phone: see [PosixSntpTransport].
 */
@OptIn(ExperimentalForeignApi::class)
class PosixSntpTransportTest {

    @Test
    fun `an answer comes back with the clock readings in order`() = runBlocking<Unit> {
        LocalUdpServer().use { server ->
            val received = async(Dispatchers.Default) { server.answerOnce { request -> serverAnswer(request, SERVER_TIME) } }
            var sent = ByteArray(0)
            val before = Clock.System.now().toEpochMilliseconds()

            val exchange = PosixSntpTransport().exchange("127.0.0.1", server.port, 5_000) { requestTime ->
                SntpPacket.buildRequest(requestTime, 7).also { sent = it }
            }

            val after = Clock.System.now().toEpochMilliseconds()
            assertNotNull(exchange)
            // The server got exactly the packet that was built, and the answer is the server's.
            assertContentEquals(sent, received.await())
            assertEquals(SERVER_TIME, SntpPacket.readTimeStamp(exchange.response, TRANSMIT_OFFSET))
            assertTrue(exchange.requestTime in before..after, "request time ${exchange.requestTime}")
            assertTrue(exchange.requestTicks <= exchange.responseTicks)
        }
    }

    @Test
    fun `no answer within the limit gives null`() = runBlocking<Unit> {
        LocalUdpServer().use { server ->
            // Reads the request and sends nothing back.
            val received = async(Dispatchers.Default) { server.answerOnce { null } }
            val start = monotonicMillis()

            val exchange = PosixSntpTransport().exchange("127.0.0.1", server.port, 500) { SntpPacket.buildRequest(it, 7) }

            assertNull(exchange)
            assertTrue(monotonicMillis() - start < 3_000, "took ${monotonicMillis() - start} ms")
            assertNotNull(received.await())
        }
    }

    /** A short answer would be read past its end by the arithmetic. */
    @Test
    fun `a short answer counts as no answer`() = runBlocking<Unit> {
        LocalUdpServer().use { server ->
            val received = async(Dispatchers.Default) { server.answerOnce { ByteArray(10) } }

            val exchange = PosixSntpTransport().exchange("127.0.0.1", server.port, 2_000) { SntpPacket.buildRequest(it, 7) }

            assertNull(exchange)
            assertNotNull(received.await())
        }
    }

    @Test
    fun `a server that cannot be found gives null`() = runBlocking<Unit> {
        val start = monotonicMillis()

        val exchange = PosixSntpTransport().exchange("aaps-sntp-test.invalid", 123, 2_000) { SntpPacket.buildRequest(it, 7) }

        assertNull(exchange)
        assertTrue(monotonicMillis() - start < 5_000, "took ${monotonicMillis() - start} ms")
    }

    /** One UDP socket on 127.0.0.1 with a port the system picks. */
    private class LocalUdpServer : AutoCloseable {

        private val fd = socket(AF_INET, SOCK_DGRAM, 0).also { check(it >= 0) { "No socket" } }

        val port: Int = memScoped {
            val address = alloc<sockaddr_in>()
            address.sin_len = sizeOf<sockaddr_in>().toUByte()
            address.sin_family = AF_INET.toUByte()
            address.sin_port = 0u
            address.sin_addr.s_addr = LOOPBACK_NETWORK_ORDER
            check(bind(fd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0) { "bind failed" }
            val length = alloc<socklen_tVar>()
            length.value = sizeOf<sockaddr_in>().toUInt()
            check(getsockname(fd, address.ptr.reinterpret(), length.ptr) == 0) { "getsockname failed" }
            // So a test that sends nothing cannot leave this socket waiting for ever.
            val timeout = alloc<timeval>()
            timeout.tv_sec = 5
            timeout.tv_usec = 0
            check(setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().toUInt()) == 0) { "setsockopt failed" }
            fromNetworkOrder(address.sin_port)
        }

        /**
         * Waits for one datagram and sends back what [reply] makes of it, if anything. Returns what was
         * received, or null when nothing came within the socket's 5 s.
         */
        fun answerOnce(reply: (request: ByteArray) -> ByteArray?): ByteArray? = memScoped {
            val buffer = ByteArray(512)
            val from = alloc<sockaddr_in>()
            val fromLength = alloc<socklen_tVar>()
            fromLength.value = sizeOf<sockaddr_in>().toUInt()
            val count = buffer.usePinned { recvfrom(fd, it.addressOf(0), buffer.size.toULong(), 0, from.ptr.reinterpret(), fromLength.ptr) }
            if (count <= 0) return@memScoped null
            val request = buffer.copyOf(count.toInt())
            reply(request)?.let { answer ->
                answer.usePinned { sendto(fd, it.addressOf(0), answer.size.toULong(), 0, from.ptr.reinterpret(), fromLength.value) }
            }
            request
        }

        override fun close() {
            closeSocket(fd)
        }
    }

    private companion object {

        /** 2026-11-19, a fixed time so the answer can be recognised. */
        const val SERVER_TIME = 1_795_124_000_000L

        /** 127.0.0.1 as `s_addr` holds it: network byte order, read by a little-endian CPU. */
        const val LOOPBACK_NETWORK_ORDER = 0x0100007Fu

        /** A port as `sin_port` holds it, in network byte order, turned into a number. */
        fun fromNetworkOrder(port: UShort): Int = ((port.toInt() and 0xFF) shl 8) or ((port.toInt() shr 8) and 0xFF)
    }
}
