package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.send
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proof that an iOS app can catch its own OAuth redirect.
 *
 * This is the question the whole Google Drive port rests on. Android finishes a sign in by listening
 * on `localhost:8080` and letting the browser redirect to it; if an iOS app cannot do the same, the
 * flow needs a custom URL scheme, a new client id registered against the bundle, and two auth paths
 * to keep in step. So rather than assume either answer, these bind a real socket on a real simulator
 * and connect to it.
 *
 * What they do not prove is the browser half - that a page loaded in `SFSafariViewController` reaches
 * this listener while AAPS is in front. That needs the app running and is checked there. What is
 * settled here is the part that would have been the blocker: the bind, the accept and the reply.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLoopbackAuthServerTest {

    private class SilentLogger : AAPSLogger {
        val errors = mutableListOf<String>()
        override fun debug(tag: LTag, message: String) {}
        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) { errors.add(message) }
        override fun error(tag: LTag, message: String, throwable: Throwable) { errors.add(message) }
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private val logger = SilentLogger()
    private val server = IosLoopbackAuthServer(logger)

    // A port of its own per test, so one that is slow to release cannot fail the next.
    private var nextPort = 18080

    @AfterTest
    fun stop() = server.stop()

    /** Sends one raw HTTP request line to 127.0.0.1, the way the browser would. */
    private fun requestLoopback(port: Int, requestLine: String): Boolean {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return false
        val connected = memScoped {
            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            address.sin_port = (((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)).convert()
            address.sin_addr.s_addr = 0x0100007Fu
            connect(fd, address.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) == 0
        }
        if (!connected) {
            close(fd)
            return false
        }
        val bytes = "$requestLine\r\nHost: localhost\r\n\r\n".encodeToByteArray()
        bytes.usePinned { send(fd, it.addressOf(0), bytes.size.convert(), 0) }
        close(fd)
        return true
    }

    @Test
    fun `the app can listen on loopback`() {
        assertTrue(server.start(nextPort++), "binding 127.0.0.1 failed: ${logger.errors}")
    }

    /** The whole point: a redirect arrives and the code comes out of it. */
    @Test
    fun `a redirect to loopback is caught and its code read`() = runTest {
        val port = nextPort++
        assertTrue(server.start(port))

        val waiting = async { server.awaitCallback(timeoutMs = 5_000) }
        assertTrue(requestLoopback(port, "GET /oauth/callback?code=the-code&state=xyz HTTP/1.1"))

        val result = assertIs<OAuthCallback.Result.Code>(waiting.await())
        assertEquals("the-code", result.code)
        assertEquals("xyz", result.state)
    }

    /** A browser asks for the favicon first. That must not be taken as the answer. */
    @Test
    fun `another request does not end the wait`() = runTest {
        val port = nextPort++
        assertTrue(server.start(port))

        val waiting = async { server.awaitCallback(timeoutMs = 5_000) }
        assertTrue(requestLoopback(port, "GET /favicon.ico HTTP/1.1"))
        assertTrue(requestLoopback(port, "GET /oauth/callback?code=after-the-favicon HTTP/1.1"))

        assertEquals("after-the-favicon", assertIs<OAuthCallback.Result.Code>(waiting.await()).code)
    }

    /** A user who closes the browser without finishing is a timeout, not a hang. */
    @Test
    fun `nothing arriving is a timeout and not a hang`() = runTest {
        assertTrue(server.start(nextPort++))

        assertNull(server.awaitCallback(timeoutMs = 500))
    }

    @Test
    fun `a refusal comes back as a refusal`() = runTest {
        val port = nextPort++
        assertTrue(server.start(port))

        val waiting = async { server.awaitCallback(timeoutMs = 5_000) }
        assertTrue(requestLoopback(port, "GET /oauth/callback?error=access_denied HTTP/1.1"))

        assertEquals("access_denied", assertIs<OAuthCallback.Result.Denied>(waiting.await()).error)
    }

    /** Stopping has to free the port, or a second sign in in one session cannot start. */
    @Test
    fun `the port is free again after stopping`() {
        val port = nextPort++
        assertTrue(server.start(port))
        server.stop()

        assertTrue(server.start(port), "the port was not released: ${logger.errors}")
    }
}
