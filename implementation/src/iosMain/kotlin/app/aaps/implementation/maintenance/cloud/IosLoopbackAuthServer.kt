package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.POLLIN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_REUSEADDR
import platform.posix.timeval
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.uint32_tVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/**
 * The local listener that catches the end of a Google sign in.
 *
 * ## Why a socket rather than a custom URL scheme
 *
 * The flow AAPS already uses is the loopback redirect: the browser is sent to
 * `http://localhost:8080/oauth/callback`, and the app answers that request itself. Android does the
 * same thing with a `ServerSocket`, so keeping it means the same client id, the same redirect and
 * the same registration in Google's console work for both. A custom URL scheme would need a new
 * iOS client id registered against the bundle, and would leave two flows to keep in step.
 *
 * ## The two things this depends on
 *
 * **The app stays in front.** iOS suspends a backgrounded app within a few seconds, and a suspended
 * app is not accepting connections. So the sign in has to be shown in a browser presented *over*
 * AAPS - `SFSafariViewController` or `ASWebAuthenticationSession` - and not by handing the URL to
 * Safari, which would switch away from AAPS and let it be suspended before Google redirects back.
 * See `IosUrlOpener`, which is the wrong opener for this one job.
 *
 * **The bind is loopback only.** `INADDR_LOOPBACK`, never `INADDR_ANY`. Listening on all interfaces
 * asks iOS for the local network permission, which shows the user a prompt about finding devices on
 * their network - for a sign in that never leaves the phone. Loopback needs no permission.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLoopbackAuthServer(private val aapsLogger: AAPSLogger) {

    private var listenSocket: Int = -1

    /**
     * Starts listening, and says whether it managed to.
     *
     * A port already in use is the ordinary failure - a previous sign in that was not closed - and
     * is reported rather than thrown, so the caller can tell the user to try again.
     */
    fun start(port: Int): Boolean {
        stop()
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) {
            aapsLogger.error(LTag.CORE, "$TAG could not make a socket")
            return false
        }

        memScoped {
            val reuse = alloc<uint32_tVar>()
            reuse.value = 1u
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<uint32_tVar>().convert())

            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            // Network byte order by hand. `htons` and `htonl` are C macros rather than functions, so
            // Kotlin/Native does not expose them; the swap is two lines and needs no interop.
            address.sin_port = (((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)).convert()
            // Loopback only. See the class docs: INADDR_ANY would ask the user for a permission
            // this does not need. 127.0.0.1 big endian is 0x0100007F.
            address.sin_addr.s_addr = LOOPBACK_BIG_ENDIAN

            if (bind(fd, address.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) != 0) {
                aapsLogger.error(LTag.CORE, "$TAG could not bind port $port, it may still be held by an earlier sign in")
                close(fd)
                return false
            }
        }

        // A backlog of more than one on purpose: a browser opens several connections around a
        // redirect - the favicon, a prefetch - and with a backlog of one the redirect itself can be
        // the connection that gets refused. A test caught exactly that.
        if (listen(fd, BACKLOG) != 0) {
            aapsLogger.error(LTag.CORE, "$TAG could not listen on port $port")
            close(fd)
            return false
        }

        listenSocket = fd
        aapsLogger.debug(LTag.CORE, "$TAG listening on 127.0.0.1:$port")
        return true
    }

    /**
     * Waits for the browser to come back, up to [timeoutMs], and hands over only a callback that
     * proves it is ours.
     *
     * Requests that are not the callback are answered and waited past, because a browser will happily
     * ask for `/favicon.ico` first and that must not end the wait.
     *
     * @param expectedState the `state` sent to the provider. **Required, and compared here.** A
     *   loopback listener accepts a connection from anything else running on the device, so the code
     *   in the request is not by itself evidence that this app asked for it - `state` is what makes
     *   it evidence, and PKCE does not replace that. It used to be parsed, carried and left for "the
     *   caller" to check, with no caller in the tree to do it.
     * @return the callback, or null when nothing came in time - which is also what a user who closed
     *   the browser without finishing looks like. A callback whose state does not match is not
     *   returned: the browser is told the sign in failed and the wait carries on, because the real
     *   one may still be coming.
     */
    suspend fun awaitCallback(expectedState: String, timeoutMs: Long): OAuthCallback.Result? = withContext(Dispatchers.IO) {
        val listening = listenSocket
        if (listening < 0) return@withContext null

        // Closed however this ends - answered, timed out, failed or cancelled. Leaving it open left a
        // fixed, well known port listening for the life of the app, still accepting anything with a
        // `code` on it, and was also why a second sign in found the port already held.
        try {
            var remaining = timeoutMs
            while (remaining > 0) {
                val slice = if (remaining > POLL_SLICE_MS) POLL_SLICE_MS else remaining
                val ready = memScoped {
                    val descriptor = alloc<pollfd>()
                    descriptor.fd = listening
                    descriptor.events = POLLIN.toShort()
                    descriptor.revents = 0
                    poll(descriptor.ptr, 1.convert(), slice.toInt())
                }
                if (ready < 0) {
                    aapsLogger.error(LTag.CORE, "$TAG stopped waiting, poll failed")
                    return@withContext null
                }
                if (ready > 0) {
                    val result = acceptOne(expectedState)
                    // Not the callback - a favicon, say - or a callback that was not ours. Either way
                    // the real one may still arrive, so keep waiting rather than failing the sign in.
                    if (result != null && result != OAuthCallback.Result.NotTheCallback) return@withContext result
                }
                remaining -= slice
            }
            aapsLogger.debug(LTag.CORE, "$TAG gave up waiting for the browser")
            null
        } finally {
            stop()
        }
    }

    private fun acceptOne(expectedState: String): OAuthCallback.Result? {
        val client = memScoped {
            val length = alloc<socklen_tVar>()
            length.value = sizeOf<sockaddr_in>().convert()
            val from = alloc<sockaddr_in>()
            accept(listenSocket, from.ptr.reinterpret<sockaddr>(), length.ptr)
        }
        if (client < 0) return null

        // A read deadline on the accepted socket, which the poll loop above does not cover: poll
        // watches the *listening* socket, so once a connection is accepted the wait below is outside
        // every timeout this class has. Without it any local process could open a connection, send
        // nothing, and park the sign in for ever - the documented timeout would never fire.
        memScoped {
            val deadline = alloc<timeval>()
            deadline.tv_sec = READ_TIMEOUT_SECONDS.convert()
            deadline.tv_usec = 0.convert()
            setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, deadline.ptr, sizeOf<timeval>().convert())
        }

        val requestLine = memScoped {
            val buffer = allocArray<ByteVar>(REQUEST_BUFFER)
            val read = recv(client, buffer, (REQUEST_BUFFER - 1).convert(), 0)
            // readBytes rather than null terminating and reading back: the request is bytes, and
            // only the first line is wanted from them.
            if (read <= 0) null else buffer.readBytes(read.toInt()).decodeToString().substringBefore('\r').substringBefore('\n')
        }

        if (requestLine == null) {
            close(client)
            return null
        }

        val parsed = OAuthCallback.parseRequestLine(requestLine)
        // The state check, and the reason this method takes the expected value at all. A code from a
        // request this app did not start is refused before it can be returned, and the browser is
        // told the sign in failed rather than being shown success for someone else's redirect.
        //
        // Refused as NotTheCallback rather than as an error, so the wait carries on. Ending it here
        // would let any local process cancel a sign in by sending one bogus code, which is a nastier
        // thing to hand over than the nuisance it looks like.
        val result = if (parsed is OAuthCallback.Result.Code && parsed.state != expectedState) {
            aapsLogger.error(LTag.CORE, "$TAG refused a callback whose state did not match the one sent")
            OAuthCallback.Result.NotTheCallback
        } else {
            parsed
        }
        val response = OAuthCallback.responseFor(result, SIGNED_IN, FAILED).encodeToByteArray()
        response.usePinned { pinned -> send(client, pinned.addressOf(0), response.size.convert(), 0) }
        close(client)
        return result
    }

    fun stop() {
        if (listenSocket >= 0) {
            close(listenSocket)
            listenSocket = -1
            aapsLogger.debug(LTag.CORE, "$TAG stopped listening")
        }
    }

    private companion object {

        private const val TAG = "IosLoopbackAuthServer:"
        private const val REQUEST_BUFFER = 4096
        private const val POLL_SLICE_MS = 250L

        /**
         * How long a connected client has to send its request line.
         *
         * The browser sends it immediately; this exists only so that something which connects and
         * then says nothing cannot hold the sign in open.
         */
        private const val READ_TIMEOUT_SECONDS = 5
        private const val BACKLOG = 8
        private const val LOOPBACK_BIG_ENDIAN = 0x0100007Fu

        // Shown in the browser, not in AAPS, so they are not translated with the app's strings.
        private const val SIGNED_IN = "AAPS is signed in. You can close this page."
        private const val FAILED = "AAPS could not complete the sign in."
    }
}
