package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/**
 * [AuthRedirectListener] on the JVM, which is Android and the desktop.
 *
 * The counterpart of the iOS listener, and deliberately the same shape: bind loopback, wait for the
 * browser, check the state, answer, close. Both had to be written because a socket is one of the few
 * things Kotlin cannot share, but only the socket differs - the parsing and the decision about what
 * a redirect means are [OAuthCallback], which both use.
 *
 * Binds to the loopback address by name rather than to every interface. On Android that keeps the
 * listener off the local network; on a desktop it keeps a firewall quiet. Nothing outside the
 * machine has any business reaching a sign in that ends on this one.
 */
class JvmAuthRedirectListener(private val aapsLogger: AAPSLogger) : AuthRedirectListener {

    private var server: ServerSocket? = null

    override fun start(port: Int): Boolean {
        stop()
        return try {
            server = ServerSocket(port, BACKLOG, InetAddress.getByName(LOOPBACK)).apply {
                soTimeout = ACCEPT_TIMEOUT_MS
            }
            aapsLogger.debug(LTag.CORE, "$TAG listening on $LOOPBACK:$port")
            true
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "$TAG could not bind port $port, it may still be held by an earlier sign in: ${e.message}")
            false
        }
    }

    override suspend fun awaitCallback(expectedState: String, timeoutMs: Long): OAuthCallback.Result? =
        withContext(Dispatchers.IO) {
            val listening = server ?: return@withContext null
            val deadline = System.currentTimeMillis() + timeoutMs
            try {
                while (System.currentTimeMillis() < deadline && !listening.isClosed) {
                    val result = acceptOne(listening) ?: continue
                    // Anything else that reached the port - a favicon, or another app - is answered
                    // and waited past, because the real redirect may still be coming.
                    if (result is OAuthCallback.Result.Code && result.state != expectedState) {
                        aapsLogger.warn(LTag.CORE, "$TAG ignored a callback whose state did not match")
                        continue
                    }
                    if (result !is OAuthCallback.Result.NotTheCallback) return@withContext result
                }
                aapsLogger.debug(LTag.CORE, "$TAG gave up waiting for the browser")
                null
            } finally {
                // Closed however this ends. A well known port left open keeps accepting anything that
                // arrives with a code on it, and holds the port against the next sign in.
                stop()
            }
        }

    private fun acceptOne(listening: ServerSocket): OAuthCallback.Result? =
        try {
            listening.accept().use { socket ->
                socket.soTimeout = READ_TIMEOUT_MS
                val requestLine = socket.getInputStream().bufferedReader().readLine()
                val result = if (requestLine == null) OAuthCallback.Result.Malformed
                else OAuthCallback.parseRequestLine(requestLine)
                socket.getOutputStream().write(OAuthCallback.responseFor(result, SIGNED_IN, FAILED).toByteArray())
                result
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            if (!listening.isClosed) aapsLogger.error(LTag.CORE, "$TAG could not read the callback: ${e.message}")
            null
        }

    override fun stop() {
        server?.let { open ->
            runCatching { open.close() }
            aapsLogger.debug(LTag.CORE, "$TAG stopped listening")
        }
        server = null
    }

    private companion object {

        private const val TAG = "JvmAuthRedirectListener:"
        private const val LOOPBACK = "127.0.0.1"

        /** A browser opens several connections around a redirect; one would refuse the rest. */
        private const val BACKLOG = 8
        private const val ACCEPT_TIMEOUT_MS = 250
        private const val READ_TIMEOUT_MS = 2_000

        // Shown in the browser rather than in AAPS, so not translated with the app's strings.
        private const val SIGNED_IN = "AAPS is signed in. You can close this page."
        private const val FAILED = "AAPS could not complete the sign in."
    }
}
