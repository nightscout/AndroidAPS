package app.aaps.plugins.sync.garmin

import android.content.Context
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.LinkedList
import java.util.Queue

class GarminMessengerTest: TestBase() {
    private val context = mock<Context>()
    private val client1 = mock<GarminClient>() {
        on { name } doReturn "Mock1"
    }
    private val client2 = mock<GarminClient>() {
        on { name } doReturn "Mock2"
    }
    private var appId1 = "appId1"
    private val appId2 = "appId2"

    private val apps = mapOf(appId1 to "$appId1-name", appId2 to "$appId2-name")
    private val msgs: Queue<Pair<GarminApplication, Any>> = LinkedList()
    private var messenger = GarminMessenger(
        aapsLogger, context, apps, { app, msg -> msgs.add(app to msg) }, false, false)
    private val deviceId = 11L
    private val deviceName = "$deviceId-name"
    private val device = GarminDevice(client1, deviceId, deviceName)
    private val device2 = GarminDevice(client2, 12L, "dev2-name")

    @BeforeEach
    fun setup() {
        messenger.onConnect(client1)
        messenger.onConnect(client2)
    }
    @AfterEach
    fun cleanup() {
        messenger.dispose()
        assertTrue(messenger.isDisposed)
    }

    @Test
    fun onConnectDevice() {
        messenger.onConnectDevice(client1, deviceId, deviceName)
        verify(client1).retrieveApplicationInfo(device, appId1, apps[appId1]!!)
        verify(client1).retrieveApplicationInfo(device, appId2, apps[appId2]!!)
    }

    @Test
    fun onApplicationInfo() {
        messenger.onApplicationInfo(device, appId1, true)
        val app = messenger.liveApplications.first()
        assertEquals(device, app.device)
        assertEquals(appId1, app.id)
        assertEquals(apps[appId1], app.name)

        messenger.onApplicationInfo(device, appId1, false)
        assertEquals(0, messenger.liveApplications.size)
    }

    @Test
    fun onDisconnectDevice() {
        messenger.onConnectDevice(client1, deviceId, deviceName)
        messenger.onApplicationInfo(device, appId1, true)
        messenger.onApplicationInfo(device2, appId1, true)
        assertEquals(2, messenger.liveApplications.size)
        messenger.onDisconnectDevice(client1, device2.id)
        assertEquals(1, messenger.liveApplications.size)
        assertEquals(appId1, messenger.liveApplications.first().id)
    }

    @Test
    fun onDisconnect() {
        messenger.onApplicationInfo(device, appId1, true)
        messenger.onApplicationInfo(device2, appId2, true)
        assertEquals(2, messenger.liveApplications.size)
        messenger.onDisconnect(client1)
        assertEquals(1, messenger.liveApplications.size)
        val app = messenger.liveApplications.first()
        assertEquals(device2, app.device)
        assertEquals(appId2, app.id)
        assertEquals(apps[appId2], app.name)
    }

    @Test
    fun onReceiveMessage() {
        val data = GarminSerializer.serialize("foo")
        messenger.onReceiveMessage(client1, device.id, appId1, data)
        val (app, payload) = msgs.remove()
        assertEquals(appId1, app.id)
        assertEquals("foo", payload)
    }

    @Test
    fun sendMessageDevice() {
        messenger.onApplicationInfo(device, appId1, true)
        messenger.onApplicationInfo(device, appId2, true)

        val msgs = mutableListOf<Pair<GarminApplication, ByteArray>>()
        whenever(client1.sendMessage(any(), any())).thenAnswer { i ->
            msgs.add(i.getArgument<GarminApplication>(0) to i.getArgument(1))
        }

        messenger.sendMessage(device, "foo")
        assertEquals(2, msgs.size)
        val msg1 = msgs.first { (app, _) -> app.id == appId1 }.second
        val msg2 = msgs.first { (app, _) -> app.id == appId2 }.second
        assertEquals("foo", GarminSerializer.deserialize(msg1))
        assertEquals("foo", GarminSerializer.deserialize(msg2))
        messenger.onSendMessage(client1, device.id, appId1, null)
    }

    @Test
    fun onSendMessageAll() {
        messenger.onApplicationInfo(device, appId1, true)
        messenger.onApplicationInfo(device2, appId2, true)
        assertEquals(2, messenger.liveApplications.size)

        val msgs = mutableListOf<Pair<GarminApplication, ByteArray>>()
        whenever(client1.sendMessage(any(), any())).thenAnswer { i ->
            msgs.add(i.getArgument<GarminApplication>(0) to i.getArgument(1))
        }
        whenever(client2.sendMessage(any(), any())).thenAnswer { i ->
            msgs.add(i.getArgument<GarminApplication>(0) to i.getArgument(1))
        }

        messenger.sendMessage(listOf("foo"))
        assertEquals(2, msgs.size)
        val msg1 = msgs.first { (app, _) -> app.id == appId1 }.second
        val msg2 = msgs.first { (app, _) -> app.id == appId2 }.second
        assertEquals(listOf("foo"), GarminSerializer.deserialize(msg1))
        assertEquals(listOf("foo"), GarminSerializer.deserialize(msg2))
        messenger.onSendMessage(client1, device.id, appId1, null)
    }
}