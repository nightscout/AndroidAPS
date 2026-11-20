package app.aaps.pump.omnipod.omnipod5.emulator

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetVersionCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBolusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ProgramReminder
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoActivationTimeResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoTriggeredAlertsResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5BleManagerImpl
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.security.SecureO5RegistrationStorage
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.InMemoryO5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.omnipod5.di.O5EmulatorRadio
import app.aaps.shared.tests.AAPSLoggerTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * The Omnipod 5 driver's real Bluetooth manager against the emulated pod: the scan, the P-256 pairing with its
 * encrypted certificate messages, the EAP-AKA session, the AID setup commands, and plain and signed commands with
 * their answers, whose CRC and sequence number the driver checks. A wrong byte on either side fails here, on the JVM.
 */
class O5EmulatorLinkTest {

    private val aapsLogger = AAPSLoggerTest()
    private val config: Config = mock()
    private val p256KeyGenerator = P256KeyGenerator()

    private lateinit var podState: InMemoryO5PodStateManager
    private lateinit var radio: O5EmulatorRadio
    private lateinit var manager: O5BleManagerImpl
    private var sequence = 0

    @BeforeEach
    fun setUp() {
        podState = InMemoryO5PodStateManager()
        radio = O5EmulatorRadio(aapsLogger, config, PumpEmulators(), podState, p256KeyGenerator)
        radio.transport.speed = EmulatorSpeed.FAST
        manager = O5BleManagerImpl(aapsLogger, podState, config, radio, mock<SecureO5RegistrationStorage>(), p256KeyGenerator)
    }

    @Test
    fun `the driver pairs a new pod and both sides hold the same key`() {
        val events = manager.pairNewPod().toList().blockingGet()

        assertTrue(events.any { it is PodEvent.Paired })
        assertEquals(PodEvent.Connected, events.last())
        assertNotNull(podState.ltk)
        assertTrue(podState.ltk!!.contentEquals(radio.transport.emulator.state.ltk))
        assertEquals(Connected, manager.getStatus())
        assertEquals(O5PodStateManager.BluetoothConnectionState.CONNECTED, podState.bluetoothConnectionState)
    }

    @Test
    fun `the AID setup commands and a plain command get their answers`() {
        manager.pairNewPod().blockingSubscribe()

        val version = send(GetVersionCommand.Builder().setSequenceNumber(next()).setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID).build())
        assertEquals(PodStatus.FILLED, (version as VersionResponse).podStatus)
        assertEquals(5, version.productId.toInt())
        manager.sendAidSetupCommands().blockingAwait()
        assertTrue(send(status(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)) is DefaultStatusResponse)
    }

    @Test
    fun `a signed insulin command is taken by the pod`() {
        manager.pairNewPod().blockingSubscribe()

        val answer = send(
            ProgramBolusCommand.Builder().setUniqueId(podId()).setSequenceNumber(next()).setNonce(NONCE)
                .setNumberOfUnits(2.6).setDelayBetweenPulsesInEighthSeconds(8)
                .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0)).build()
        )
        assertTrue(answer is DefaultStatusResponse)
        assertNotNull(radio.transport.emulator.state.bolus)
    }

    @Test
    fun `the two status pages only the Omnipod 5 driver asks for are answered`() {
        manager.pairNewPod().blockingSubscribe()

        assertTrue(send(status(ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_1)) is PodInfoTriggeredAlertsResponse)
        assertTrue(send(status(ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_5)) is PodInfoActivationTimeResponse)
    }

    @Test
    fun `after a broken link the next connect makes a new session`() {
        manager.pairNewPod().blockingSubscribe()

        radio.transport.breakLink()
        assertEquals(NotConnected, manager.getStatus())
        assertEquals(O5PodStateManager.BluetoothConnectionState.DISCONNECTED, podState.bluetoothConnectionState)

        manager.connect(CONNECT_TIMEOUT_MS).blockingSubscribe()
        assertTrue(send(status(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)) is DefaultStatusResponse)
    }

    private fun next(): Short = (sequence++ and 0x0f).toShort()

    private fun podId(): Int = requireNotNull(podState.podId).toInt()

    private fun status(page: ResponseType.StatusResponseType) =
        GetStatusCommand.Builder().setUniqueId(podId()).setSequenceNumber(next()).setStatusResponseType(page).build()

    private fun send(command: Command): Response =
        manager.sendCommand(command, Response::class).toList().blockingGet()
            .filterIsInstance<PodEvent.ResponseReceived>().single().response

    private companion object {

        const val NONCE = 1229869870
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
