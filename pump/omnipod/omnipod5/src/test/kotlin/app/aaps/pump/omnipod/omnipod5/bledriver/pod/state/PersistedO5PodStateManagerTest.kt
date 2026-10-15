package app.aaps.pump.omnipod.omnipod5.bledriver.pod.state

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoActivationTimeResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoTriggeredAlertsResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Gson+[Preferences] persistence round-trip for [PersistedO5PodStateManager], with a
 * particular focus on [BasalProgram] - it has a custom `segments` property getter
 * (`Collections.unmodifiableList` wrapper), and no other class in this file's persisted
 * state had needed to serialize something with a custom getter before, so this is the
 * check that resolves that open question rather than assuming it round-trips correctly.
 *
 * [Preferences] is backed here by a single in-memory [String] var rather than individual
 * stubbed return values, so `store()` followed by a fresh manager's `load()` exercises the
 * real Gson serialize/deserialize round trip, not just mock plumbing.
 */
class PersistedO5PodStateManagerTest : TestBase() {

    @Mock lateinit var preferences: Preferences

    private var backingStore: String? = null

    @BeforeEach
    fun setUp() {
        backingStore = null
        whenever(preferences.put(eq(O5StringNonPreferenceKey.PodState), any())).thenAnswer {
            backingStore = it.getArgument(1)
            Unit
        }
        whenever(preferences.getIfExists(O5StringNonPreferenceKey.PodState)).thenAnswer { backingStore }
        whenever(preferences.get(O5StringNonPreferenceKey.PodState)).thenAnswer { backingStore ?: "" }
    }

    private fun newManager() = PersistedO5PodStateManager(aapsLogger, preferences)

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    @Test
    fun `fresh manager with no stored state returns defaults`() {
        val manager = newManager()

        assertThat(manager.activationProgress).isEqualTo(ActivationProgress.NOT_STARTED)
        assertThat(manager.ltk).isNull()
        assertThat(manager.basalProgram).isNull()
        assertThat(manager.msgSequenceNumber).isEqualTo(1.toByte())
    }

    @Test
    fun `basalProgram round-trips through Gson via a fresh manager instance`() {
        val program = BasalProgram(
            listOf(
                BasalProgram.Segment(0, 24, 100),
                BasalProgram.Segment(24, 48, 150)
            )
        )
        val writer = newManager()
        writer.basalProgram = program

        val reader = newManager()

        assertThat(reader.basalProgram).isEqualTo(program)
        assertThat(reader.basalProgram!!.segments).hasSize(2)
        assertThat(reader.basalProgram!!.segments[0].basalRateInHundredthUnitsPerHour).isEqualTo(100)
        assertThat(reader.basalProgram!!.segments[1].basalRateInHundredthUnitsPerHour).isEqualTo(150)
    }

    @Test
    fun `activationProgress and prime parameters round-trip`() {
        val writer = newManager()
        writer.activationProgress = ActivationProgress.PRIME_COMPLETED
        writer.primePulseRate = 20
        writer.firstPrimeBolusVolume = 52
        writer.secondPrimeBolusVolume = 10
        writer.podLifeInHours = 72

        val reader = newManager()

        assertThat(reader.activationProgress).isEqualTo(ActivationProgress.PRIME_COMPLETED)
        assertThat(reader.primePulseRate).isEqualTo(20.toShort())
        assertThat(reader.firstPrimeBolusVolume).isEqualTo(52.toShort())
        assertThat(reader.secondPrimeBolusVolume).isEqualTo(10.toShort())
        assertThat(reader.podLifeInHours).isEqualTo(72.toShort())
    }

    @Test
    fun `pendingDoseCommand round-trips including nested bolusType enum`() {
        val pending = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BOLUS,
            requestedUnits = 1.25,
            bolusType = BS.Type.SMB,
            startedAt = 123456789L,
            bolusRecordExpected = true
        )
        val writer = newManager()
        writer.pendingDoseCommand = pending

        val reader = newManager()

