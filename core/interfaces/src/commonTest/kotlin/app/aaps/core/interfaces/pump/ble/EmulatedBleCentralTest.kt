package app.aaps.core.interfaces.pump.ble

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class EmulatedBleCentralTest {

    private val service = BleUuid.of("fff0")
    private val notify = CharacteristicId(service, BleUuid.of("fff1"))
    private val write = CharacteristicId(service, BleUuid.of("fff2"))

    @Test
    fun aScanFindsOnlyTheDevicesThatMatch() = runTest {
        val central = central()
        central.add(Pump(DeviceId("PUMP"), "PUMP-1", service))
        central.add(Pump(DeviceId("OTHER"), "OTHER-1", BleUuid.of("aaaa")))

        val found = central.scan(ScanFilter(services = listOf(service))).take(3).toList()

        assertEquals(setOf(DeviceId("PUMP")), found.map { it.device }.toSet())
        assertEquals(listOf("PUMP-1"), central.scan(ScanFilter(namePrefix = "PUMP")).take(1).toList().map { it.name })
    }

    @Test
    fun aScanWithBluetoothOffFailsWithTheReason() = runTest {
        val central = central()
        central.setAdapterState(AdapterState.OFF)

        val error = assertFailsWith<BleException> { central.scan(ScanFilter()).first() }

        assertEquals(BleFailure.Kind.BLUETOOTH_OFF, error.failure.kind)
    }

    @Test
    fun aConnectToADeviceThatIsNotThereTimesOut() = runTest {
        val central = central()

        val result = central.connect(DeviceId("NOBODY"), ConnectOptions(timeout = 3.seconds))

        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<BleResult.Failed>(result).failure.kind)
        assertEquals(3_000, testScheduler.currentTime)
    }

    @Test
    fun aDeviceOutOfRangeDoesNotAnswerTheConnect() = runTest {
        val central = central()
        central.add(Pump(DeviceId("PUMP"), "PUMP-1", service).apply { inRange = false })

        val result = central.connect(DeviceId("PUMP"), ConnectOptions(timeout = 1.seconds))

        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<BleResult.Failed>(result).failure.kind)
    }

    @Test
    fun aWriteReachesTheDeviceAndItsAnswerComesBack() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)
        val link = assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device)).value

        assertIs<BleResult.Ok<Unit>>(link.subscribe(notify))
        assertIs<BleResult.Ok<Unit>>(link.write(write, byteArrayOf(1, 2, 3), WriteType.WITHOUT_RESPONSE))

        assertContentEquals(byteArrayOf(1, 2, 3), pump.written.single())
        val answer = link.incoming.first()
        assertEquals(notify, answer.characteristic)
        assertContentEquals(byteArrayOf(3, 2, 1), answer.data)
    }

    @Test
    fun nothingArrivesBeforeTheSubscription() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)
        assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device))

        assertFalse(pump.links.single().send(notify, byteArrayOf(1)))
    }

    @Test
    fun aWriteTheDeviceNeverAnswersTimesOutAndEndsTheLink() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service).apply { answerWrites = false }
        central.add(pump)
        val link = assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device)).value

        val result = link.write(write, byteArrayOf(1), WriteType.WITH_RESPONSE)

        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<BleResult.Failed>(result).failure.kind)
        assertIs<LinkState.Lost>(link.state.value)
        assertEquals(1, pump.disconnects)
    }

    @Test
    fun theDeviceDropsTheLink() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)
        val link = assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device)).value

        pump.links.single().drop()

        assertIs<LinkState.Lost>(link.state.value)
        assertFalse(pump.links.single().isUp)
        assertEquals(BleFailure.Kind.NO_LINK, assertIs<BleResult.Failed>(link.write(write, byteArrayOf(1), WriteType.WITHOUT_RESPONSE)).failure.kind)
        assertEquals(1, pump.disconnects)
    }

    @Test
    fun aNewLinkIsNotReachedByTheOldOne() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)
        val first = assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device)).value
        first.close()
        val second = assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device)).value
        assertIs<BleResult.Ok<Unit>>(second.subscribe(notify))

        assertFalse(pump.links[0].send(notify, byteArrayOf(1)), "the old link")
        assertTrue(pump.links[1].send(notify, byteArrayOf(2)), "the new link")
        assertContentEquals(byteArrayOf(2), second.incoming.first().data)
    }

    @Test
    fun bluetoothOffDropsEveryLink() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)
        val link = assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device)).value

        central.setAdapterState(AdapterState.OFF)

        assertEquals(BleFailure.Kind.BLUETOOTH_OFF, assertIs<LinkState.Lost>(link.state.value).failure.kind)
        assertEquals(BleFailure.Kind.BLUETOOTH_OFF, assertIs<BleResult.Failed>(central.connect(pump.device)).failure.kind)
    }

    /** A device that bonds with a PIN gets the one the phone gave, and can refuse a wrong one. */
    @Test
    fun theBondPinReachesTheDevice() = runTest {
        val central = central()
        val pump = PinPump(DeviceId("PUMP"), wantedPin = "1234")
        central.add(pump)

        assertIs<BleResult.Failed>(central.bonding.createBond(pump.device, 5.seconds, pin = "9999"))
        assertEquals(BondState.NONE, central.bonding.bondState(pump.device).first())
        assertIs<BleResult.Ok<Unit>>(central.bonding.createBond(pump.device, 5.seconds, pin = "1234"))
        assertEquals(listOf<String?>("9999", "1234"), pump.pins)
    }

    /** Only `onBond` with the PIN: a device that needs one. */
    private inner class PinPump(override val device: DeviceId, private val wantedPin: String) : EmulatedPeripheral {

        val pins = mutableListOf<String?>()
        override val services = GattServices(mapOf(notify to setOf(CharacteristicProperty.NOTIFY)))
        override fun advertisement(): Advertisement = Advertisement(device, "PIN-PUMP", -50, emptyList(), emptyMap(), emptyMap())
        override suspend fun onConnect(link: PeripheralLink): Boolean = true
        override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit>? = BleResult.Ok(Unit)

        override suspend fun onBond(pin: String?): BleResult<Unit>? {
            pins += pin
            return if (pin == wantedPin) BleResult.Ok(Unit) else BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "wrong PIN"))
        }
    }

    @Test
    fun bondingEndsBondedAndCanBeRemoved() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)
        val bonding = central.bonding

        assertEquals(BondState.NONE, bonding.bondState(pump.device).first())
        assertIs<BleResult.Ok<Unit>>(bonding.createBond(pump.device, 5.seconds))
        assertEquals(BondState.BONDED, bonding.bondState(pump.device).first())
        assertIs<BleResult.Ok<Unit>>(bonding.removeBond(pump.device))
        assertEquals(BondState.NONE, bonding.bondState(pump.device).first())
    }

    @Test
    fun `a connect that bonds first leaves the device bonded`() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service)
        central.add(pump)

        assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device, ConnectOptions(androidBondBeforeDiscovery = true)))

        assertEquals(BondState.BONDED, central.bonding.bondState(pump.device).first())
    }

    @Test
    fun `a bond the device refuses fails the connect and ends the link`() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service).apply { bondResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED)) }
        central.add(pump)

        val result = central.connect(pump.device, ConnectOptions(androidBondBeforeDiscovery = true))

        assertEquals(BleFailure.Kind.REFUSED, assertIs<BleResult.Failed>(result).failure.kind)
        assertEquals(1, pump.disconnects)
        assertEquals(BondState.NONE, central.bonding.bondState(pump.device).first())
    }

    @Test
    fun `a bond the device never ends leaves the connect waiting until its limit`() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service).apply { bondResult = null }
        central.add(pump)

        val result = central.connect(pump.device, ConnectOptions(timeout = 2.seconds, androidBondBeforeDiscovery = true))

        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<BleResult.Failed>(result).failure.kind)
    }

    @Test
    fun `a bonded device is not bonded again`() = runTest {
        val central = central()
        val pump = Pump(DeviceId("PUMP"), "PUMP-1", service).apply { bondResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED)) }
        central.add(pump, bonded = true)

        assertIs<BleResult.Ok<BleLink>>(central.connect(pump.device, ConnectOptions(androidBondBeforeDiscovery = true)))
        assertEquals(0, pump.bondRequests)
    }

    /** The central's own coroutines run on the test's clock, so the time limits are virtual too. */
    private fun TestScope.central() = EmulatedBleCentral(scope = backgroundScope)

    /** Answers each write with the same bytes, reversed, on [notify]. */
    private inner class Pump(override val device: DeviceId, private val name: String, private val advertisedService: BleUuid) : EmulatedPeripheral {

        var inRange = true
        var answerWrites = true
        var bondResult: BleResult<Unit>? = BleResult.Ok(Unit)
        var bondRequests = 0
        val written = mutableListOf<ByteArray>()
        val links = mutableListOf<PeripheralLink>()
        var disconnects = 0

        override val services = GattServices(
            mapOf(
                notify to setOf(CharacteristicProperty.NOTIFY),
                write to setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE)
            )
        )

        override fun advertisement(): Advertisement? =
            if (inRange) Advertisement(device, name, -50, listOf(advertisedService), emptyMap(), emptyMap()) else null

        override suspend fun onConnect(link: PeripheralLink): Boolean {
            if (inRange) links += link
            return inRange
        }

        override suspend fun onBond(): BleResult<Unit>? {
            bondRequests++
            return bondResult
        }

        override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit>? {
            if (!answerWrites) return null
            written += data
            link.send(notify, data.reversedArray())
            return BleResult.Ok(Unit)
        }

        override fun onDisconnect(link: PeripheralLink) {
            disconnects++
        }
    }
}
