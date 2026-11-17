@file:OptIn(ExperimentalUuidApi::class)

package app.aaps.pump.carelevo.ble.gatt

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedPeripheral
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.PeripheralLink
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.pump.carelevo.ble.CarelevoGatt
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The seam the CareLevo client works on, over a Bluetooth v2 link. What it adds to the link: the write type the
 * transport before used, the check that the device is a patch, and the patch's data and the link's end as events, in
 * order and with nothing lost before the client listens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BleLinkGattConnectionTest {

    private val aapsLogger: AAPSLogger = mock()

    /** The write type Android takes by default: without response when the characteristic offers it. */
    @Test
    fun `a write goes without response when the write characteristic offers it`() = runTest {
        val patch = Patch(writeProperties = setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE))
        val gatt = connect(patch)

        gatt.writeCharacteristic(BleLinkGattConnection.WRITE_UUID, byteArrayOf(1))

        assertThat(patch.writeTypes).containsExactly(WriteType.WITHOUT_RESPONSE)
    }

    @Test
    fun `a write goes with response when the write characteristic offers only that`() = runTest {
        val patch = Patch(writeProperties = setOf(CharacteristicProperty.WRITE))
        val gatt = connect(patch)

        gatt.writeCharacteristic(BleLinkGattConnection.WRITE_UUID, byteArrayOf(1))

        assertThat(patch.writeTypes).containsExactly(WriteType.WITH_RESPONSE)
    }

    @Test
    fun `a write the device refuses fails`() = runTest {
        val patch = Patch().apply { writeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, platformStatus = 5)) }
        val gatt = connect(patch)

        assertFailsWith<GattWriteException> { gatt.writeCharacteristic(BleLinkGattConnection.WRITE_UUID, byteArrayOf(1)) }
    }

    @Test
    fun `a write to another characteristic is refused before the link`() = runTest {
        val patch = Patch()
        val gatt = connect(patch)

        assertFailsWith<IllegalArgumentException> { gatt.writeCharacteristic(Uuid.random(), byteArrayOf(1)) }
        assertThat(patch.writeTypes).isEmpty()
    }

    /** The client subscribes to the events a moment after the link is up; what came before must still reach it. */
    @Test
    fun `data the patch sent before anyone listened reaches the first listener`() = runTest {
        val patch = Patch()
        val gatt = connect(patch)
        patch.link!!.send(CarelevoGatt.NOTIFY, byteArrayOf(7))
        runCurrent()

        val events = mutableListOf<GattEvent>()
        backgroundScope.launch { gatt.events.collect { events += it } }
        runCurrent()

        assertThat(events.map { (it as GattEvent.Notification).payload.toList() }).containsExactly(listOf<Byte>(7))
        assertThat((events.single() as GattEvent.Notification).uuid).isEqualTo(BleLinkGattConnection.NOTIFY_UUID)
    }

    @Test
    fun `everything the patch sent comes before the disconnect`() = runTest {
        val patch = Patch()
        val gatt = connect(patch)
        val events = async { gatt.events.take(3).toList() }
        runCurrent()

        patch.link!!.send(CarelevoGatt.NOTIFY, byteArrayOf(1))
        patch.link!!.send(CarelevoGatt.NOTIFY, byteArrayOf(2))
        patch.link!!.drop()
        advanceUntilIdle()

        val received = events.await()
        assertThat(received.take(2).map { (it as GattEvent.Notification).payload.single() }).containsExactly(1.toByte(), 2.toByte()).inOrder()
        assertThat(received[2]).isEqualTo(GattEvent.ConnectionStateChanged(GattConnState.DISCONNECTED))
    }

    @Test
    fun `a device without the notify characteristic is no patch`() = runTest {
        val gatt = connect(Patch(hasNotify = false), subscribe = false)

        assertFailsWith<GattDiscoveryException> { gatt.discoverServices() }
    }

    @Test
    fun `a subscription the device refuses fails`() = runTest {
        val patch = Patch().apply { subscribeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED)) }
        val gatt = connect(patch, subscribe = false)

        assertFailsWith<GattDiscoveryException> { gatt.enableNotifications(BleLinkGattConnection.NOTIFY_UUID) }
    }

    @Test
    fun `close ends the link`() = runTest {
        val patch = Patch()
        val link = (central(patch).connect(patch.device) as BleResult.Ok).value
        val gatt = BleLinkGattConnection(link, backgroundScope, aapsLogger)

        gatt.closeNow()

        assertThat(link.state.value).isEqualTo(LinkState.Closed)
        assertThat(patch.link).isNull()
    }

    /** A session that ends cancels its scope first and closes the link after; the link must still close. */
    @Test
    fun `close ends the link also when its scope is already cancelled`() = runTest {
        val patch = Patch()
        val link = (central(patch).connect(patch.device) as BleResult.Ok).value
        val sessionScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val gatt = BleLinkGattConnection(link, sessionScope, aapsLogger)
        sessionScope.cancel()

        gatt.close()
        advanceUntilIdle()

        assertThat(link.state.value).isEqualTo(LinkState.Closed)
        assertThat(patch.link).isNull()
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun TestScope.central(patch: Patch) = EmulatedBleCentral(scope = backgroundScope).also { it.add(patch) }

    private suspend fun TestScope.connect(patch: Patch, subscribe: Boolean = true): BleLinkGattConnection {
        val link: BleLink = (central(patch).connect(patch.device) as BleResult.Ok).value
        val gatt = BleLinkGattConnection(link, backgroundScope, aapsLogger)
        if (subscribe) gatt.enableNotifications(BleLinkGattConnection.NOTIFY_UUID)
        return gatt
    }

    private class Patch(
        private val writeProperties: Set<CharacteristicProperty> = setOf(CharacteristicProperty.WRITE),
        private val hasNotify: Boolean = true
    ) : EmulatedPeripheral {

        override val device = DeviceId("94:B2:16:1D:2F:6D")
        override val services = GattServices(
            buildMap {
                put(CarelevoGatt.WRITE, writeProperties)
                if (hasNotify) put(CarelevoGatt.NOTIFY, setOf(CharacteristicProperty.NOTIFY))
            }
        )
        val writeTypes = mutableListOf<WriteType>()
        var writeResult: BleResult<Unit> = BleResult.Ok(Unit)
        var subscribeResult: BleResult<Unit> = BleResult.Ok(Unit)
        var link: PeripheralLink? = null

        override fun advertisement(): Advertisement? = null

        override suspend fun onConnect(link: PeripheralLink): Boolean {
            this.link = link
            return true
        }

        override suspend fun onSubscribe(link: PeripheralLink, characteristic: CharacteristicId): BleResult<Unit> = subscribeResult

        override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit> {
            writeTypes += type
            return writeResult
        }

        override fun onDisconnect(link: PeripheralLink) {
            if (this.link === link) this.link = null
        }
    }
}
