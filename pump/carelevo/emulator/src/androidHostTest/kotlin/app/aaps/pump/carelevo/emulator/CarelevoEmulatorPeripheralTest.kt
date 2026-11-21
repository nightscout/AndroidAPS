package app.aaps.pump.carelevo.emulator

import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.ble.Incoming
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.pump.carelevo.ble.CarelevoGatt
import app.aaps.pump.carelevo.ble.commands.BasalProgramCommand
import app.aaps.pump.carelevo.ble.commands.BolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCommand
import app.aaps.pump.carelevo.ble.commands.ImmediateBolusCommand
import app.aaps.pump.carelevo.ble.commands.InfusionInfoCommand
import app.aaps.pump.carelevo.ble.commands.PatchDiscardCommand
import app.aaps.pump.carelevo.ble.commands.PatchInfoCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The radio side of the emulated patch, [CarelevoEmulatorPeripheral], seen through [EmulatedBleCentral] as the driver
 * sees it. [CarelevoPumpEmulatorTest] proves every opcode round-trips through the codec, so nothing here re-asserts
 * frame contents beyond the opcode that tells one answer from the next. What this pins down is the radio: what a write
 * answers, the fault switches the scenario tests drive, and that an old link gets nothing of a new one.
 *
 * The central runs on the test's clock, so its time limits are virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarelevoEmulatorPeripheralTest {

    private lateinit var state: CarelevoPumpState
    private lateinit var emulator: CarelevoPumpEmulator
    private lateinit var peripheral: CarelevoEmulatorPeripheral

    @BeforeEach
    fun setUp() {
        state = CarelevoPumpState()
        emulator = CarelevoPumpEmulator(state)
        peripheral = CarelevoEmulatorPeripheral(emulator)
    }

    // ---- The link ----------------------------------------------------------------------------

    @Test
    fun `a connect gives the service of the patch and the subscription is confirmed`() = runTest {
        val link = connect(central())

        assertThat(link.services.has(CarelevoGatt.WRITE)).isTrue()
        assertThat(link.services.has(CarelevoGatt.NOTIFY)).isTrue()
        assertThat(peripheral.state().linkUp).isTrue()
    }

    @Test
    fun `a write is answered on the notify characteristic`() = runTest {
        val link = connect(central())
        val incoming = incomingOf(link)

        assertThat(link.write(CarelevoGatt.WRITE, PatchDiscardCommand().encode(), WriteType.WITH_RESPONSE)).isEqualTo(BleResult.Ok(Unit))

        val answer = incoming.receive()
        assertThat(answer.characteristic).isEqualTo(CarelevoGatt.NOTIFY)
        assertThat(answer.data[0]).isEqualTo(PatchDiscardCommand.RESPONSE_OPCODE)
    }

    @Test
    fun `a multi-frame command delivers one notification per emulator frame in order`() = runTest {
        val link = connect(central())
        val incoming = incomingOf(link)

        link.write(CarelevoGatt.WRITE, PatchInfoCommand().encode(), WriteType.WITH_RESPONSE)

        // 0x33 is answered by two report frames; both must arrive as separate notifications, in order.
        assertThat(listOf(incoming.receive().data[0], incoming.receive().data[0]))
            .containsExactly(PatchInfoCommand.RPT1_OPCODE, PatchInfoCommand.RPT2_OPCODE).inOrder()
    }

    @Test
    fun `a silent opcode completes the write but gets no answer`() = runTest {
        state.silentOpcodes += InfusionInfoCommand.REQUEST_OPCODE
        val link = connect(central())
        val incoming = incomingOf(link)

        assertThat(link.write(CarelevoGatt.WRITE, InfusionInfoCommand().encode(), WriteType.WITH_RESPONSE)).isEqualTo(BleResult.Ok(Unit))

        runCurrent()
        assertThat(incoming.tryReceive().getOrNull()).isNull()
        // ...and the frame is still recorded, so a test can assert what was sent into the silence.
        assertThat(peripheral.writes).hasSize(1)
    }

    /** The patch never got it: it does nothing. For the app this is not the same as a lost answer. */
    @Test
    fun `a lost command does nothing on the patch and gets no answer`() = runTest {
        val link = connect(central())
        val incoming = incomingOf(link)
        peripheral.loseCommand(EmulatedCommand.TEMP_BASAL_START)

        link.write(CarelevoGatt.WRITE, TempBasalCommand.byUnit(1.5, 1, 0).encode(), WriteType.WITH_RESPONSE)

        runCurrent()
        assertThat(incoming.tryReceive().getOrNull()).isNull()
        assertThat(state.tempBasalRunning).isFalse()
        assertThat(peripheral.state().commands).isEqualTo(0)
    }

    /** The patch runs it and its answer is lost: the temp basal runs, and the app cannot know. */
    @Test
    fun `a lost answer still runs the command on the patch once`() = runTest {
        val link = connect(central())
        val incoming = incomingOf(link)
        peripheral.loseAnswer(EmulatedCommand.TEMP_BASAL_START)

        link.write(CarelevoGatt.WRITE, TempBasalCommand.byUnit(1.5, 1, 0).encode(), WriteType.WITH_RESPONSE)

        runCurrent()
        assertThat(incoming.tryReceive().getOrNull()).isNull()
        assertThat(peripheral.state().tempBasal).isEqualTo(EmulatedTempBasal(absolute = 1.5, percent = null))

        link.write(CarelevoGatt.WRITE, TempBasalCommand.byUnit(1.5, 1, 0).encode(), WriteType.WITH_RESPONSE)

        assertThat(incoming.receive()).isNotNull()
    }

    @Test
    fun `a broken link tells the driver it is lost`() = runTest {
        val link = connect(central())

        peripheral.breakLink()

        assertThat(link.state.value).isInstanceOf(LinkState.Lost::class.java)
        assertThat(peripheral.state().linkUp).isFalse()
    }

    @Test
    fun `out of range a connect never comes up and the patch is not seen`() = runTest {
        val central = central()
        peripheral.setOutOfRange(true)

        val result = central.connect(peripheral.device, ConnectOptions(timeout = 2.seconds))

        assertThat((result as BleResult.Failed).failure.kind).isEqualTo(BleFailure.Kind.TIMEOUT)
        assertThat(peripheral.advertisement()).isNull()
    }

    @Test
    fun `writes records every frame in order`() = runTest {
        val patchInfo = PatchInfoCommand().encode()
        val infusionInfo = InfusionInfoCommand().encode()
        val discard = PatchDiscardCommand().encode()
        val link = connect(central())

        link.write(CarelevoGatt.WRITE, patchInfo, WriteType.WITH_RESPONSE)
        link.write(CarelevoGatt.WRITE, infusionInfo, WriteType.WITH_RESPONSE)
        link.write(CarelevoGatt.WRITE, discard, WriteType.WITH_RESPONSE)

        assertThat(peripheral.writes.map { it.toList() })
            .containsExactly(patchInfo.toList(), infusionInfo.toList(), discard.toList()).inOrder()
    }

    /** The driver connects for each queue burst: nothing meant for the old link may reach the new one. */
    @Test
    fun `a new link gets the answers and the old one nothing`() = runTest {
        val central = central()
        val first = connect(central)
        first.close()
        val second = connect(central)
        val incoming = incomingOf(second)

        second.write(CarelevoGatt.WRITE, PatchInfoCommand().encode(), WriteType.WITH_RESPONSE)

        assertThat(listOf(incoming.receive().data[0], incoming.receive().data[0]))
            .containsExactly(PatchInfoCommand.RPT1_OPCODE, PatchInfoCommand.RPT2_OPCODE).inOrder()
        assertThat(first.state.value).isEqualTo(LinkState.Closed)
    }

    // ---- Bonding -----------------------------------------------------------------------------

    @Test
    fun `pairing bonds the patch once`() = runTest {
        val central = central()

        connect(central, ConnectOptions(androidBondBeforeDiscovery = true)).close()
        connect(central, ConnectOptions(androidBondBeforeDiscovery = true))

        assertThat(peripheral.bondCalls).isEqualTo(1)
        assertThat(central.bonding.bondState(peripheral.device).first()).isEqualTo(BondState.BONDED)
    }

    // ---- The scan ----------------------------------------------------------------------------

    @Test
    fun `a scan for the service finds the patch with its name and mac`() = runTest {
        val central = central()

        val found = central.scan(ScanFilter(services = listOf(CarelevoGatt.SERVICE))).first()

        assertThat(found.device.value).isEqualTo(DEFAULT_MAC)
        assertThat(found.name).isEqualTo("CareLevo-$DEFAULT_SERIAL")
        // CarelevoPatchConnectViewModel drops anything weaker than -45.
        assertThat(found.rssi).isGreaterThan(-45)
    }

    @Test
    fun `the advertised name follows the patch serial`() {
        state.serialNumber = OTHER_SERIAL

        assertThat(peripheral.advertisement()?.name).isEqualTo("CareLevo-$OTHER_SERIAL")
    }

    // ---- Test hooks --------------------------------------------------------------------------

    @Test
    fun `the speed is the speed of the emulator`() {
        peripheral.speed = EmulatorSpeed.FAST
        assertThat(emulator.speed).isEqualTo(EmulatorSpeed.FAST)

        emulator.speed = EmulatorSpeed.REAL
        assertThat(peripheral.speed).isEqualTo(EmulatorSpeed.REAL)
    }

    /** Each command the scenarios can lose maps to the opcodes the driver sends for it. */
    @Test
    fun `each command that can be lost is lost on its own opcodes`() = runTest {
        val frames = mapOf(
            EmulatedCommand.BOLUS_START to listOf(ImmediateBolusCommand(actionId = 1, volume = 1.0).encode()),
            EmulatedCommand.BOLUS_STOP to listOf(BolusCancelCommand().encode()),
            EmulatedCommand.TEMP_BASAL_START to listOf(TempBasalCommand.byUnit(1.5, 1, 0).encode()),
            EmulatedCommand.TEMP_BASAL_STOP to listOf(TempBasalCancelCommand().encode()),
            EmulatedCommand.EXTENDED_BOLUS_START to listOf(ExtendBolusCommand(immediateDose = 0.0, extendedSpeed = 1.0, hour = 1, min = 0).encode()),
            EmulatedCommand.EXTENDED_BOLUS_STOP to listOf(ExtendBolusCancelCommand().encode()),
            // The basal program has two opcodes: set at the activation, update later.
            EmulatedCommand.BASAL_PROFILE to listOf(
                BasalProgramCommand(isUpdate = false, seqNo = 0, segmentSpeeds = listOf(1.0)).encode(),
                BasalProgramCommand(isUpdate = true, seqNo = 0, segmentSpeeds = listOf(1.0)).encode()
            ),
            EmulatedCommand.STATUS to listOf(InfusionInfoCommand().encode())
        )
        assertThat(frames.keys).containsExactlyElementsIn(EmulatedCommand.entries)
        val link = connect(central())
        val incoming = incomingOf(link)

        for ((command, commandFrames) in frames) for (frame in commandFrames) {
            val before = peripheral.state().commands
            peripheral.loseCommand(command)

            link.write(CarelevoGatt.WRITE, frame, WriteType.WITH_RESPONSE)

            runCurrent()
            assertWithMessage("$command 0x%02X answered".format(frame[0])).that(incoming.tryReceive().getOrNull()).isNull()
            assertWithMessage("$command 0x%02X reached the patch".format(frame[0])).that(peripheral.state().commands).isEqualTo(before)
        }
    }

    @Test
    fun `a lost command is lost once and only for its own opcode`() = runTest {
        val link = connect(central())
        val incoming = incomingOf(link)
        peripheral.loseCommand(EmulatedCommand.STATUS)

        link.write(CarelevoGatt.WRITE, PatchDiscardCommand().encode(), WriteType.WITH_RESPONSE)
        assertThat(incoming.receive().data[0]).isEqualTo(PatchDiscardCommand.RESPONSE_OPCODE)

        link.write(CarelevoGatt.WRITE, InfusionInfoCommand().encode(), WriteType.WITH_RESPONSE)
        link.write(CarelevoGatt.WRITE, InfusionInfoCommand().encode(), WriteType.WITH_RESPONSE)
        assertThat(incoming.receive().data[0]).isEqualTo(InfusionInfoCommand.RESPONSE_OPCODE)
        runCurrent()
        assertThat(incoming.tryReceive().getOrNull()).isNull()
    }

    @Test
    fun `a new patch drops the link and forgets the faults and is in range`() = runTest {
        val central = central()
        val old = connect(central)
        peripheral.loseCommand(EmulatedCommand.STATUS)
        peripheral.loseAnswer(EmulatedCommand.STATUS)
        peripheral.setOutOfRange(true)
        val oldSerial = state.serialNumber

        assertThat(peripheral.replaceWithNewPump()).isTrue()

        assertThat(peripheral.pumpState.serialNumber).isNotEqualTo(oldSerial)
        assertThat(old.state.value).isInstanceOf(LinkState.Lost::class.java)
        val link = connect(central)
        val incoming = incomingOf(link)
        link.write(CarelevoGatt.WRITE, InfusionInfoCommand().encode(), WriteType.WITH_RESPONSE)
        assertThat(incoming.receive().data[0]).isEqualTo(InfusionInfoCommand.RESPONSE_OPCODE)
    }

    @Test
    fun `each alarm the patch reports sets its flag`() {
        assertThat(peripheral.raise(EmulatedAlarm.EMPTY_RESERVOIR)).isTrue()
        assertThat(peripheral.raise(EmulatedAlarm.OCCLUSION)).isTrue()
        assertThat(peripheral.raise(EmulatedAlarm.LOW_BATTERY)).isTrue()

        assertThat(peripheral.pumpState.criticalAlarmFlags.outOfInsulin).isTrue()
        assertThat(peripheral.pumpState.criticalAlarmFlags.occlusionDetected).isTrue()
        assertThat(peripheral.pumpState.advisoryAlarmFlags.lowBattery).isTrue()
    }

    /** No snapshot flag: the driver works these out from the values the patch reports. */
    @Test
    fun `an alarm the patch has no flag for is refused and changes nothing`() {
        val critical = state.criticalAlarmFlags
        val advisory = state.advisoryAlarmFlags

        assertThat(peripheral.raise(EmulatedAlarm.LOW_RESERVOIR)).isFalse()
        assertThat(peripheral.raise(EmulatedAlarm.STOPPED_TOO_LONG)).isFalse()

        assertThat(state.criticalAlarmFlags).isEqualTo(critical)
        assertThat(state.advisoryAlarmFlags).isEqualTo(advisory)
    }

    @Test
    fun `stopping the next bolus is set on the emulator`() {
        assertThat(peripheral.stopNextBolusAt(0.5)).isTrue()

        assertThat(emulator.stopNextBolusAt).isEqualTo(0.5)
    }

    @Test
    fun `the saved state is the patch and restores to the same patch`() = runTest {
        val link = connect(central())
        link.write(CarelevoGatt.WRITE, InfusionInfoCommand().encode(), WriteType.WITH_RESPONSE)
        state.serialNumber = OTHER_SERIAL

        val restored = CarelevoPumpEmulator.restore(peripheral.saveState())

        assertThat(restored.state.serialNumber).isEqualTo(OTHER_SERIAL)
        assertThat(restored.commands).isEqualTo(1)
    }

    @Test
    fun `pumpState is the emulator state instance`() {
        // Not a copy: a test mutates `pumpState` and the emulator must answer from it.
        assertThat(peripheral.pumpState).isSameInstanceAs(emulator.state)
        assertThat(peripheral.pumpState).isSameInstanceAs(state)
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun TestScope.central(): EmulatedBleCentral = EmulatedBleCentral(scope = backgroundScope).also { it.add(peripheral) }

    /** Connected and subscribed, as the driver's session opens a link. */
    private suspend fun connect(central: EmulatedBleCentral, options: ConnectOptions = ConnectOptions()): BleLink {
        val link = (central.connect(peripheral.device, options) as BleResult.Ok).value
        assertThat(link.subscribe(CarelevoGatt.NOTIFY)).isEqualTo(BleResult.Ok(Unit))
        return link
    }

    private fun TestScope.incomingOf(link: BleLink): ReceiveChannel<Incoming> = link.incoming.produceIn(backgroundScope)

    private companion object {

        /** [CarelevoPumpState.macAddress] rendered the way the central names the device. */
        const val DEFAULT_MAC = "94:B2:16:1D:2F:6D"
        const val DEFAULT_SERIAL = "CL24000000001"
        const val OTHER_SERIAL = "CL24000000042"
    }
}
