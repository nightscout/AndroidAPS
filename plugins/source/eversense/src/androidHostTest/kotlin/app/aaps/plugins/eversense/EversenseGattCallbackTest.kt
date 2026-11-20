package app.aaps.plugins.eversense

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.plugins.eversense.connection.EversenseConnectionController
import app.aaps.plugins.eversense.connection.SessionStore
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.packets.e3.GetSignalStrengthRawPacket
import app.aaps.plugins.eversense.storage.EversenseStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class EversenseGattCallbackTest {
    private val plugin: EversenseCGMPlugin = mock()
    private val gatt: BluetoothGatt = mock()
    private val characteristic: BluetoothGattCharacteristic = mock()
    private val delegate: EversenseStore = mock()
    private val controller = EversenseConnectionController(
        object : EversenseConnectionController.Scheduler {
            override fun after(delayMs: Long, action: () -> Unit) = EversenseConnectionController.Task { }
        }, { 0L }, object : EversenseConnectionController.Transport {
            override fun open(session: Long) = true
            override fun reuse(session: Long) = true
            override fun close() {}
        }
    )
    private val callback: EversenseGattCallback
    init {
        controller.start()
        whenever(plugin.sessionActive(1L)).thenReturn(true)
        whenever(plugin.deliver(eq(1L), any())).thenAnswer { it.getArgument<() -> Unit>(1).invoke() }
        callback = EversenseGattCallback(plugin, SessionStore(controller, 1, delegate), 1)
        callback.attachGatt(gatt)
        EversenseGattCallback::class.java.getDeclaredField("requestCharacteristic").apply {
            isAccessible = true
            set(callback, characteristic)
        }
    }
    @AfterEach fun close() { callback.closeSession() }

    @Test fun synchronousBleResponseBeforeWriteReturnsIsRetained() {
        val packet = GetSignalStrengthRawPacket()
        whenever(gatt.writeCharacteristic(characteristic)).thenAnswer {
            callback.onCharacteristicChanged(gatt, characteristic,
                byteArrayOf(packet.spec.responseId, 0, 0, 0, 0x87.toByte(), 3))
            true
        }
        val response = callback.writePacket<GetSignalStrengthRawPacket.Response>(packet, 100)
        assertEquals(903, response.rawValue)
        assertEquals(80, response.signalStrength)
    }
    @Test fun timeoutInvalidatesLinkRatherThanAllowingResponseToReachNextCommand() {
        whenever(gatt.writeCharacteristic(characteristic)).thenReturn(true)
        assertThrows(EversenseWriteException::class.java) {
            callback.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 1)
        }
        verify(plugin).sessionDisconnected(1, -1, false)
    }
    @Test fun rejected365CommandDoesNotInvalidateAuthenticatedLink() {
        val crypto: app.aaps.plugins.eversense.util.EversenseCrypto365Util = mock()
        whenever(crypto.encrypt(any())).thenReturn(byteArrayOf(3, 14))
        whenever(crypto.decrypt(any())).thenReturn(byteArrayOf(0xff.toByte(), 0x0e, 5))
        EversenseGattCallback::class.java.getDeclaredField("cryptoUtil").apply { isAccessible = true; set(callback, crypto) }
        EversenseGattCallback::class.java.getDeclaredField("security").apply {
            isAccessible = true
            set(callback, app.aaps.plugins.eversense.enums.EversenseSecurityType.SecureV2)
        }
        whenever(gatt.writeCharacteristic(characteristic)).thenAnswer {
            callback.onCharacteristicChanged(gatt, characteristic, byteArrayOf(1, 1, 1, 0x42, 1))
            true
        }
        val error = assertThrows(EversenseWriteException::class.java) {
            callback.writePacket<app.aaps.plugins.eversense.packets.e365.SetAppVersion365Packet.Response>(
                app.aaps.plugins.eversense.packets.e365.SetAppVersion365Packet(), 100)
        }
        assertEquals("Transmitter rejected the packet", error.message)
        verify(plugin, never()).sessionDisconnected(any(), any(), any())
    }

    @Test fun closedSessionCannotSendAnotherPacket() {
        callback.closeSession()
        assertThrows(EversenseWriteException::class.java) {
            callback.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 1)
        }
        verify(gatt, never()).writeCharacteristic(any())
    }
    @Test fun rejectedBleWriteDoesNotWaitForResponse() {
        whenever(gatt.writeCharacteristic(characteristic)).thenReturn(false)
        assertThrows(EversenseWriteException::class.java) {
            callback.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100)
        }
        verify(plugin, never()).sessionDisconnected(any(), any(), any())
    }
    @Test fun queuedWorkFromInterrupted365LinkIsRejectedAfterGattReuse() {
        val device: android.bluetooth.BluetoothDevice = mock()
        whenever(gatt.device).thenReturn(device)
        whenever(device.name).thenReturn("T123")
        whenever(gatt.connect()).thenReturn(true)
        EversenseGattCallback::class.java.getDeclaredField("security").apply {
            isAccessible = true
            set(callback, app.aaps.plugins.eversense.enums.EversenseSecurityType.SecureV2)
        }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val first = callback.submitToExecutor {
            entered.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val old = callback.submitToExecutor { fail<Unit>("Stale work must not run") }
            callback.onConnectionStateChange(gatt, 19, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED)
            assertTrue(callback.reuse())
            release.countDown()
            first.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertThrows(java.util.concurrent.ExecutionException::class.java) {
                old.get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            assertEquals(42, callback.submitToExecutor { 42 }.get(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally { release.countDown() }
    }

    @Test fun commandsRequiringReadyTransmitterCannotRunDuringAuthentication() {
        val future = callback.submitToExecutor(readyOnly = true) { fail<Unit>("Not authenticated") }
        assertThrows(java.util.concurrent.ExecutionException::class.java) {
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
    @Test fun stoppingCancelsQueuedFuturesAndRejectsNewWorkWithoutThrowingOnCaller() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        callback.submitToExecutor {
            entered.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val queued = callback.submitToExecutor { fail<Unit>("Stopped work must not run") }
            callback.closeSession()
            assertTrue(queued.isCancelled)
            val rejected = callback.submitToExecutor { 42 }
            assertThrows(java.util.concurrent.ExecutionException::class.java) {
                rejected.get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        } finally { release.countDown() }
    }

    @Test fun closingSessionReleasesAnInFlightPacketWaitImmediately() {
        val sent = java.util.concurrent.CountDownLatch(1)
        whenever(gatt.writeCharacteristic(characteristic)).thenAnswer { sent.countDown(); true }
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val result = worker.submit<GetSignalStrengthRawPacket.Response> {
                callback.writePacket(GetSignalStrengthRawPacket(), 60_000)
            }
            assertTrue(sent.await(5, java.util.concurrent.TimeUnit.SECONDS))
            callback.closeSession()
            val error = assertThrows(java.util.concurrent.ExecutionException::class.java) {
                result.get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            assertTrue(error.cause is EversenseWriteException)
        } finally { worker.shutdownNow() }
    }

    @Test fun registered365IdentityUsesBluetoothAuthStartWithoutInternet() {
        val crypto: app.aaps.plugins.eversense.util.EversenseCrypto365Util = mock()
        whenever(crypto.canUseShortcut()).thenReturn(true)
        whenever(crypto.generateKeyPairIfNotExists()).thenReturn(true)
        whenever(crypto.generateEphem()).thenReturn(byteArrayOf(1))
        whenever(crypto.getStartSecret(any())).thenReturn(byteArrayOf(1))
        doThrow(IllegalStateException("Authentication reached session-key derivation"))
            .whenever(crypto).generateSessionKey(any())
        EversenseGattCallback::class.java.getDeclaredField("cryptoUtil").apply { isAccessible = true; set(callback, crypto) }
        EversenseGattCallback::class.java.getDeclaredField("security").apply {
            isAccessible = true
            set(callback, app.aaps.plugins.eversense.enums.EversenseSecurityType.SecureV2)
        }
        whenever(gatt.writeCharacteristic(characteristic)).thenAnswer {
            val packets = app.aaps.plugins.eversense.packets.e365.Eversense365Packets
            callback.onCharacteristicChanged(gatt, characteristic,
                byteArrayOf(1, 1, 1, packets.AuthenticateResponseId, packets.AuthenticateStart) + ByteArray(64))
            true
        }
        EversenseGattCallback::class.java.getDeclaredMethod("authV2flow").apply { isAccessible = true }.invoke(callback)
        verify(crypto).generateSessionKey(any())
        verify(plugin, never()).hasInternet()
        verify(gatt).writeCharacteristic(characteristic)
    }
    @Test fun missing365RegistrationOfflineDoesNotAttemptHttpOrBluetoothWhoAmI() {
        val crypto: app.aaps.plugins.eversense.util.EversenseCrypto365Util = mock()
        whenever(crypto.canUseShortcut()).thenReturn(false)
        whenever(crypto.generateKeyPairIfNotExists()).thenReturn(true)
        whenever(plugin.hasInternet()).thenReturn(false)
        EversenseGattCallback::class.java.getDeclaredField("cryptoUtil").apply { isAccessible = true; set(callback, crypto) }
        EversenseGattCallback::class.java.getDeclaredMethod("authV2flow").apply { isAccessible = true }.invoke(callback)
        verify(plugin).sessionDisconnected(1, BluetoothGatt.GATT_SUCCESS, false)
        verify(gatt, never()).writeCharacteristic(any())
    }

    @Test fun offlineDecryptFailureRetainsRegisteredIdentityForLocalReconnect() {
        val crypto: app.aaps.plugins.eversense.util.EversenseCrypto365Util = mock()
        whenever(crypto.decrypt(any())).thenReturn(byteArrayOf())
        whenever(plugin.hasInternet()).thenReturn(false)
        EversenseGattCallback::class.java.getDeclaredField("cryptoUtil").apply { isAccessible = true; set(callback, crypto) }
        EversenseGattCallback::class.java.getDeclaredField("security").apply {
            isAccessible = true
            set(callback, app.aaps.plugins.eversense.enums.EversenseSecurityType.SecureV2)
        }
        callback.onCharacteristicChanged(gatt, characteristic, byteArrayOf(1, 1, 1, 0x42, 1))
        verify(crypto).decrypt(any())
        verify(crypto, never()).disallowUseShortcut()
        verify(plugin).sessionDisconnected(1, BluetoothGatt.GATT_SUCCESS, false)
    }

}
