package app.aaps.plugins.sync.garmin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.shared.tests.TestBase
import com.garmin.android.apps.connectmobile.connectiq.IConnectIQService
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class GarminDeviceClientTest : TestBase() {

    private val serviceDescriptor = "com.garmin.android.apps.connectmobile.connectiq.IConnectIQService"
    private lateinit var client: GarminDeviceClient
    private lateinit var serviceConnection: ServiceConnection
    private lateinit var device: GarminDevice
    private val packageName = "TestPackage"
    private val actions = mutableMapOf<String, BroadcastReceiver>()

    // Maps app ids to intent actions.
    private val receivers = mutableMapOf<String, String>()

    private val receiver = mock<GarminReceiver>()
    private val binder = mock<IBinder> {
        on { isBinderAlive } doReturn true
    }
    private val ciqService = mock<IConnectIQService> {
        on { asBinder() } doReturn binder
        on { connectedDevices } doReturn listOf(IQDevice(1L, "TDevice"))
        on { registerApp(any(), any(), any()) }.doAnswer { i ->
            receivers[i.getArgument<IQApp>(0).applicationId] = i.getArgument(1)
        }
    }
    private val context = mock<Context> {
        on { packageName } doReturn this@GarminDeviceClientTest.packageName
        on { registerReceiver(any<BroadcastReceiver>(), any()) } doAnswer { i ->
            actions[i.getArgument<IntentFilter>(1).getAction(0)] = i.getArgument(0)
            Intent()
        }
        on { unregisterReceiver(any()) } doAnswer { i ->
            val keys = actions.entries.filter { (_, br) -> br == i.getArgument(0) }.map { (k, _) -> k }
            keys.forEach { k -> actions.remove(k) }
        }
        on { bindService(any(), eq(Context.BIND_AUTO_CREATE), any(), any()) }.doAnswer { i ->
            serviceConnection = i.getArgument(3)
            i.getArgument<Executor>(2).execute {
                serviceConnection.onServiceConnected(
                    GarminDeviceClient.CONNECTIQ_SERVICE_COMPONENT,
                    Binder().apply { attachInterface(ciqService, serviceDescriptor) })
            }
            true
        }
        on { bindService(any(), any(), eq(Context.BIND_AUTO_CREATE)) }.doAnswer { i ->
            serviceConnection = i.getArgument(1)
            serviceConnection.onServiceConnected(
                GarminDeviceClient.CONNECTIQ_SERVICE_COMPONENT,
                Binder().apply { attachInterface(ciqService, serviceDescriptor) })
            true
        }
    }

    @Before
    fun setup() {
        client = GarminDeviceClient(aapsLogger, context, receiver, retryWaitFactor = 0L)
        device = GarminDevice(client, 1L, "TDevice")
        verify(receiver, timeout(2_000L)).onConnect(client)
    }

    @After
    fun shutdown() {
        if (::client.isInitialized) client.dispose()
        assertEquals(0, actions.size)  // make sure all broadcastReceivers were unregistered
        verify(context).unbindService(serviceConnection)
    }

    @Test
    fun connect() {
    }

    @Test
    fun disconnect() {
        serviceConnection.onServiceDisconnected(GarminDeviceClient.CONNECTIQ_SERVICE_COMPONENT)
        verify(receiver).onDisconnect(client)
        assertEquals(0, actions.size)
    }

    @Test
    fun connectedDevices() {
        assertEquals(listOf(device), client.connectedDevices)
        verify(ciqService).connectedDevices
    }

    @Test
    fun reconnectDeadBinder() {
        whenever(binder.isBinderAlive).thenReturn(false, true)
        assertEquals(listOf(device), client.connectedDevices)

        verify(ciqService).connectedDevices
        verify(ciqService, times(2)).asBinder()
        verify(context, times(2))
            .bindService(any(), eq(Context.BIND_AUTO_CREATE), any(), any())

        verifyNoMoreInteractions(ciqService)
        verifyNoMoreInteractions(receiver)
    }

    @Test
    fun sendMessage() {
        val appId = "APPID1"
        val data = "Hello, World!".toByteArray()

        client.sendMessage(GarminApplication(device, appId, "$appId-name"), data)
        verify(ciqService).sendMessage(
            argThat { iqMsg ->
                data.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId })

        val intent = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.SUCCESS.ordinal)
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_APPLICATION_ID, appId)
        }
        actions[client.sendMessageAction]!!.onReceive(context, intent)
        actions[client.sendMessageAction]!!.onReceive(context, intent)  // extra on receive will be ignored
        verify(receiver).onSendMessage(client, device.id, appId, null)
    }

    @Test
    fun sendMessage_failNoRetry() {
        val appId = "APPID1"
        val data = "Hello, World!".toByteArray()

        client.sendMessage(GarminApplication(device, appId, "$appId-name"), data)
        verify(ciqService).sendMessage(
            argThat { iqMsg ->
                data.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId })

        val intent = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.FAILURE_MESSAGE_TOO_LARGE.ordinal)
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_APPLICATION_ID, appId)
        }
        actions[client.sendMessageAction]!!.onReceive(context, intent)
        verify(receiver).onSendMessage(client, device.id, appId, "error FAILURE_MESSAGE_TOO_LARGE")
    }

    @Test
    fun sendMessage_failRetry() {
        val appId = "APPID1"
        val data = "Hello, World!".toByteArray()

        client.sendMessage(GarminApplication(device, appId, "$appId-name"), data)
        verify(ciqService).sendMessage(
            argThat { iqMsg ->
                data.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId })

        val intent = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.FAILURE_DURING_TRANSFER.ordinal)
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_APPLICATION_ID, appId)
        }
        actions[client.sendMessageAction]!!.onReceive(context, intent)
        verifyNoMoreInteractions(receiver)

        // Verify retry ...
        verify(ciqService, timeout(10_000L).times(2)).sendMessage(
            argThat { iqMsg ->
                data.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId })

        intent.putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.SUCCESS.ordinal)
        actions[client.sendMessageAction]!!.onReceive(context, intent)
        verify(receiver).onSendMessage(client, device.id, appId, null)
    }

    @Test
    fun sendMessage_2toSameApp() {
        val appId = "APPID1"
        val data1 = "m1".toByteArray()
        val data2 = "m2".toByteArray()

        client.sendMessage(GarminApplication(device, appId, "$appId-name"), data1)
        client.sendMessage(GarminApplication(device, appId, "$appId-name"), data2)
        verify(ciqService).sendMessage(
            argThat { iqMsg ->
                data1.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId })
        verify(ciqService, atLeastOnce()).asBinder()
        verifyNoMoreInteractions(ciqService)

        val intent = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.SUCCESS.ordinal)
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_APPLICATION_ID, appId)
        }
        actions[client.sendMessageAction]!!.onReceive(context, intent)
        verify(receiver).onSendMessage(client, device.id, appId, null)

        verify(ciqService, timeout(5000L)).sendMessage(
            argThat { iqMsg ->
                data2.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId })

        actions[client.sendMessageAction]!!.onReceive(context, intent)
        verify(receiver, times(2)).onSendMessage(client, device.id, appId, null)
    }

    @Test
    fun sendMessage_2to2Apps() {
        val appId1 = "APPID1"
        val appId2 = "APPID2"
        val data1 = "m1".toByteArray()
        val data2 = "m2".toByteArray()

        client.sendMessage(GarminApplication(device, appId1, "$appId1-name"), data1)
        client.sendMessage(GarminApplication(device, appId2, "$appId2-name"), data2)
        verify(ciqService).sendMessage(
            argThat { iqMsg ->
                data1.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId1 })
        verify(ciqService, timeout(5000L)).sendMessage(
            argThat { iqMsg ->
                data2.contentEquals(iqMsg.messageData)
                    && iqMsg.notificationPackage == packageName
                    && iqMsg.notificationAction == client.sendMessageAction
            },
            argThat { iqDevice -> iqDevice.deviceIdentifier == device.id },
            argThat { iqApp -> iqApp?.applicationId == appId2 })

        val intent1 = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.SUCCESS.ordinal)
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_APPLICATION_ID, appId1)
        }
        actions[client.sendMessageAction]!!.onReceive(context, intent1)
        verify(receiver).onSendMessage(client, device.id, appId1, null)

        val intent2 = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_STATUS, ConnectIQ.IQMessageStatus.SUCCESS.ordinal)
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_APPLICATION_ID, appId2)
        }
        actions[client.sendMessageAction]!!.onReceive(context, intent2)
        verify(receiver).onSendMessage(client, device.id, appId2, null)
    }

    @Test
    fun receiveMessage() {
        val app = GarminApplication(GarminDevice(client, 1L, "D1"), "APPID1", "N1")
        client.registerForMessages(app)
        assertTrue(receivers.contains(app.id))
        val intent = Intent().apply {
            putExtra(GarminDeviceClient.EXTRA_REMOTE_DEVICE, app.device.toIQDevice())
            putExtra(GarminDeviceClient.EXTRA_PAYLOAD, "foo".toByteArray())
        }
        actions[receivers[app.id]]!!.onReceive(context, intent)
        verify(receiver).onReceiveMessage(
            eq(client),
            eq(app.device.id),
            eq(app.id),
            argThat { payload -> "foo" == String(payload) })
    }
}