        assertThat(reader.pendingDoseCommand).isEqualTo(pending)
        assertThat(reader.pendingDoseCommand!!.bolusType).isEqualTo(BS.Type.SMB)
        assertThat(reader.pendingDoseCommand!!.bolusRecordExpected).isTrue()
    }

    @Test
    fun `pending temp type and exact basal program survive restart`() {
        val writer = newManager()
        val temp = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.TEMP_BASAL_START,
            requestedRate = 0.7, requestedDurationMinutes = 30, startedAt = 1000L,
            tempBasalType = PumpSync.TemporaryBasalType.SUPERBOLUS, historyId = 7L
        )
        writer.pendingDoseCommand = temp
        assertThat(newManager().pendingDoseCommand).isEqualTo(temp)
        val program = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BASAL_PROGRAM, startedAt = 2000L,
            requestedBasalProgram = BasalProgram(listOf(BasalProgram.Segment(0, 24, 70), BasalProgram.Segment(24, 48, 120)))
        )
        writer.pendingDoseCommand = program
        assertThat(newManager().pendingDoseCommand).isEqualTo(program)
    }

    @Test
    fun `status acceptance survives restart and a later cancellation sequence`() {
        val writer = newManager()
        val response = DefaultStatusResponse(hexToBytes("1D1800A02800000463E8"))
        writer.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BOLUS, requestedUnits = 3.0,
            startedAt = 1000L, sequenceNumber = response.sequenceNumberOfLastProgrammingCommand
        )
        writer.updateFromDefaultStatusResponse(response)
        val restarted = newManager()
        assertThat(restarted.pendingDoseCommand!!.confirmedByStatus).isTrue()
        restarted.pendingDoseCommand = restarted.pendingDoseCommand!!.copy(
            bolusCancelRequested = true, bolusCancelSequenceNumber = 6, bolusCancelHistoryId = 8L
        )
        restarted.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1800A03000000463E8")))
        val afterCancel = newManager()
        assertThat(afterCancel.pendingDoseCommand!!.confirmedByStatus).isTrue()
        assertThat(afterCancel.pendingDoseCommand!!.bolusCancelRequested).isTrue()
        assertThat(afterCancel.pendingDoseCommand!!.bolusCancelSequenceNumber).isEqualTo(6.toShort())
        assertThat(afterCancel.pendingDoseCommand!!.bolusCancelHistoryId).isEqualTo(8L)
    }

    @Test
    fun `older state without cancel intent is loaded as unknown rather than not cancelled`() {
        val writer = newManager()
        writer.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BOLUS, requestedUnits = 1.0, startedAt = 1000L
        )
        backingStore = requireNotNull(backingStore).replace(",\"bolusCancelRequested\":false", "")
        assertThat(newManager().pendingDoseCommand!!.bolusCancelRequested).isNull()
    }

    @Test
    fun `dosing state fields round-trip`() {
        val writer = newManager()
        writer.deliverySuspended = true
        writer.lastBolusStartTime = 1_000L
        writer.lastBolusRequestedUnits = 2.5
        writer.lastBolusDeliveredUnits = 2.5
        writer.activeTempBasalStartTime = 2_000L
        writer.activeTempBasalRate = 0.5
        writer.activeTempBasalDurationMinutes = 30

        val reader = newManager()

        assertThat(reader.deliverySuspended).isTrue()
        assertThat(reader.lastBolusStartTime).isEqualTo(1_000L)
        assertThat(reader.lastBolusRequestedUnits).isEqualTo(2.5)
        assertThat(reader.lastBolusDeliveredUnits).isEqualTo(2.5)
        assertThat(reader.activeTempBasalStartTime).isEqualTo(2_000L)
        assertThat(reader.activeTempBasalRate).isEqualTo(0.5)
        assertThat(reader.activeTempBasalDurationMinutes).isEqualTo(30.toShort())
    }

    @Test
    fun `completeBolus persists accounting and clears pending in one state update`() {
        val writer = newManager()
        writer.cumulativeBolusPulsesDelivered = 10
        writer.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BOLUS,
            requestedUnits = 1.0,
            startedAt = 1_000L
        )

        writer.completeBolus(1_000L, 1.0, 0.75, 15)

        val reader = newManager()
        assertThat(reader.cumulativeBolusPulsesDelivered).isEqualTo(25.toShort())
        assertThat(reader.lastBolusDeliveredUnits).isEqualTo(0.75)
        assertThat(reader.pendingDoseCommand).isNull()
    }

    @Test
    fun `completeBolus does not add pulses twice`() {
        val manager = newManager()
        manager.cumulativeBolusPulsesDelivered = 10

        manager.completeBolus(1_000L, 1.0, 0.75, 15)
        manager.completeBolus(1_000L, 1.0, 0.75, 15)

        assertThat(manager.cumulativeBolusPulsesDelivered).isEqualTo(25.toShort())
    }

    @Test
    fun `completeBolus records a new bolus when an older bolus was already completed`() {
        val manager = newManager()
        manager.cumulativeBolusPulsesDelivered = 10
        manager.lastBolusStartTime = 500L
        manager.lastBolusRequestedUnits = 2.0
        manager.lastBolusDeliveredUnits = 2.0

        manager.completeBolus(1_000L, 1.0, 0.75, 15)

        assertThat(manager.cumulativeBolusPulsesDelivered).isEqualTo(25.toShort())
        assertThat(manager.lastBolusStartTime).isEqualTo(1_000L)
        assertThat(manager.lastBolusRequestedUnits).isEqualTo(1.0)
        assertThat(manager.lastBolusDeliveredUnits).isEqualTo(0.75)
    }

    @Test
    fun `store() persists ltk but the logged copy strips it`() {
        val writer = newManager()
        writer.ltk = byteArrayOf(1, 2, 3, 4)

        val reader = newManager()
        assertThat(reader.ltk).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun `increaseMessageSequenceNumber wraps at 4 bits`() {
        val manager = newManager()
        manager.msgSequenceNumber = 0x0e

        manager.increaseMessageSequenceNumber()
        assertThat(manager.msgSequenceNumber).isEqualTo(0x0f.toByte())

        manager.increaseMessageSequenceNumber()
        assertThat(manager.msgSequenceNumber).isEqualTo(0x00.toByte())
    }

    @Test
    fun `reset clears activation progress, prime parameters, and dosing state`() {
        val manager = newManager()
        manager.activationProgress = ActivationProgress.COMPLETED
        manager.basalProgram = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))
        manager.primePulseRate = 20
        manager.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BASAL_PROGRAM, startedAt = 1L
        )
        manager.controllerId = 0x11223340L
        manager.podId = 0x11223341L
        manager.ltk = byteArrayOf(9)

        manager.reset()

        assertThat(manager.activationProgress).isEqualTo(ActivationProgress.NOT_STARTED)
        assertThat(manager.basalProgram).isNull()
        assertThat(manager.primePulseRate).isNull()
        assertThat(manager.pendingDoseCommand).isNull()
        assertThat(manager.ltk).isNull()
        assertThat(manager.controllerId).isEqualTo(0x11223340L)
        assertThat(manager.podId).isNull()
        assertThat(manager.nextPodId).isEqualTo(0x11223342L)
    }

    @Test
    fun `connection identity and counters round-trip`() {
        val writer = newManager()
        writer.bluetoothAddress = "AA:BB:CC:DD:EE:FF"
        writer.controllerId = 0x11223344L
        writer.podId = 0x55667788L
        writer.nextPodId = 0x11223345L
        writer.connectionAttempts = 3
        writer.successfulConnections = 2
        writer.eapAkaSequenceNumber = 42L

        val reader = newManager()

        assertThat(reader.bluetoothAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(reader.controllerId).isEqualTo(0x11223344L)
        assertThat(reader.podId).isEqualTo(0x55667788L)
        assertThat(reader.nextPodId).isEqualTo(0x11223345L)
        assertThat(reader.connectionAttempts).isEqualTo(3)
        assertThat(reader.successfulConnections).isEqualTo(2)
        assertThat(reader.eapAkaSequenceNumber).isEqualTo(42L)
    }

    @Test
    fun `updateFromPairing sets controllerId, podId, ltk, and msgSequenceNumber together and persists`() {
        val writer = newManager()

        val ltk = ByteArray(16) { it.toByte() }
        writer.updateFromPairing(
            controllerId = 0xAABBCCDDL,
            podId = 0x11223344L,
            pairResult = app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult(ltk = ltk, msgSeq = 7)
        )

        val reader = newManager()
        assertThat(reader.controllerId).isEqualTo(0xAABBCCDDL)
        assertThat(reader.podId).isEqualTo(0x11223344L)
        assertThat(reader.ltk).isEqualTo(ltk)
        assertThat(reader.msgSequenceNumber).isEqualTo(7.toByte())
    }

    @Test
    fun `updateFromVersionResponse persists the parsed status-version fields`() {
        val response = VersionResponse(hexToBytes("0115040A00010300040208146CC1000954D400FFFFFFFF"))
        val writer = newManager()

        writer.updateFromVersionResponse(response)

        val reader = newManager()
        assertThat(reader.podStatus).isEqualTo(response.podStatus)
        assertThat(reader.firmwareVersion).isNotNull()
        assertThat(reader.bleVersion).isNotNull()
        assertThat(reader.lotNumber).isEqualTo(response.lotNumber)
        assertThat(reader.podSequenceNumber).isEqualTo(response.podSequenceNumber)
        assertThat(reader.lastStatusResponseReceived).isNotNull()
    }

    @Test
    fun `updateFromDefaultStatusResponse persists the parsed status fields`() {
        val response = DefaultStatusResponse(hexToBytes("1D1800A02800000463FF"))
        val writer = newManager()

        writer.updateFromDefaultStatusResponse(response)

        val reader = newManager()
        assertThat(reader.podStatus).isEqualTo(response.podStatus)
        assertThat(reader.deliveryStatus).isEqualTo(response.deliveryStatus)
        assertThat(reader.totalPulsesDelivered).isEqualTo(response.totalPulsesDelivered)
        assertThat(response.reservoirPulsesRemaining).isEqualTo(1023.toShort())
        assertThat(reader.reservoirPulsesRemaining).isNull()
        assertThat(reader.minutesSinceActivation).isEqualTo(response.minutesSinceActivation)
    }

    @Test
    fun `reservoir retains a measured value when a later status reports above 50`() {
        val writer = newManager()
        writer.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1800A02800000463E8")))
        writer.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1800A02800000463FF")))
        writer.updateFromAlarmStatusResponse(AlarmStatusResponse(hexToBytes("021602080100000501BD00000003FF01950000000000670A")))

        assertThat(newManager().reservoirPulsesRemaining).isEqualTo(1000.toShort())
    }

    @Test
    fun `old saved above 50 reservoir reading is not treated as a measured value`() {
        val writer = newManager()
        writer.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1800A02800000463E8")))
        backingStore = requireNotNull(backingStore).replace(
            "\"reservoirPulsesRemaining\":1000", "\"reservoirPulsesRemaining\":1023"
        )

        assertThat(newManager().reservoirPulsesRemaining).isNull()
    }

    @Test
    fun `updateFromDefaultStatusResponse seeds basal expected from the new pulse count`() {
        val response = DefaultStatusResponse(hexToBytes("1D1800A02800000463FF"))
        val manager = newManager()
        manager.activationProgress = ActivationProgress.COMPLETED
        manager.cumulativeBolusPulsesDelivered = 0

        manager.updateFromDefaultStatusResponse(response)

        assertThat(manager.basalExpected).isEqualTo(
            response.totalPulsesDelivered * PodConstants.POD_PULSE_BOLUS_UNITS
        )
    }


    @Test
    fun `isPodKaput is true once a status response reports ALARM`() {
        val response = DefaultStatusResponse(hexToBytes("1D1D00A02800000463FF"))
        val manager = newManager()
        assertThat(manager.isPodKaput).isFalse()

        manager.updateFromDefaultStatusResponse(response)

        assertThat(manager.podStatus).isEqualTo(PodStatus.ALARM)
        assertThat(manager.isPodKaput).isTrue()
    }

    @Test
    fun `isPodKaput is true once a status response reports DEACTIVATED`() {
        val manager = newManager()

        manager.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1F00A02800000463FF")))

        assertThat(manager.podStatus).isEqualTo(PodStatus.DEACTIVATED)
        assertThat(manager.isPodKaput).isTrue()
    }

    @Test
    fun `activation time exceeded is terminal`() {
        val manager = newManager()

        manager.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1E00A02800000463FF")))

        assertThat(manager.podStatus).isEqualTo(PodStatus.LUMP_OF_COAL)
        assertThat(manager.isPodActivationTimeExceeded).isTrue()
        assertThat(manager.isPodKaput).isTrue()
    }

    @Test
    fun `isPodKaput stays false for a normally running pod`() {
        val manager = newManager()

        manager.updateFromDefaultStatusResponse(DefaultStatusResponse(hexToBytes("1D1800A02800000463FF")))

        assertThat(manager.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(manager.isPodKaput).isFalse()
    }

    @Test
    fun `updateFromAlarmStatusResponse persists the parsed alarm fields`() {
        val response = AlarmStatusResponse(hexToBytes("021602080100000501BD00000003FF01950000000000670A"))
        val writer = newManager()

        writer.updateFromAlarmStatusResponse(response)

        val reader = newManager()
        assertThat(reader.alarmType).isEqualTo(response.alarmType)
        assertThat(reader.alarmTime).isEqualTo(response.alarmTime)
        assertThat(reader.occlusionAlarm).isEqualTo(response.occlusionAlarm)
        assertThat(reader.podStatusWhenAlarmOccurred).isEqualTo(response.podStatusWhenAlarmOccurred)
        assertThat(reader.rssi).isEqualTo(response.rssi)
    }

    @Test
    fun `updateFromActivationTimeResponse persists podActivatedAt as an epoch millis Long`() {
        val bytes = byteArrayOf(
            0x02, 0x11, 0x05,
            0x14,
            0x00, 0x7D,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x07,
            0x0F,
            0x1A,
            0x09,
            0x1E
        )
        val response = PodInfoActivationTimeResponse(bytes)
        val writer = newManager()

        writer.updateFromActivationTimeResponse(response)

        val reader = newManager()
        assertThat(reader.alarmType).isEqualTo(AlarmType.ALARM_OCCLUDED)
        assertThat(reader.alarmTime).isEqualTo(125.toShort())
        val activatedAt = requireNotNull(reader.podActivatedAt)
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = activatedAt
        assertThat(calendar[java.util.Calendar.YEAR]).isEqualTo(2026)
        assertThat(calendar[java.util.Calendar.MONTH]).isEqualTo(java.util.Calendar.JULY)
        assertThat(calendar[java.util.Calendar.DAY_OF_MONTH]).isEqualTo(15)
    }

    @Test
    fun `updateFromTriggeredAlertsResponse persists the AlertType-to-Short map through Gson`() {
        val bytes = byteArrayOf(
            0x02, 0x13, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x0A,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x78,
            0x00, 0x00,
            0x00, 0x00,
            0x01, 0x2C
        )
        val response = PodInfoTriggeredAlertsResponse(bytes)
        val writer = newManager()

        writer.updateFromTriggeredAlertsResponse(response)

        val reader = newManager()
        val triggered = requireNotNull(reader.triggeredAlertTimes)
        assertThat(triggered).containsExactly(
            AlertType.MULTI_COMMAND, 10.toShort(),
            AlertType.LOW_RESERVOIR, 120.toShort(),
            AlertType.EXPIRATION, 300.toShort()
        )
    }

    @Test
    fun `load() falls back to defaults when the stored JSON is corrupted`() {
        backingStore = "{ not valid json"

        val manager = newManager()

        assertThat(manager.activationProgress).isEqualTo(ActivationProgress.NOT_STARTED)
        assertThat(manager.ltk).isNull()
    }
}
