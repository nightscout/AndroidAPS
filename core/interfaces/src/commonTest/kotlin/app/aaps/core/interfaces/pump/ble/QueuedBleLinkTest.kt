package app.aaps.core.interfaces.pump.ble

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class QueuedBleLinkTest {

    private val service = BleUuid.of("fff0")
    private val writeOnly = CharacteristicId(service, BleUuid.of("fff1"))
    private val notifyOnly = CharacteristicId(service, BleUuid.of("fff2"))
    private val both = CharacteristicId(service, BleUuid.of("fff3"))
    private val readable = CharacteristicId(service, BleUuid.of("fff4"))
    private val missing = CharacteristicId(service, BleUuid.of("ffff"))

    private val port = RecordingPort()
    private val link = QueuedBleLink(
        DeviceId("AA:BB:CC:DD:EE:FF"),
        GattServices(
            mapOf(
                writeOnly to setOf(CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
                notifyOnly to setOf(CharacteristicProperty.NOTIFY),
                both to setOf(CharacteristicProperty.NOTIFY, CharacteristicProperty.INDICATE),
                readable to setOf(CharacteristicProperty.READ)
            )
        ),
        port
    )

    @Test
    fun aWriteIsOkOnlyWhenTheStackSaysSo() = runTest {
        val ok = async { link.write(writeOnly, byteArrayOf(1, 2), WriteType.WITHOUT_RESPONSE) }
        runCurrent()
        assertContentEquals(byteArrayOf(1, 2), port.started.single().data)
        link.writeDone(writeOnly, null)
        assertIs<BleResult.Ok<Unit>>(ok.await())

        val failed = async { link.write(writeOnly, byteArrayOf(3), WriteType.WITHOUT_RESPONSE) }
        runCurrent()
        link.writeDone(writeOnly, BleFailure(BleFailure.Kind.REFUSED, platformStatus = 133))
        assertEquals(133, assertIs<BleResult.Failed>(failed.await()).failure.platformStatus)
        assertEquals(LinkState.Up, link.state.value)
    }

    @Test
    fun aWriteLongerThanOnePacketIsRefusedBeforeTheStack() = runTest {
        val result = link.write(writeOnly, ByteArray(21), WriteType.WITHOUT_RESPONSE)

        assertEquals(BleFailure.Kind.REFUSED, assertIs<BleResult.Failed>(result).failure.kind)
        assertTrue(port.started.isEmpty())
    }

    @Test
    fun aCharacteristicTheDeviceDoesNotHaveIsNotSupported() = runTest {
        assertEquals(BleFailure.Kind.NOT_SUPPORTED, assertIs<BleResult.Failed>(link.write(missing, byteArrayOf(1), WriteType.WITH_RESPONSE)).failure.kind)
        assertEquals(BleFailure.Kind.NOT_SUPPORTED, assertIs<BleResult.Failed>(link.read(writeOnly)).failure.kind)
        assertEquals(BleFailure.Kind.NOT_SUPPORTED, assertIs<BleResult.Failed>(link.subscribe(writeOnly)).failure.kind)
        assertTrue(port.started.isEmpty())
    }

    @Test
    fun anOperationTheStackDoesNotStartIsRefusedAndTheLinkStaysUp() = runTest {
        port.startResult = false

        val result = link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE)

        assertEquals(BleFailure.Kind.REFUSED, assertIs<BleResult.Failed>(result).failure.kind)
        assertEquals(LinkState.Up, link.state.value)
    }

    @Test
    fun anOperationWithNoEndTimesOutAndClosesTheLink() = runTest {
        val result = link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE, timeout = 1.seconds)

        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<BleResult.Failed>(result).failure.kind)
        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<LinkState.Lost>(link.state.value).failure.kind)
        assertEquals(1, port.released)
        assertEquals(BleFailure.Kind.NO_LINK, assertIs<BleResult.Failed>(link.read(readable)).failure.kind)
        assertEquals(1, port.started.size, "only the write reached the stack")
    }

    @Test
    fun theOperationsRunOneAtATimeInOrder() = runTest {
        val first = async { link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE) }
        val second = async { link.write(writeOnly, byteArrayOf(2), WriteType.WITHOUT_RESPONSE) }
        runCurrent()
        assertEquals(1, port.started.size, "the second waits for the end of the first")

        link.writeDone(writeOnly, null)
        runCurrent()
        assertEquals(2, port.started.size)
        assertContentEquals(byteArrayOf(2), port.started[1].data)
        link.writeDone(writeOnly, null)
        assertIs<BleResult.Ok<Unit>>(first.await())
        assertIs<BleResult.Ok<Unit>>(second.await())
    }

    @Test
    fun anOperationLeftByACancelledCallerStillHoldsTheStack() = runTest {
        val abandoned = launch { link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE) }
        runCurrent()
        abandoned.cancel()
        val next = async { link.read(readable) }
        runCurrent()
        assertEquals(1, port.started.size, "the read waits for the end of the abandoned write")

        link.writeDone(writeOnly, null)
        runCurrent()
        assertEquals(2, port.started.size)
        link.readDone(readable, byteArrayOf(7), null)
        assertContentEquals(byteArrayOf(7), assertIs<BleResult.Ok<ByteArray>>(next.await()).value)
    }

    @Test
    fun anAbandonedOperationThatNeverEndsClosesTheLinkAtItsTimeLimit() = runTest {
        val abandoned = launch { link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE, timeout = 2.seconds) }
        runCurrent()
        abandoned.cancel()

        val next = link.read(readable)

        assertEquals(BleFailure.Kind.NO_LINK, assertIs<BleResult.Failed>(next).failure.kind)
        assertIs<LinkState.Lost>(link.state.value)
    }

    @Test
    fun aLostLinkEndsTheRunningOperationAndTheIncomingData() = runTest {
        val write = async { link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE) }
        runCurrent()
        link.received(notifyOnly, byteArrayOf(9))

        link.lost(BleFailure(BleFailure.Kind.NO_LINK, platformStatus = 8))

        assertEquals(BleFailure.Kind.NO_LINK, assertIs<BleResult.Failed>(write.await()).failure.kind)
        assertEquals(8, assertIs<LinkState.Lost>(link.state.value).failure.platformStatus)
        val data = withTimeoutOrNull(1.seconds) { link.incoming.toList() }
        assertEquals(1, data?.size, "data that came before the loss is kept, and the flow ends")
        assertEquals(1, port.released)
    }

    @Test
    fun dataIsKeptUntilItIsCollectedAndNothingCountsAfterTheEnd() = runTest {
        link.received(notifyOnly, byteArrayOf(1))
        link.received(both, byteArrayOf(2))
        link.close()
        link.received(notifyOnly, byteArrayOf(3))

        val data = link.incoming.toList()

        assertEquals(listOf(notifyOnly, both), data.map { it.characteristic })
        assertContentEquals(byteArrayOf(2), data[1].data)
    }

    @Test
    fun closeIsSafeTwiceAndAfterALoss() = runTest {
        link.close()
        link.close()
        link.lost(BleFailure(BleFailure.Kind.NO_LINK))

        assertEquals(LinkState.Closed, link.state.value)
        assertEquals(1, port.released)
    }

    @Test
    fun autoSubscribesWithIndicationsWhenOfferedElseNotifications() = runTest {
        val indicate = async { link.subscribe(both) }
        runCurrent()
        link.subscribeDone(both, null)
        assertIs<BleResult.Ok<Unit>>(indicate.await())

        val notify = async { link.subscribe(notifyOnly) }
        runCurrent()
        link.subscribeDone(notifyOnly, null)
        assertIs<BleResult.Ok<Unit>>(notify.await())

        assertEquals(listOf(Subscription.INDICATE, Subscription.NOTIFY), port.started.map { it.kind })
        assertEquals(BleFailure.Kind.NOT_SUPPORTED, assertIs<BleResult.Failed>(link.subscribe(notifyOnly, Subscription.INDICATE)).failure.kind)
    }

    @Test
    fun anEndThatNobodyWaitsForIsIgnored() = runTest {
        val read = async { link.read(readable) }
        runCurrent()

        link.writeDone(writeOnly, null)
        link.readDone(both, byteArrayOf(1), null)
        runCurrent()
        assertTrue(read.isActive)

        link.readDone(readable, byteArrayOf(5), null)
        assertContentEquals(byteArrayOf(5), assertIs<BleResult.Ok<ByteArray>>(read.await()).value)
    }

    @Test
    fun theMtuIsTheNewOneOrTheOldOneWhenTheRequestFails() = runTest {
        val granted = async { link.requestMtu(185) }
        runCurrent()
        link.mtuDone(185, null)
        assertEquals(185, granted.await())

        val refused = async { link.requestMtu(517) }
        runCurrent()
        link.mtuDone(23, BleFailure(BleFailure.Kind.REFUSED))
        assertEquals(23, refused.await(), "the old MTU: 20 bytes and the 3 byte header")
    }

    @Test
    fun aDeviceThatAnswersAfterTheLimitDoesNotChangeTheResult() = runTest {
        val write = async { link.write(writeOnly, byteArrayOf(1), WriteType.WITHOUT_RESPONSE, timeout = 500.milliseconds) }
        advanceTimeBy(600.milliseconds)
        link.writeDone(writeOnly, null)

        assertEquals(BleFailure.Kind.TIMEOUT, assertIs<BleResult.Failed>(write.await()).failure.kind)
    }

    private class Started(val characteristic: CharacteristicId?, val data: ByteArray? = null, val kind: Subscription? = null)

    private class RecordingPort : BleLinkPort {

        val started = mutableListOf<Started>()
        var startResult = true
        var released = 0

        override fun startWrite(characteristic: CharacteristicId, data: ByteArray, type: WriteType): Boolean {
            started += Started(characteristic, data)
            return startResult
        }

        override fun startRead(characteristic: CharacteristicId): Boolean {
            started += Started(characteristic)
            return startResult
        }

        override fun startSubscribe(characteristic: CharacteristicId, kind: Subscription): Boolean {
            started += Started(characteristic, kind = kind)
            return startResult
        }

        override fun startMtu(wanted: Int): Boolean {
            started += Started(null)
            return startResult
        }

        override fun maxWriteLength(type: WriteType): Int = 20
        override fun requestPriority(priority: LinkPriority) {}

        override fun release() {
            released++
        }
    }
}
