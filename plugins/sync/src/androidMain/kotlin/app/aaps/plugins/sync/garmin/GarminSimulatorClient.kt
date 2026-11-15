package app.aaps.plugins.sync.garmin

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQApp.IQAppStatus
import io.reactivex.rxjava3.disposables.Disposable
import org.jetbrains.annotations.VisibleForTesting
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** [GarminClient] that talks to the ConnectIQ simulator via HTTP.
 *
 * This is needed for Garmin device app development. */
class GarminSimulatorClient(
    private val aapsLogger: AAPSLogger,
    private val receiver: GarminReceiver,
    var port: Int = 7381
): Disposable, GarminClient {

    override val name = "Sim"
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket()
    private val connections: MutableList<Connection> = Collections.synchronizedList(mutableListOf())
    private var nextDeviceId = AtomicLong(1)
    @VisibleForTesting
    val iqApp = IQApp("SimApp", IQAppStatus.INSTALLED, "Simulator", 1)
    private val readyLock = ReentrantLock()
    private val readyCond = readyLock.newCondition()
    override val connectedDevices: List<GarminDevice> get() = connections.map { c -> c.device }

    override fun registerForMessages(app: GarminApplication) {
    }

    private inner class Connection(private val socket: Socket): Disposable {
        val device = GarminDevice(
            this@GarminSimulatorClient,
            nextDeviceId.getAndAdd(1L),
            "Sim@${socket.remoteSocketAddress}")

        fun start() {
            executor.execute {
                try {
                    run()
                } catch (e: Throwable) {
                  aapsLogger.error(LTag.GARMIN, "$device failed", e)
                }
            }
        }

        fun send(data: ByteArray) {
            if (socket.isConnected && !socket.isOutputShutdown) {
                aapsLogger.info(LTag.GARMIN, "sending ${data.size} bytes to $device")
                socket.outputStream.write(data)
                socket.outputStream.flush()
            } else {
                aapsLogger.warn(LTag.GARMIN, "socket closed, cannot send $device")
            }
        }

        private fun run() {
            socket.soTimeout = 0
            socket.isInputShutdown
            while (!socket.isClosed && socket.isConnected) {
                try {
                    val data = readAvailable(socket.inputStream) ?: break
                    if (data.isNotEmpty()) {
                        kotlin.runCatching {
                            receiver.onReceiveMessage(this@GarminSimulatorClient, device.id, iqApp.applicationId, data)
                        }
                    }
                } catch (e: SocketException) {
                    aapsLogger.warn(LTag.GARMIN, "socket read failed ${e.message}")
                    break
                }
            }
            aapsLogger.info(LTag.GARMIN, "disconnect ${device.name}" )
            connections.remove(this)
        }

        private fun readAvailable(input: InputStream): ByteArray? {
            val buffer = ByteArray(1 shl 14)
            aapsLogger.info(LTag.GARMIN, "$device reading")
            val len = input.read(buffer)
            aapsLogger.info(LTag.GARMIN, "$device read $len bytes")
            if (len < 0) {
                return null
            }
            val data = ByteArray(len)
            System.arraycopy(buffer, 0, data, 0, data.size)
            return data
        }

        override fun dispose() {
            aapsLogger.info(LTag.GARMIN, "close $device")

            @Suppress("EmptyCatchBlock")
            try {
                socket.close()
            } catch (e: SocketException) {
                aapsLogger.warn(LTag.GARMIN, "closing socket failed ${e.message}")
            }
        }

        override fun isDisposed() = socket.isClosed
    }

    init {
        executor.execute {
            runCatching(::listen).exceptionOrNull()?.let { e->
                aapsLogger.error(LTag.GARMIN, "listen failed", e)
            }
        }
    }

    private fun listen() {
        val ip = Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        aapsLogger.info(LTag.GARMIN, "bind to $ip:$port")
        serverSocket.bind(InetSocketAddress(ip, port))
        port = serverSocket.localPort
        receiver.onConnect(this@GarminSimulatorClient)
        while (!serverSocket.isClosed) {
            val s = serverSocket.accept()
            aapsLogger.info(LTag.GARMIN, "accept " + s.remoteSocketAddress)
            connections.add(Connection(s))
            connections.last().start()
        }
        receiver.onDisconnect(this@GarminSimulatorClient)
    }

    /** Wait for the server to start listing to requests. */
    fun awaitReady(wait: Duration): Boolean {
        val waitUntil = Instant.now() + wait
        readyLock.withLock {
            while (!serverSocket.isBound && Instant.now() < waitUntil) {
                readyCond.await(20, TimeUnit.MILLISECONDS)
            }
        }
        return serverSocket.isBound
    }

    override fun dispose() {
        executor.shutdown()
        connections.forEach { c -> c.dispose() }
        connections.clear()
        serverSocket.close()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }

    override fun isDisposed() = serverSocket.isClosed

    private fun getConnection(device: GarminDevice): Connection? {
        return connections.firstOrNull { c -> c.device.id == device.id }
    }

    override fun sendMessage(app: GarminApplication, data: ByteArray) {
        val c = getConnection(app.device) ?: return
        try {
            c.send(data)
            receiver.onSendMessage(this, app.device.id, app.id, null)
        } catch (e: SocketException) {
            val errorMessage = "sending failed '${e.message}'"
            receiver.onSendMessage(this, app.device.id, app.id, errorMessage)
            c.dispose()
            connections.remove(c)
        }
    }

    override fun toString() = name
}