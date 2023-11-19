package app.aaps.plugins.sync.garmin

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.net.Inet4Address
import java.net.Socket
import java.time.Duration

class GarminSimulatorClientTest: TestBase() {

    private lateinit var client: GarminSimulatorClient
    private val receiver: GarminReceiver = mock()

    @BeforeEach
    fun setup() {
        client = GarminSimulatorClient(aapsLogger, receiver, 0)
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
        }
        assertEquals(1, client.connectedDevices.size)
        val device: GarminDevice = client.connectedDevices.first()
        verify(receiver, timeout(1_000))
            .onReceiveMessage(eq(client), eq(device.id), eq("SIMAPP"), eq(payload))
    }

    @Test
    fun sendMessage() {
        val payload = "foo".toByteArray()
        assertTrue(client.awaitReady(Duration.ofSeconds(10)))
        val port = client.port
        val ip = Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        val device: GarminDevice
        val app: GarminApplication
        Socket(ip, port).use { socket ->
            assertTrue(socket.isConnected)
            verify(receiver).onConnect(client)
            assertEquals(1, client.connectedDevices.size)
            device = client.connectedDevices.first()
            app = GarminApplication(device, "SIMAPP", "T")
            client.sendMessage(app, payload)
        }
        verify(receiver, timeout(1_000)).onSendMessage(eq(client), eq(device.id), eq(app.id), isNull())
    }
}