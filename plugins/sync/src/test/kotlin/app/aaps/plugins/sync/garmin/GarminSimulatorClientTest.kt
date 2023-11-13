package app.aaps.plugins.sync.garmin

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.net.Inet4Address
import java.net.Socket
import java.time.Duration

class GarminSimulatorClientTest: TestBase() {

    private var device: GarminDevice? = null
    private var app: GarminApplication? = null
    private lateinit var client: GarminSimulatorClient
    private val receiver: GarminReceiver = mock() {
        on { onConnectDevice(any(), any(), any()) }.doAnswer { i ->
            device = GarminDevice(client, i.getArgument(1), i.getArgument(2))
            app = GarminApplication(
                client, device!!, client.iqApp.applicationID, client.iqApp.displayName)
        }
    }

    @BeforeEach
    fun setup() {
        client = GarminSimulatorClient(aapsLogger, receiver, 0)
    }

    @Test
    fun retrieveApplicationInfo() {
        assertTrue(client.awaitReady(Duration.ofSeconds(10)))
        val port = client.port
        val ip = Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        Socket(ip, port).use { socket ->
            assertTrue(socket.isConnected)
            verify(receiver).onConnect(client)
            verify(receiver, timeout(1_000)).onConnectDevice(eq(client), any(), any())
            client.retrieveApplicationInfo(app!!.device, app!!.id, app!!.name!!)
        }
        verify(receiver).onApplicationInfo(app!!.device, app!!.id, true)
    }

    @Test
    fun receiveMessage() {
        val payload = "foo".toByteArray()
        assertTrue(client.awaitReady(Duration.ofSeconds(10)))
        val port = client.port
        val ip = Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        Socket(ip, port).use { socket ->
            assertTrue(socket.isConnected)
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()
            verify(receiver).onConnect(client)
            verify(receiver, timeout(1_000)).onConnectDevice(eq(client), any(), any())
        }
        verify(receiver).onReceiveMessage(
            eq(client), eq(device!!.id), eq("SimApp"),
            argThat { p -> payload.contentEquals(p) })
    }

    @Test
    fun sendMessage() {
        val payload = "foo".toByteArray()
        assertTrue(client.awaitReady(Duration.ofSeconds(10)))
        val port = client.port
        val ip = Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        Socket(ip, port).use { socket ->
            assertTrue(socket.isConnected)
            verify(receiver).onConnect(client)
            verify(receiver, timeout(1_000)).onConnectDevice(eq(client), any(), any())
            assertNotNull(device)
            assertNotNull(app)
            client.sendMessage(app!!, payload)
        }
        verify(receiver).onSendMessage(eq(client), any(), eq(app!!.id), isNull())
    }
}