package app.aaps.plugins.sync.garmin

import android.content.Context
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

class GarminMessengerTest: TestBase() {
    private val context = mock<Context>()

    private var appId1 = "appId1"
    private val appId2 = "appId2"

    private val apps = mapOf(appId1 to "$appId1-name", appId2 to "$appId2-name")
    private val outMessages = mutableListOf<Pair<GarminApplication, ByteArray>>()
    private val inMessages = mutableListOf<Pair<GarminApplication, Any>>()
    private var messenger = GarminMessenger(
        aapsLogger, context, apps, { app, msg -> inMessages.add(app to msg) },
        enableConnectIq = false, enableSimulator = false)
    private val client1 = mock<GarminClient>() {
        on { name } doReturn "Mock1"
        on { sendMessage(any(), any()) } doAnswer { a ->
            outMessages.add(a.getArgument<GarminApplication>(0) to a.getArgument(1))
            Unit
        }
    }
    private val client2 = mock<GarminClient>() {
        on { name } doReturn "Mock2"
        on { sendMessage(any(), any()) } doAnswer { a ->
            outMessages.add(a.getArgument<GarminApplication>(0) to a.getArgument(1))
            Unit
        }
    }
    private val device1 = GarminDevice(client1, 11L, "dev1-name")
    private val device2 = GarminDevice(client2, 12L, "dev2-name")

    @BeforeEach
    fun setup() {
        messenger.onConnect(client1)
        messenger.onConnect(client2)
        client1.stub {
            on { connectedDevices } doReturn listOf(device1)
        }
        client2.stub {
            on { connectedDevices } doReturn listOf(device2)
        }
    }
    @AfterEach
    fun cleanup() {
        messenger.dispose()
        verify(client1).dispose()
        verify(client2).dispose()
        assertTrue(messenger.isDisposed)
    }

    @Test
    fun onDisconnect() {
        messenger.onDisconnect(client1)
        val msg = "foo"
        messenger.sendMessage(msg)
        outMessages.forEach { (app, payload) ->
            assertEquals(client2, app.device.client)
            assertEquals(msg, GarminSerializer.deserialize(payload))
        }
    }

    @Test
    fun onReceiveMessage() {
        val data = GarminSerializer.serialize("foo")
        messenger.onReceiveMessage(client1, device1.id, appId1, data)
        val (app, payload) = inMessages.removeAt(0)
        assertEquals(appId1, app.id)
        assertEquals("foo", payload)
    }

    @Test
    fun sendMessageDevice() {
        messenger.sendMessage(device1, "foo")
        assertEquals(2, outMessages.size)
        val msg1 = outMessages.first { (app, _) -> app.id == appId1 }.second
        val msg2 = outMessages.first { (app, _) -> app.id == appId2 }.second
        assertEquals("foo", GarminSerializer.deserialize(msg1))
        assertEquals("foo", GarminSerializer.deserialize(msg2))
        messenger.onSendMessage(client1, device1.id, appId1, null)
    }

    @Test
    fun onSendMessageAll() {
        messenger.sendMessage(listOf("foo"))
        assertEquals(4, outMessages.size)
        val msg11 = outMessages.first { (app, _) -> app.device == device1 && app.id == appId1 }.second
        val msg12 = outMessages.first { (app, _) -> app.device == device1 && app.id == appId2 }.second
        val msg21 = outMessages.first { (app, _) -> app.device == device2 && app.id == appId1 }.second
        val msg22 = outMessages.first { (app, _) -> app.device == device2 && app.id == appId2 }.second
        assertEquals(listOf("foo"), GarminSerializer.deserialize(msg11))
        assertEquals(listOf("foo"), GarminSerializer.deserialize(msg12))
        assertEquals(listOf("foo"), GarminSerializer.deserialize(msg21))
        assertEquals(listOf("foo"), GarminSerializer.deserialize(msg22))
        messenger.onSendMessage(client1, device1.id, appId1, null)
    }
}