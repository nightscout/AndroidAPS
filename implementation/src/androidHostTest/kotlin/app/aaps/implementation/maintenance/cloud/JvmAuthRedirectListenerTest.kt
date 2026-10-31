package app.aaps.implementation.maintenance.cloud

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket

/**
 * The JVM half of catching an OAuth redirect, against real sockets.
 *
 * The same cases the iOS listener is checked for, because the two have to behave alike: a user who
 * exports on a phone and imports on an iPhone meets both. Where they differ - POSIX sockets against
 * `ServerSocket` - is exactly where a difference in behaviour could hide, so neither is tested only
 * by reading it.
 */
class JvmAuthRedirectListenerTest : TestBase() {

    private val sut by lazy { JvmAuthRedirectListener(aapsLogger) }
    private var port = 19080

    @AfterEach fun stop() = sut.stop()

    /** Sends one raw request to the loopback port, the way the browser would. */
    private fun requestLoopback(port: Int, requestLine: String): Boolean =
        try {
            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.getOutputStream().write("$requestLine\r\nHost: localhost\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                true
            }
        } catch (_: Exception) {
            false
        }

    @Test
    fun `the app can listen on loopback`() {
        assertThat(sut.start(port++)).isTrue()
    }

    @Test
    fun `a redirect is caught and its code read`() = runTest {
        val p = port++
        assertThat(sut.start(p)).isTrue()

        val waiting = async { sut.awaitCallback("the-state", 5_000) }
        assertThat(requestLoopback(p, "GET /oauth/callback?code=the-code&state=the-state HTTP/1.1")).isTrue()

        val result = waiting.await()
        assertThat(result).isInstanceOf(OAuthCallback.Result.Code::class.java)
        assertThat((result as OAuthCallback.Result.Code).code).isEqualTo("the-code")
    }

    /**
     * A loopback listener accepts a connection from anything else on the machine, so the code alone
     * is not evidence this app asked for it. The state is what makes it evidence.
     */
    @Test
    fun `a callback whose state does not match is ignored`() = runTest {
        val p = port++
        assertThat(sut.start(p)).isTrue()

        val waiting = async { sut.awaitCallback("the-state", 3_000) }
        assertThat(requestLoopback(p, "GET /oauth/callback?code=forged&state=somebody-else HTTP/1.1")).isTrue()
        assertThat(requestLoopback(p, "GET /oauth/callback?code=ours&state=the-state HTTP/1.1")).isTrue()

        assertThat((waiting.await() as OAuthCallback.Result.Code).code).isEqualTo("ours")
    }

    /** A browser asks for the favicon first, and that must not end the wait. */
    @Test
    fun `another request does not end the wait`() = runTest {
        val p = port++
        assertThat(sut.start(p)).isTrue()

        val waiting = async { sut.awaitCallback("the-state", 5_000) }
        assertThat(requestLoopback(p, "GET /favicon.ico HTTP/1.1")).isTrue()
        assertThat(requestLoopback(p, "GET /oauth/callback?code=after&state=the-state HTTP/1.1")).isTrue()

        assertThat((waiting.await() as OAuthCallback.Result.Code).code).isEqualTo("after")
    }

    @Test
    fun `a refusal comes back as a refusal`() = runTest {
        val p = port++
        assertThat(sut.start(p)).isTrue()

        val waiting = async { sut.awaitCallback("the-state", 5_000) }
        assertThat(requestLoopback(p, "GET /oauth/callback?error=access_denied HTTP/1.1")).isTrue()

        assertThat(waiting.await()).isInstanceOf(OAuthCallback.Result.Denied::class.java)
    }

    /** A user who closed the browser without finishing is a timeout, not a hang. */
    @Test
    fun `nothing arriving is a timeout`() = runTest {
        assertThat(sut.start(port++)).isTrue()

        assertThat(sut.awaitCallback("the-state", 500)).isNull()
    }

    /**
     * The port has to come back, or a second sign in in one session cannot start - and a well known
     * port left open keeps accepting anything with a code on it.
     */
    @Test
    fun `the port is free again after a wait ends`() = runTest {
        val p = port++
        assertThat(sut.start(p)).isTrue()
        sut.awaitCallback("the-state", 300)

        assertThat(sut.start(p)).isTrue()
    }

    @Test
    fun `the port is free again after stopping`() {
        val p = port++
        assertThat(sut.start(p)).isTrue()
        sut.stop()

        assertThat(sut.start(p)).isTrue()
    }
}
