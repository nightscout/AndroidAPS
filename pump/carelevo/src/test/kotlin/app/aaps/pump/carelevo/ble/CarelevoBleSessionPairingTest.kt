package app.aaps.pump.carelevo.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedPeripheral
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.PeripheralLink
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotTier
import app.aaps.pump.carelevo.ble.commands.AlertAlarmSetCommand
import app.aaps.pump.carelevo.ble.commands.SafetyCheckResponse
import app.aaps.pump.carelevo.ble.gatt.GattDiscoveryException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.experimental.xor
import kotlin.test.assertFailsWith

/**
 * Tests [CarelevoBleSession] against a scripted fake pump.
 *
 * [CarelevoBleSession.runPairing] — including the **set-time retry round**: each `requestMultiple` round
 * registers a fresh waiter, so rounds stay isolated and the scenario is scriptable — round 1 answers with
 * an empty serial, round 2 with a valid one, and the retry is observable on the wire (two 0x11 writes).
 *
 * Then the other session ops ([CarelevoBleSession.readInfusionInfo], [CarelevoBleSession.runSingle],
 * [CarelevoBleSession.runSafetyCheck], [CarelevoBleSession.runBasalProgram]) and the `withSession`
 * guarantees they all share: address normalization, connect refusal/timeout, the bond poll, the
 * close-and-release teardown on a blown deadline, and the inter-session settle spacing.
 *
 * The fake patch sits on a Bluetooth v2 [EmulatedBleCentral], so the session runs over the same link
 * code a real patch gets. Uses `runTest` virtual time: the central and the session's `sessionDispatcher`
 * and `linkDispatcher` test seams all run on the test's scheduler, so every time limit is virtual and the
 * order of events is deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CarelevoBleSessionPairingTest {

    private val aapsLogger: AAPSLogger = mock()

    private lateinit var patch: FakePatch
    private lateinit var central: EmulatedBleCentral
    private lateinit var session: CarelevoBleSession

    private val spec = CarelevoBleSession.PairingSpec(
        volume = 300,
        remains = 30,
        expiry = 120,
        maxBasalSpeed = 15.0,
        maxBolusDose = 25.0,
        buzzUse = true
    )

    @BeforeEach
    fun setUp() {
        patch = FakePatch()
    }

    /**
     * Builds the central and the session on this test's scheduler: the central's coroutines, the session's
     * internal scopes (per-session + queue connect/disconnect) and the patch's own delayed drops.
     */
    private fun TestScope.useTestDispatcher() {
        // Not bonded: the central asks the patch, which answers from its `bonded` switch.
        central = EmulatedBleCentral(scope = backgroundScope).also { it.add(patch, bonded = false) }
        patch.scope = backgroundScope
        session = CarelevoBleSession(central, aapsLogger)
        val dispatcher = StandardTestDispatcher(testScheduler)
        session.sessionDispatcher = dispatcher
        session.linkDispatcher = dispatcher
    }

    @Test
    fun `runPairing retries the set-time round when the serial comes back empty`() = runTest {
        useTestDispatcher()
        patch.emptySerialRounds = 1

        val result = session.runPairing("94:B2:16:1D:2F:6D", spec)

        // Retry observable on the wire: two 0x11 set-time writes (round 1 empty serial → round 2 valid).
        assertThat(patch.writes.count { it[0] == SET_TIME_OPCODE }).isEqualTo(2)
        assertThat(result.serialNumber).isEqualTo(SERIAL)
        assertThat(result.firmwareVersion).isEqualTo("T168")
        assertThat(result.modelName).isEqualTo("6776514848")
        // Stored lower case under the id the patch was found at, which is the MAC it reports (0x9B) too.
        assertThat(result.address).isEqualTo("94:b2:16:1d:2f:6d")

        // The auth write must carry checkSumV2(key) over (MAC bytes + checksum byte), seeded with the
        // random key echoed in the 0x3B request — recompute from the captured wire traffic.
        val key = patch.writes.first { it[0] == MAC_REQUEST_OPCODE }[1]
        val expectedCheckSum = (MAC_BYTES + CHECKSUM_BYTE).fold(key) { acc, b -> acc xor b }
        val authWrite = patch.writes.first { it[0] == APP_AUTH_OPCODE }
        assertThat(authWrite[1]).isEqualTo(expectedCheckSum)
    }

    @Test
    fun `runPairing succeeds on the first round with a single set-time write`() = runTest {
        useTestDispatcher()
        patch.emptySerialRounds = 0

        val result = session.runPairing("94:B2:16:1D:2F:6D", spec)

        assertThat(patch.writes.count { it[0] == SET_TIME_OPCODE }).isEqualTo(1)
        assertThat(result.serialNumber).isEqualTo(SERIAL)
        // Full activation sequence on ONE session, in protocol order.
        assertThat(patch.writes.map { it[0] }).isEqualTo(
            listOf(MAC_REQUEST_OPCODE, APP_AUTH_OPCODE, SET_TIME_OPCODE, ALERT_ALARM_OPCODE, THRESHOLD_OPCODE)
        )
        // Threshold bundle carries the spec verbatim: remains, expiry, basal/bolus unit+centi, buzz.
        val threshold = patch.writes.first { it[0] == THRESHOLD_OPCODE }
        assertThat(threshold).isEqualTo(byteArrayOf(THRESHOLD_OPCODE, 30, 120, 15, 0, 25, 0, 0x01))
    }

    @Test
    fun `runPairing fails when the serial stays empty after all rounds`(): Unit = runTest {
        useTestDispatcher()
        patch.emptySerialRounds = Int.MAX_VALUE

        val e = assertFailsWith<IllegalStateException> {
            session.runPairing("94:B2:16:1D:2F:6D", spec)
        }

        assertThat(e.message).contains("patch info invalid")
        // Both rounds were really attempted before giving up.
        assertThat(patch.writes.count { it[0] == SET_TIME_OPCODE }).isEqualTo(2)
    }

    @Test
    fun `runPairing creates the bond when the device is not bonded`() = runTest {
        useTestDispatcher()
        patch.bonded = false

        session.runPairing("94:B2:16:1D:2F:6D", spec)

        // The connect bonds the patch before the discovery, the order it requires.
        assertThat(patch.createBondCalls).isEqualTo(1)
    }

    @Test
    fun `runPairing times out when the bond never completes`() = runTest {
        useTestDispatcher()
        patch.bonded = false
        patch.bondOnCreate = false // SMP never finishes → the bond never ends

        assertFailsWith<TimeoutCancellationException> { session.runPairing(ADDRESS, spec) }

        // The bond is part of the connect now (Bluetooth v2), so the open() budget of a pairing bounds it:
        // connect + bond. The bond was asked for, and no protocol write ever went out on an unbonded link.
        assertThat(patch.createBondCalls).isEqualTo(1)
        assertThat(testScheduler.currentTime).isEqualTo(CONNECT_TIMEOUT_MS + BOND_TIMEOUT_MS)
        assertThat(patch.writes).isEmpty()
    }

    // ===== The other session ops =====

    @Test
    fun `readInfusionInfo runs the status read and decodes the report`() = runTest {
        useTestDispatcher()

        val info = session.readInfusionInfo(ADDRESS)

        assertThat(patch.writes.map { it[0] }).isEqualTo(listOf(INFUSION_INFO_OPCODE))
        assertThat(info.runningMinutes).isEqualTo(150)
        assertThat(info.insulinRemaining).isEqualTo(150.5)
        assertThat(info.infusedTotalBasalAmount).isEqualTo(10.25)
        assertThat(info.infusedTotalBolusAmount).isEqualTo(5.5)
        assertThat(info.pumpStateRaw).isEqualTo(2)
        assertThat(info.modeRaw).isEqualTo(1)
    }

    @Test
    fun `runSingle runs an arbitrary command on a fresh session`() = runTest {
        useTestDispatcher()

        val result = session.runSingle(ADDRESS, AlertAlarmSetCommand(3))

        assertThat(result.resultCode).isEqualTo(0)
        assertThat(patch.writes.single()).isEqualTo(byteArrayOf(ALERT_ALARM_OPCODE, 3))
    }

    @Test
    fun `runSafetyCheck reports every progress frame and completes on the terminal frame`() = runTest {
        useTestDispatcher()
        patch.safetyCheckProgressFrames = 2

        val frames = mutableListOf<SafetyCheckResponse>()
        session.runSafetyCheck(ADDRESS) { frames += it }

        // onFrame sees each progress report as the pump streams it, then the terminal SUCCESS.
        assertThat(frames.map { it.resultCode }).isEqualTo(listOf(SAFETY_PROGRESS_RESULT, SAFETY_PROGRESS_RESULT, 0))
        assertThat(frames.last().insulinVolume).isEqualTo(210)
        assertThat(frames.last().durationSeconds).isEqualTo(210)
        assertThat(patch.writes.single()[0]).isEqualTo(SAFETY_CHECK_OPCODE)
    }

    @Test
    fun `runBasalProgram writes every seqNo on ONE session and reports success`() = runTest {
        useTestDispatcher()

        val ok = session.runBasalProgram(ADDRESS, listOf(listOf(1.0), listOf(2.0), listOf(3.0)))

        assertThat(ok).isTrue()
        assertThat(patch.writes.map { it[0] }).isEqualTo(List(3) { BASAL_SET_OPCODE })
        assertThat(patch.writes.map { it[1] }).isEqualTo(listOf<Byte>(0, 1, 2))
        // One session for all three: the link was opened once and released once.
        assertThat(patch.connectAddresses).hasSize(1)
    }

    @Test
    fun `runBasalProgram short-circuits on the first rejected seqNo`() = runTest {
        useTestDispatcher()
        patch.basalRejectAtSeq = 1

        val ok = session.runBasalProgram(ADDRESS, listOf(listOf(1.0), listOf(2.0), listOf(3.0)))

        assertThat(ok).isFalse()
        // seqNo 2 must NOT follow a rejected seqNo 1 — no partial program left on the pump.
        assertThat(patch.writes.map { it[1] }).isEqualTo(listOf<Byte>(0, 1))
    }

    @Test
    fun `runBasalProgram update sends the mid-therapy change opcode`() = runTest {
        useTestDispatcher()

        val ok = session.runBasalProgram(ADDRESS, listOf(listOf(1.0)), isUpdate = true)

        assertThat(ok).isTrue()
        assertThat(patch.writes.single()[0]).isEqualTo(BASAL_UPDATE_OPCODE)
    }

    // ===== withSession: open, teardown, spacing =====

    @Test
    fun `session normalizes the stored lowercase address before dialing`() = runTest {
        useTestDispatcher()

        session.readInfusionInfo("94:b2:16:1d:2f:6d")

        // BluetoothAdapter.getRemoteDevice throws on a lowercase MAC; the stored address is lowercase.
        assertThat(patch.connectAddresses).containsExactly(ADDRESS)
    }

    @Test
    fun `session fails fast when the BLE stack refuses the connect`() = runTest {
        useTestDispatcher()
        // Bluetooth off: the central refuses every connect at once.
        central.setAdapterState(AdapterState.OFF)

        val e = assertFailsWith<IllegalArgumentException> { session.readInfusionInfo(ADDRESS) }

        assertThat(e.message).contains("connect() refused")
        assertThat(patch.writes).isEmpty()
        assertThat(patch.connectAddresses).isEmpty()
    }

    @Test
    fun `session times out when the patch never reports CONNECTED`() = runTest {
        useTestDispatcher()
        patch.reportConnected = false

        assertFailsWith<TimeoutCancellationException> { session.readInfusionInfo(ADDRESS) }

        // The whole connect→discover→enable handshake is bounded, so a lost callback cannot suspend
        // forever while holding sessionMutex.
        assertThat(testScheduler.currentTime).isEqualTo(CONNECT_TIMEOUT_MS)
        assertThat(patch.writes).isEmpty()
        assertThat(patch.linkUp).isFalse()
    }

    @Test
    fun `an op that blows its deadline still tears the session down and frees the next one`() = runTest {
        useTestDispatcher()
        patch.silentOpcodes += INFUSION_INFO_OPCODE // patch never answers 0x31

        assertFailsWith<TimeoutCancellationException> { session.readInfusionInfo(ADDRESS) }

        // finally: the link was closed despite the blown deadline...
        assertThat(patch.linkUp).isFalse()
        // ...and sessionMutex was freed, so a later op — e.g. an out-of-band bolus cancel — still runs.
        assertThat(session.runSingle(ADDRESS, AlertAlarmSetCommand(0)).resultCode).isEqualTo(0)
    }

    @Test
    fun `back-to-back sessions wait out the inter-session settle before reconnecting`() = runTest {
        useTestDispatcher()
        // Warm-up session: the first session has no previous close to space out from, and it primes
        // class loading so the wall-clock gap the settle measures stays far below its 1 s window.
        session.runSingle(ADDRESS, AlertAlarmSetCommand(0))
        val afterFirst = testScheduler.currentTime

        session.runSingle(ADDRESS, AlertAlarmSetCommand(0))
        val afterSecond = testScheduler.currentTime

        assertThat(afterFirst).isEqualTo(0L) // nothing to settle from on the first session
        // The second dial waits (near enough) the full settle: the patch needs time to release the link.
        assertThat(afterSecond - afterFirst).isAtLeast(SETTLE_LOWER_BOUND_MS)
        assertThat(afterSecond - afterFirst).isAtMost(INTER_SESSION_SETTLE_MS)
    }

    // ===== Queue-owned-link lifecycle: PR #5003 surface (see _docs/CARELEVO_QUEUE_OWNED_LINK.md) =====
    //
    // Suspend-with-active-temp (CarelevoOverviewViewModel.startPumpStopProcess) runs cancelTempBasal as one
    // queue command then CmdPumpStop as a second. Under the per-op/transient model each is its own session,
    // so the 2nd re-dials while the 1st tears down — PR #5003's handshake-drop race. The queue-owned held
    // link collapses both ops onto ONE connection, removing the second handshake entirely.

    @Test
    fun `off-queue back-to-back ops each open their own transient session`() = runTest {
        useTestDispatcher()

        // No held link (pre-activation / off-queue): two ops = two transient sessions = two connects.
        session.readInfusionInfo(ADDRESS)
        session.readInfusionInfo(ADDRESS)

        assertThat(patch.connectAddresses).hasSize(2)
    }

    @Test
    fun `a handshake drop on a transient second session fails the second op`() = runTest {
        useTestDispatcher()
        // The 2nd connect succeeds at the BLE layer but the patch never reaches STATE_CONNECTED — a
        // handshake drop on the re-dial (what PR #5003 observed and retries).
        patch.dropConnectAtIndex = 2

        session.readInfusionInfo(ADDRESS) // op 1: clean session

        assertFailsWith<TimeoutCancellationException> {
            session.readInfusionInfo(ADDRESS) // op 2: fresh handshake drops → the op fails
        }
        assertThat(patch.connectAddresses).hasSize(2)
    }

    @Test
    fun `the queue-owned held link is reused across ops - one connect, not one per op`() = runTest {
        useTestDispatcher()

        // Queue opens the link once (connect-before-execute), then runs ops on it.
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        assertThat(session.connected.value).isTrue()

        session.readInfusionInfo(ADDRESS) // op 1 on the held link
        session.readInfusionInfo(ADDRESS) // op 2 on the held link — no re-dial

        // ONE connect total (the held link), not one per op — this removes #5003's second handshake.
        assertThat(patch.connectAddresses).hasSize(1)

        session.requestDisconnect("queue empty")
        advanceUntilIdle()
        assertThat(session.connected.value).isFalse()
    }

    @Test
    fun `a would-be second-session handshake drop cannot happen on the held link`() = runTest {
        useTestDispatcher()
        // A drop scripted for a 2nd connect that never occurs: the held link makes exactly one connect, so
        // both ops run on it and the second op succeeds where the transient path would have failed.
        patch.dropConnectAtIndex = 2

        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()

        session.readInfusionInfo(ADDRESS)
        session.readInfusionInfo(ADDRESS)

        assertThat(patch.connectAddresses).hasSize(1)
    }

    // ===== Held-link hardening (code-review fixes) =====

    @Test
    fun `a transient session never reports the held-link connected state`() = runTest {
        useTestDispatcher()
        val seen = mutableListOf<Boolean>()
        val collector = launch { session.connected.collect { seen.add(it) } }
        runCurrent() // collector subscribes and records the initial false

        session.readInfusionInfo(ADDRESS) // transient (no held link)
        advanceUntilIdle()
        collector.cancel()

        // `connected` tracks ONLY the queue-owned held link; a transient op must never flip it true, else
        // isConnected() would let the queue skip connect() and re-dial per op (#5003 regression).
        assertThat(seen).doesNotContain(true)
    }

    @Test
    fun `an unexpected drop on the held link tears it down so the queue re-dials`() = runTest {
        useTestDispatcher()
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        assertThat(session.connected.value).isTrue()

        // Patch drops out of range: the held link is lost.
        patch.dropLink()
        advanceUntilIdle()

        // watchForDrop tore the held link down → connected=false → the queue connect()s on its next command.
        assertThat(session.connected.value).isFalse()
    }

    @Test
    fun `a failed op on the held link tears it down so the next command re-dials`() = runTest {
        useTestDispatcher()
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        assertThat(session.connected.value).isTrue()

        // The patch stops answering → the op times out on the (now suspect) held link.
        patch.silentOpcodes.add(INFUSION_INFO_OPCODE)
        assertFailsWith<TimeoutCancellationException> { session.readInfusionInfo(ADDRESS) }

        // Held link dropped rather than left up on a dead GATT → the next command re-dials.
        assertThat(session.connected.value).isFalse()
    }

    @Test
    fun `readActiveAlarmSnapshots timing out on the held link leaves it up for the next command`() = runTest {
        useTestDispatcher()
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        assertThat(session.connected.value).isTrue()

        // Older firmware doesn't understand 0x43 at all — the fake has no branch for it, so it simply
        // never answers (see respondTo's `when`). readActiveAlarmSnapshots swallows that itself (an
        // inner withTimeoutOrNull, returning emptyList()) rather than letting a TimeoutCancellationException
        // reach withSession — a real response is always 3 elements, so empty is unambiguous "no answer".
        // Unlike every other op, this must NOT be treated as "the link is suspect": an old patch simply
        // never answering 0x43 is expected, not a sign of a dead GATT.
        assertThat(session.readActiveAlarmSnapshots(ADDRESS)).isEmpty()

        assertThat(session.connected.value).isTrue()
        // The SAME held link still works for the next command — no re-dial.
        session.readInfusionInfo(ADDRESS)
        assertThat(patch.connectAddresses).hasSize(1)
    }

    @Test
    fun `pairing while a held link is up tears the held link down first`() = runTest {
        useTestDispatcher()
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        assertThat(session.connected.value).isTrue()

        // An ensureBond (pairing) op must not open a 2nd link to the patch alongside the held link — it
        // closes the held link first, so pairing runs as the sole (transient) session. Pre-fix the held link
        // would be orphaned (still up), leaving connected=true.
        session.runPairing(ADDRESS, spec)
        advanceUntilIdle()

        assertThat(session.connected.value).isFalse()
    }

    // ===== Before Bluetooth v2: what the session does that no test checked yet =====

    @Test
    fun `readActiveAlarmSnapshots reads the three tiers in order on one session`() = runTest {
        useTestDispatcher()
        patch.answersAlarmSnapshots = true

        val snapshots = session.readActiveAlarmSnapshots(ADDRESS)

        assertThat(snapshots.map { it.tier })
            .containsExactly(ActiveAlarmSnapshotTier.CRITICAL, ActiveAlarmSnapshotTier.ADVISORY, ActiveAlarmSnapshotTier.NOTIFICATION).inOrder()
        assertThat(snapshots[0].flags.outOfInsulin).isTrue()
        assertThat(snapshots.all { it.infusing }).isTrue()
        assertThat(patch.writes.map { it.toList() }).containsExactly(
            listOf(ALARM_SNAPSHOT_OPCODE, CRITICAL_TIER),
            listOf(ALARM_SNAPSHOT_OPCODE, ADVISORY_TIER),
            listOf(ALARM_SNAPSHOT_OPCODE, NOTIFICATION_TIER)
        ).inOrder()
        assertThat(patch.connectAddresses).hasSize(1)
    }

    @Test
    fun `runAdditionalPriming sends the priming and returns the result of the patch`() = runTest {
        useTestDispatcher()

        val result = session.runAdditionalPriming(ADDRESS)

        assertThat(result.resultCode).isEqualTo(0)
        assertThat(patch.writes.map { it.toList() }).containsExactly(listOf(ADDITIONAL_PRIMING_OPCODE))
    }

    @Test
    fun `runPairing fails when the patch refuses the app auth`() = runTest {
        useTestDispatcher()
        patch.appAuthResult = ALARM_REFUSED

        assertFailsWith<IllegalStateException> { session.runPairing(ADDRESS, spec) }
        assertThat(patch.writes.map { it[0] }).doesNotContain(SET_TIME_OPCODE)
    }

    @Test
    fun `runPairing fails when the patch refuses the alert alarm mode`() = runTest {
        useTestDispatcher()
        patch.alertAlarmResult = ALARM_REFUSED

        assertFailsWith<IllegalStateException> { session.runPairing(ADDRESS, spec) }
        assertThat(patch.writes.map { it[0] }).doesNotContain(THRESHOLD_OPCODE)
    }

    @Test
    fun `runPairing fails when the patch refuses the thresholds`() = runTest {
        useTestDispatcher()
        patch.thresholdResult = ALARM_REFUSED

        assertFailsWith<IllegalStateException> { session.runPairing(ADDRESS, spec) }
    }

    /** Alarms and pulse-finish reports come by themselves; the pump plugin handles them. */
    @Test
    fun `a frame the patch sends by itself on the held link reaches the unsolicited handler`() = runTest {
        useTestDispatcher()
        val received = mutableListOf<UnsolicitedMessage>()
        session.unsolicitedHandler = { received += it }
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()

        patch.push(byteArrayOf(PULSE_FINISH_OPCODE, 0x00))
        advanceUntilIdle()

        assertThat(received.map { it.opcode }).containsExactly(PULSE_FINISH_OPCODE)
        assertThat(received.single().payload.toList()).containsExactly(PULSE_FINISH_OPCODE, 0x00.toByte()).inOrder()
    }

    @Test
    fun `an unsolicited handler that throws does not stop the next frames`() = runTest {
        useTestDispatcher()
        val received = mutableListOf<Byte>()
        session.unsolicitedHandler = {
            received += it.opcode
            if (received.size == 1) error("handler failed")
        }
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()

        patch.push(byteArrayOf(PULSE_FINISH_OPCODE, 0x00))
        advanceUntilIdle()
        patch.push(byteArrayOf(PULSE_FINISH_OPCODE, 0x01))
        advanceUntilIdle()

        assertThat(received).hasSize(2)
        assertThat(session.connected.value).isTrue()
    }

    @Test
    fun `a held link connect that fails leaves the link down and the next one brings it up`() = runTest {
        useTestDispatcher()
        patch.reportConnected = false

        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()

        assertThat(session.connected.value).isFalse()
        assertThat(session.isConnecting.value).isFalse()

        patch.reportConnected = true
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()

        assertThat(session.connected.value).isTrue()
        assertThat(patch.connectAddresses).hasSize(2)
    }

    @Test
    fun `a held link connect the stack refuses is logged and leaves the link down`() = runTest {
        useTestDispatcher()
        // Bluetooth off: the central refuses every connect at once.
        central.setAdapterState(AdapterState.OFF)

        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()

        assertThat(session.connected.value).isFalse()
        assertThat(session.isConnecting.value).isFalse()
        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>(), any<Throwable>())
    }

    @Test
    fun `a link drop during an op on the held link fails the op and takes the link down`() = runTest {
        useTestDispatcher()
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        patch.dropOnOpcodes += INFUSION_INFO_OPCODE

        assertFailsWith<BleDisconnectedException> { session.readInfusionInfo(ADDRESS) }
        advanceUntilIdle()

        assertThat(session.connected.value).isFalse()
    }

    /** A user stopping a bolus cancels the waiting op; the link itself is fine. */
    @Test
    fun `an op cancelled by its caller leaves the held link up`() = runTest {
        useTestDispatcher()
        session.requestConnect(ADDRESS, "queue")
        advanceUntilIdle()
        patch.silentOpcodes += INFUSION_INFO_OPCODE

        val op = launch { session.readInfusionInfo(ADDRESS) }
        runCurrent()
        op.cancel()
        advanceUntilIdle()

        assertThat(session.connected.value).isTrue()
        patch.silentOpcodes.clear()
        session.readInfusionInfo(ADDRESS)
        assertThat(patch.connectAddresses).hasSize(1)
    }

    // ===== Bluetooth v2 =====

    /** The driver checks what it connected to: a device with the service but not both characteristics is no patch. */
    @Test
    fun `a device without the notify characteristic of the patch fails the session and is closed`() = runTest {
        useTestDispatcher()
        patch.notifyMissing = true

        assertFailsWith<GattDiscoveryException> { session.readInfusionInfo(ADDRESS) }

        assertThat(patch.linkUp).isFalse()
        assertThat(patch.writes).isEmpty()
    }

    /** The id the patch was found at is the only one that works on iOS; on Android it is the reported MAC too. */
    @Test
    fun `pairing stores the id the patch was found at and logs a different reported MAC`() = runTest {
        useTestDispatcher()
        patch.macBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)

        val result = session.runPairing(ADDRESS, spec)

        assertThat(result.address).isEqualTo(ADDRESS.lowercase())
        verify(aapsLogger).warn(eq(LTag.PUMPCOMM), any<String>())
    }

    // ===== Fixtures =====

    /**
     * Scripted patch: answers each opcode like a real CareLevo, with [emptySerialRounds] initial
     * set-time rounds reporting a blank serial (13 spaces) before valid rounds. It is the radio side of
     * the patch on [EmulatedBleCentral], as the driver's emulator is.
     *
     * Every knob below defaults to the well-behaved patch, so a test opts into exactly the one
     * misbehaviour it is about.
     */
    private class FakePatch : EmulatedPeripheral {

        override val device = DeviceId(ADDRESS)

        /** The device has no notify characteristic: not a CareLevo patch. */
        var notifyMissing = false

        override val services: GattServices
            get() = GattServices(
                if (notifyMissing) mapOf(CarelevoGatt.WRITE to setOf(CharacteristicProperty.WRITE))
                else mapOf(
                    CarelevoGatt.WRITE to setOf(CharacteristicProperty.WRITE),
                    CarelevoGatt.NOTIFY to setOf(CharacteristicProperty.NOTIFY)
                )
            )

        /** The MAC address the patch reports in the protocol (0x9B). */
        var macBytes: ByteArray = MAC_BYTES

        /** The test's scope, for what the patch does a moment after a write. */
        lateinit var scope: CoroutineScope

        val writes = mutableListOf<ByteArray>()
        var emptySerialRounds = 0
        var bonded = true
        var createBondCalls = 0

        /** The device id of every connect that reached the patch, in order (one entry per session). */
        val connectAddresses = mutableListOf<String>()

        /** The patch never answers a connect. */
        var reportConnected = true

        /**
         * 1-based index of a connect the patch does not answer — models a handshake drop on that
         * specific session (e.g. the 2nd of two back-to-back sessions, PR #5003's re-dial race). null →
         * every connect is answered.
         */
        var dropConnectAtIndex: Int? = null

        /** The bond completes; false → the bond never ends. */
        var bondOnCreate = true

        /** Opcodes the patch silently ignores — the caller's deadline is the only way out. */
        val silentOpcodes = mutableSetOf<Byte>()

        /** Progress frames the safety check streams before its terminal frame. */
        var safetyCheckProgressFrames = 1

        /** seqNo whose basal-program write is rejected (non-zero resultCode); null → all accepted. */
        var basalRejectAtSeq: Int? = null

        /** Result codes of the pairing steps; non-zero → the patch refuses that step. */
        var appAuthResult: Byte = RESULT_SUCCESS
        var alertAlarmResult: Byte = RESULT_SUCCESS
        var thresholdResult: Byte = RESULT_SUCCESS

        /** Newer firmware answers the 0x43 alarm snapshot; older firmware (the default here) never does. */
        var answersAlarmSnapshots = false

        /** Opcodes on which the patch drops the link instead of answering, after the write itself completed. */
        val dropOnOpcodes = mutableSetOf<Byte>()

        private var setTimeRounds = 0
        private var link: PeripheralLink? = null

        /** A link to the patch is up. */
        val linkUp: Boolean get() = link?.isUp == true

        /** The patch drops the link, as out of range. */
        fun dropLink() {
            link?.drop()
        }

        /** The patch sends [frame] by itself. */
        fun push(frame: ByteArray) {
            link?.send(CarelevoGatt.NOTIFY, frame)
        }

        override fun advertisement(): Advertisement? = null

        override suspend fun onConnect(link: PeripheralLink): Boolean {
            connectAddresses += device.value
            if (!reportConnected || connectAddresses.size == dropConnectAtIndex) return false
            this.link = link
            return true
        }

        override suspend fun onBond(): BleResult<Unit>? {
            if (!bonded) {
                createBondCalls++
                if (bondOnCreate) bonded = true
            }
            return if (bonded) BleResult.Ok(Unit) else null
        }

        override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit> {
            writes += data
            respondTo(link, data)
            return BleResult.Ok(Unit)
        }

        override fun onDisconnect(link: PeripheralLink) {
            if (this.link === link) this.link = null
        }

        private fun respondTo(link: PeripheralLink, request: ByteArray) {
            fun answer(frame: ByteArray) = link.send(CarelevoGatt.NOTIFY, frame)
            val opcode = request[0]
            if (opcode in silentOpcodes) return
            if (opcode in dropOnOpcodes) {
                // After the write completed, as the v1 transport acked a write before its answer.
                scope.launch { link.drop() }
                return
            }
            when (opcode) {
                MAC_REQUEST_OPCODE        -> answer(byteArrayOf(MAC_RESPONSE_OPCODE) + macBytes + CHECKSUM_BYTE)
                APP_AUTH_OPCODE           -> answer(byteArrayOf(APP_AUTH_ACK_OPCODE, appAuthResult))
                ALARM_SNAPSHOT_OPCODE     -> if (answersAlarmSnapshots) answer(snapshotFrame(request[1]))
                ADDITIONAL_PRIMING_OPCODE -> answer(byteArrayOf(ADDITIONAL_PRIMING_ACK_OPCODE, RESULT_SUCCESS))
                SET_TIME_OPCODE           -> {
                    setTimeRounds++
                    val serial = if (setTimeRounds <= emptySerialRounds) BLANK_SERIAL else SERIAL
                    answer(byteArrayOf(RPT1_OPCODE, RESULT_SUCCESS) + serial.toByteArray(Charsets.US_ASCII))
                    answer(RPT2_FRAME)
                }

                ALERT_ALARM_OPCODE        -> answer(byteArrayOf(ALERT_ALARM_ACK_OPCODE, alertAlarmResult))
                THRESHOLD_OPCODE          -> answer(byteArrayOf(THRESHOLD_ACK_OPCODE, thresholdResult))
                INFUSION_INFO_OPCODE      -> answer(INFUSION_INFO_FRAME)
                SAFETY_CHECK_OPCODE       -> {
                    // Streams progress reports, then the terminal SUCCESS — all on 0x72.
                    repeat(safetyCheckProgressFrames) { answer(safetyFrame(SAFETY_PROGRESS_RESULT.toByte())) }
                    answer(safetyFrame(RESULT_SUCCESS))
                }

                BASAL_SET_OPCODE          -> answer(byteArrayOf(BASAL_SET_ACK_OPCODE, basalResultFor(request[1])))
                BASAL_UPDATE_OPCODE       -> answer(byteArrayOf(BASAL_UPDATE_ACK_OPCODE, basalResultFor(request[1])))
            }
        }

        /** `[0] 0x72, [1] result, [2..3] volume 210 U, [4..5] duration 210 s`. */
        private fun safetyFrame(result: Byte) = byteArrayOf(SAFETY_CHECK_ACK_OPCODE, result, 0x02, 0x0A, 0x03, 0x1E)

        /** 15 bytes: the tier opcode, infusing, then the flags. The critical tier reports out of insulin. */
        private fun snapshotFrame(tier: Byte): ByteArray =
            ByteArray(SNAPSHOT_FRAME_LENGTH).also {
                it[0] = tier
                it[1] = 0x01
                if (tier == CRITICAL_TIER) it[OUT_OF_INSULIN_INDEX] = 0x01
            }

        private fun basalResultFor(seqNo: Byte): Byte =
            if (seqNo.toInt() == basalRejectAtSeq) BASAL_REJECTED else RESULT_SUCCESS
    }
    private companion object {

        const val ADDRESS = "94:B2:16:1D:2F:6D"

        // withSession/open budgets under test (mirrored from CarelevoBleSession's private companion).
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val BOND_TIMEOUT_MS = 15_000L
        const val INTER_SESSION_SETTLE_MS = 1000L

        // The settle is measured against the WALL clock while the test runs on virtual time, so the
        // delay is (settle − real ms spent between the two sessions). Assert a band, not an equality.
        const val SETTLE_LOWER_BOUND_MS = 500L

        const val MAC_REQUEST_OPCODE: Byte = 0x3B
        const val MAC_RESPONSE_OPCODE: Byte = 0x9B.toByte()
        const val APP_AUTH_OPCODE: Byte = 0x4B
        const val APP_AUTH_ACK_OPCODE: Byte = 0xBB.toByte()
        const val SET_TIME_OPCODE: Byte = 0x11
        const val RPT1_OPCODE: Byte = 0x93.toByte()
        const val ALERT_ALARM_OPCODE: Byte = 0x48
        const val ALERT_ALARM_ACK_OPCODE: Byte = 0xA8.toByte()
        const val THRESHOLD_OPCODE: Byte = 0x1B
        const val THRESHOLD_ACK_OPCODE: Byte = 0x7B
        const val RESULT_SUCCESS: Byte = 0x00

        const val INFUSION_INFO_OPCODE: Byte = 0x31
        const val INFUSION_INFO_ACK_OPCODE: Byte = 0x91.toByte()
        const val SAFETY_CHECK_OPCODE: Byte = 0x12
        const val SAFETY_CHECK_ACK_OPCODE: Byte = 0x72
        const val SAFETY_PROGRESS_RESULT = 4 // REP_REQUEST — a non-terminal progress report
        const val BASAL_SET_OPCODE: Byte = 0x13
        const val BASAL_SET_ACK_OPCODE: Byte = 0x73
        const val BASAL_UPDATE_OPCODE: Byte = 0x21
        const val BASAL_UPDATE_ACK_OPCODE: Byte = 0x81.toByte()
        const val BASAL_REJECTED: Byte = 0x05
        const val ALARM_REFUSED: Byte = 0x01

        const val ALARM_SNAPSHOT_OPCODE: Byte = 0x43
        const val CRITICAL_TIER: Byte = 0xA4.toByte()
        const val ADVISORY_TIER: Byte = 0xA5.toByte()
        const val NOTIFICATION_TIER: Byte = 0xA6.toByte()
        const val SNAPSHOT_FRAME_LENGTH = 15
        const val OUT_OF_INSULIN_INDEX = 2
        const val ADDITIONAL_PRIMING_OPCODE: Byte = 0x1D
        const val ADDITIONAL_PRIMING_ACK_OPCODE: Byte = 0x7D

        /** Pulse finish: the patch sends it by itself when a priming is done. */
        const val PULSE_FINISH_OPCODE: Byte = 0x98.toByte()

        // Real-shape 20-byte 0x91: running 2*60+30=150 min, remaining 1*100+50+50/100=150.5 U,
        // basal total 10+25/100=10.25 U, bolus total 5+50/100=5.5 U, pumpState 2, mode 1.
        val INFUSION_INFO_FRAME = byteArrayOf(
            INFUSION_INFO_ACK_OPCODE, 0x00,
            0x02, 0x1E,
            0x01, 0x32, 0x32,
            0x0A, 0x19,
            0x05, 0x32,
            0x02,
            0x01,
            0x00, 0x00,
            0x03, 0x00,
            0x00, 0x01, 0x0A
        )

        val MAC_BYTES = byteArrayOf(0x94.toByte(), 0xB2.toByte(), 0x16, 0x1D, 0x2F, 0x6D)
        const val CHECKSUM_BYTE: Byte = 0xAB.toByte()

        const val SERIAL = "EO12507099001" // 13 chars = RPT1 bytes 2..14
        const val BLANK_SERIAL = "             " // 13 spaces → trims to empty → retry round

        // Real-shape 16-byte RPT2: [0]=0x94, [1]=result, [2..5]="T168", [6..10] filler, [11..15] model
        // bytes 0x43,0x4C,0x33,0x30,0x30 → decimal-quirk string "6776514848" (matches the real device).
        val RPT2_FRAME = byteArrayOf(
            0x94.toByte(), 0x00,
            0x54, 0x31, 0x36, 0x38,
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x43, 0x4C, 0x33, 0x30, 0x30
        )
    }
}
