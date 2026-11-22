package app.aaps.pump.omnipod.dash.emulator

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetVersionCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.emulator.OmnipodEmulatorTransport
import app.aaps.pump.omnipod.emulator.PodEmulator
import app.aaps.shared.tests.AAPSLoggerTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The driver's real Bluetooth manager against the emulated pod: the scan, the pairing with its key exchange, the
 * EAP-AKA session and an encrypted command and its answer. Only the pod state is a mock, as a plain holder. A wrong
 * byte on either side of the pairing or of the encryption fails here, on the JVM, before any scenario runs.
 */
class DashEmulatorLinkTest {

    private val aapsLogger = AAPSLoggerTest()
    private val config: Config = mock()
    private val podState: OmnipodDashPodStateManager = mock()

    private var ltk: ByteArray? = null
    private var address: String? = null
    private var eapSqn = 0L

    private lateinit var emulator: PodEmulator
    private lateinit var transport: OmnipodEmulatorTransport
    private lateinit var manager: OmnipodDashBleManagerImpl
    private var sequence = 0

    @BeforeEach
    fun setUp() {
        whenever(podState.ltk).doAnswer { ltk }
        whenever(podState.bluetoothAddress).doAnswer { address }
        doAnswer { address = it.getArgument(0) }.whenever(podState).bluetoothAddress = anyOrNull()
        doAnswer {
            ltk = it.getArgument<PairResult>(1).ltk
            eapSqn = 1
        }.whenever(podState).updateFromPairing(any(), any())
        whenever(podState.increaseEapAkaSequenceNumber()).doAnswer { EapSqn(++eapSqn).value }

        emulator = PodEmulator(aapsLogger)
        transport = OmnipodEmulatorTransport(emulator, aapsLogger, config).apply { speed = EmulatorSpeed.FAST }
        manager = OmnipodDashBleManagerImpl(aapsLogger, podState, config, transport, transport)
    }

    @Test
    fun `the driver pairs a new pod and both sides hold the same key`() {
        val events = manager.pairNewPod().toList().blockingGet()

        assertTrue(events.any { it is PodEvent.Paired })
        assertEquals(PodEvent.Connected, events.last())
        assertNotNull(ltk)
        assertTrue(ltk!!.contentEquals(emulator.state.ltk))
        assertEquals(emulator.state.address, address)
        assertEquals(Connected, manager.getStatus())
    }

    @Test
    fun `an encrypted command gets its answer from the pod`() {
        manager.pairNewPod().blockingSubscribe()

        val version = send(GetVersionCommand.Builder().setSequenceNumber(next()).setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID).build())
        assertTrue(version is VersionResponse)
        assertEquals(PodStatus.FILLED, (version as VersionResponse).podStatus)
        // A second one: the two sides count the same nonce over the command, its answer and the ACK.
        assertTrue(send(statusCommand()) is DefaultStatusResponse)
    }

    @Test
    fun `a paired pod is not found by a scan for a new pod`() {
        manager.pairNewPod().blockingSubscribe()
        ltk = null

        assertThrows(Exception::class.java) { manager.pairNewPod().blockingSubscribe() }
    }

    @Test
    fun `after a broken link the next connect makes a new session`() {
        manager.pairNewPod().blockingSubscribe()
        assertTrue(send(statusCommand()) is DefaultStatusResponse)

        assertTrue(transport.breakLink())
        assertEquals(NotConnected, manager.getStatus())
        assertFalse(transport.state().linkUp)

        manager.connect(CONNECT_TIMEOUT_MS).blockingSubscribe()
        assertTrue(transport.state().linkUp)
        assertTrue(send(statusCommand()) is DefaultStatusResponse)
    }

    @Test
    fun `a pod out of range cannot be connected until it is back`() {
        manager.pairNewPod().blockingSubscribe()
        manager.disconnect(false)
        assertTrue(transport.setOutOfRange(true))

        assertThrows(Exception::class.java) { manager.connect(SHORT_TIMEOUT_MS).blockingSubscribe() }

        assertTrue(transport.setOutOfRange(false))
        manager.connect(CONNECT_TIMEOUT_MS).blockingSubscribe()
        assertTrue(send(statusCommand()) is DefaultStatusResponse)
    }

    @Test
    fun `the pod counts the commands that reach it`() {
        manager.pairNewPod().blockingSubscribe()
        val before = transport.state().commands

        send(statusCommand())
        send(statusCommand())

        assertEquals(before + 2, transport.state().commands)
        assertTrue(transport.loseCommand(EmulatedCommand.STATUS))
        assertFalse(transport.loseCommand(EmulatedCommand.EXTENDED_BOLUS_START))
    }

    private fun next(): Short = (sequence++ and 0x0f).toShort()

    private fun statusCommand() = GetStatusCommand.Builder().setUniqueId(POD_ID).setSequenceNumber(next())
        .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE).build()

    private fun send(command: Command): Response =
        manager.sendCommand(command, Response::class).toList().blockingGet()
            .filterIsInstance<PodEvent.ResponseReceived>().single().response

    private companion object {

        /** The id the driver gives a pod: its own controller id plus one. */
        val POD_ID = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID).increment().toLong().toInt()
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val SHORT_TIMEOUT_MS = 500L
    }
}
