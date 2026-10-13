package app.aaps.plugins.sync.garmin

import android.os.StrictMode
import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.pump.ThreadUtil
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.UncaughtExceptionHandler
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.concurrent.withLock

/** Basic HTTP server to communicate with Garmin device via localhost. */
class HttpServer internal constructor(private var aapsLogger: AAPSLogger, val port: Int) : Closeable {

    private val serverThread: Thread
    private val workerExecutor = Executors.newCachedThreadPool()
    private val endpoints: MutableMap<String, (SocketAddress, URI, String?) -> Pair<Int, CharSequence>> =
        ConcurrentHashMap()
    private var serverSocket: ServerSocket? = null
    private val readyLock = ReentrantLock()
    private val readyCond = readyLock.newCondition()

    init {
        serverThread = Thread { runServer() }
        serverThread.name = "GarminHttpServer"
        serverThread.isDaemon = true
        serverThread.uncaughtExceptionHandler = UncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            aapsLogger.error(LTag.GARMIN, "uncaught in HTTP server", e)
            serverSocket?.use {}
        }
        serverThread.start()
    }

    override fun close() {
        workerExecutor.shutdown()
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: IOException) {
        }
        try {
            serverThread.join(10_000L)
        } catch (_: InterruptedException) {
        }
    }

    /** Wait for the server to start listing to requests. */
    fun awaitReady(wait: Duration): Boolean {
        var waitNanos = wait.toNanos()
        readyLock.withLock {
            while (serverSocket?.isBound != true && waitNanos > 0L) {
                waitNanos = readyCond.awaitNanos(waitNanos)
            }
        }
        return serverSocket?.isBound == true
    }

    /** Register an endpoint (path) to handle requests. */
    fun registerEndpoint(path: String, endpoint: (SocketAddress, URI, String?) -> Pair<Int, CharSequence>) {
        aapsLogger.info(LTag.GARMIN, "Register: '$path'")
        endpoints[path] = endpoint
    }

    // @Suppress("all")
    private fun respond(
        @Suppress("SameParameterValue") code: Int,
        body: CharSequence,
        @Suppress("SameParameterValue") contentType: String,
        out: OutputStream
    ) {
        respond(code, body.toString().toByteArray(Charset.forName("UTF8")), contentType, out)
    }

    private fun respond(code: Int, out: OutputStream) {
        respond(code, null as ByteArray?, null, out)
    }

    private fun respond(code: Int, body: ByteArray?, contentType: String?, out: OutputStream) {
        val header = StringBuilder()
        header.append("HTTP/1.1 ").append(code).append(" OK\r\n")
        if (body != null) {
            appendHeader("Content-Length", "" + body.size, header)
        }
        if (contentType != null) {
            appendHeader("Content-Type", contentType, header)
        }
        header.append("\r\n")
        val bout = BufferedOutputStream(out)
        bout.write(header.toString().toByteArray(StandardCharsets.US_ASCII))
        if (body != null) {
            bout.write(body)
        }
        bout.flush()
    }

    private fun handleRequest(s: Socket) {
        val out = s.getOutputStream()
        try {
            val (uri, reqBody) = parseRequest(s.getInputStream())
            if ("favicon.ico" == uri.path) {
                respond(HttpURLConnection.HTTP_NOT_FOUND, out)
                return
            }
            val endpoint = endpoints[uri.path ?: ""]
            if (endpoint == null) {
                aapsLogger.error(LTag.GARMIN, "request path not found '" + uri.path + "'")
                respond(HttpURLConnection.HTTP_NOT_FOUND, out)
            } else {
                try {
                    val (code, body) = endpoint(s.remoteSocketAddress, uri, reqBody)
                    respond(code, body, "application/json", out)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.GARMIN, "endpoint " + uri.path + " failed", e)
                    respond(HttpURLConnection.HTTP_INTERNAL_ERROR, out)
                }
            }
        } catch (e: SocketTimeoutException) {
            // Client may just connect without sending anything.
            aapsLogger.debug(LTag.GARMIN, "socket timeout: " + e.message)
            return
        } catch (e: IOException) {
            aapsLogger.error(LTag.GARMIN, "Invalid request", e)
            respond(HttpURLConnection.HTTP_BAD_REQUEST, out)
            return
        }
    }

    private fun runServer() = try {
        // Policy won't work in unit tests, so ignore NULL builder.
        @Suppress("UNNECESSARY_SAFE_CALL")
        val policy = StrictMode.ThreadPolicy.Builder()?.permitAll()?.build()
        if (policy != null) StrictMode.setThreadPolicy(policy)
        readyLock.withLock {
            serverSocket = ServerSocket()
            serverSocket!!.bind(
                // Garmin will only connect to IP4 localhost. Therefore, we need to explicitly listen
                // on that loopback interface and cannot use InetAddress.getLoopbackAddress(). That
                // gives ::1 (IP6 localhost).
                InetSocketAddress(Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1)), port)
            )
            readyCond.signalAll()
        }
        aapsLogger.info(LTag.GARMIN, "accept connections on " + serverSocket!!.localSocketAddress)
        while (true) {
            val socket = serverSocket!!.accept()
            aapsLogger.info(LTag.GARMIN, "accept " + socket.remoteSocketAddress)
            workerExecutor.execute {
                Thread.currentThread().name = "worker" + ThreadUtil.threadId()
                try {
                    socket.use { s ->
                        s.soTimeout = 10_000
                        handleRequest(s)
                    }
                } catch (e: Exception) {
                    aapsLogger.error(LTag.GARMIN, "response failed", e)
                }
            }
        }
    } catch (e: IOException) {
        aapsLogger.error("Server crashed", e)
    } finally {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: IOException) {
            aapsLogger.error(LTag.GARMIN, "Socked close failed", e)
        }
    }

    companion object {

        private val REQUEST_HEADER = Pattern.compile("(GET|POST) (\\S*) HTTP/1.1")
        private val HEADER_LINE = Pattern.compile("([A-Za-z-]+)\\s*:\\s*(.*)")

        private fun readLine(input: InputStream, charset: Charset): String {
            val buffer = ByteArrayOutputStream(input.available())
            loop@ while (true) {
                when (val c = input.read()) {
                    '\r'.code -> {}

                    -1        -> break@loop
                    '\n'.code -> break@loop
                    else      -> buffer.write(c)
                }
            }
            return String(buffer.toByteArray(), charset)
        }

        @VisibleForTesting
        internal fun readBody(input: InputStream, length: Int): String {
            var remaining = length
            val buffer = ByteArrayOutputStream(input.available())
            var c: Int = -1
            while (remaining-- > 0 && (input.read().also { c = it }) != -1) {
                buffer.write(c)
            }
            return buffer.toString("UTF8")
        }

        /** Parses a requests and returns the URI and the request body. */
        @VisibleForTesting
        internal fun parseRequest(input: InputStream): Pair<URI, String?> {
            val headerLine = readLine(input, Charset.forName("ASCII"))
            val p = REQUEST_HEADER.matcher(headerLine)
            if (!p.matches()) {
                throw IOException("invalid HTTP header '$headerLine'")
            }
            val post = ("POST" == p.group(1))
            var uri = URI(p.group(2))
            val headers: MutableMap<String, String?> = HashMap()
            while (true) {
                val line = readLine(input, Charset.forName("ASCII"))
                if (line.isEmpty()) {
                    break
                }
                val m = HEADER_LINE.matcher(line)
                if (!m.matches()) {
                    throw IOException("invalid header line '$line'")
                }
                headers[m.group(1)!!] = m.group(2)
            }
            var body: String?
            if (post) {
                val contentLength = headers["Content-Length"]?.toInt() ?: Int.MAX_VALUE
                val keepAlive = ("Keep-Alive" == headers["Connection"])
                val contentType = headers["Content-Type"]
                if (keepAlive && contentLength == Int.MAX_VALUE) {
                    throw IOException("keep-alive without content-length for $uri")
                }
                body = readBody(input, contentLength)
                if (("application/x-www-form-urlencoded" == contentType)) {
                    uri = URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, body, null)
                    // uri.encodedQuery(body)
                    body = null
                } else if ("application/json" != contentType && body.isNotBlank()) {
                    body = null
                }
            } else {
                body = null
            }
            return Pair(uri, body?.takeUnless(String::isBlank))
        }

        private fun appendHeader(name: String, value: String, header: StringBuilder) {
            header.append(name)
            header.append(": ")
            header.append(value)
            header.append("\r\n")
        }
    }
}
