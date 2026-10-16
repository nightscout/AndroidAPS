package app.aaps.pump.omnipod.omnipod5.bledriver.pod.state

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class O5BolusAccountingTest : TestBase() {

    @Mock lateinit var storage: Preferences
    private var savedState: String? = null

    @BeforeEach
    fun setup() {
        whenever(storage.put(eq(O5StringNonPreferenceKey.PodState), any())).thenAnswer {
            savedState = it.getArgument(1)
            Unit
        }
        whenever(storage.getIfExists(O5StringNonPreferenceKey.PodState)).thenAnswer { savedState }
        whenever(storage.get(O5StringNonPreferenceKey.PodState)).thenAnswer { savedState ?: "" }
    }

    private fun manager(persisted: Boolean): O5PodStateManager =
        if (persisted) PersistedO5PodStateManager(aapsLogger, storage) else InMemoryO5PodStateManager()

    private fun status(total: Int, remaining: Int, sequence: Int = 4, delivery: DeliveryStatus = DeliveryStatus.BOLUS_AND_BASAL_ACTIVE) =
        DefaultStatusResponse(
            ByteBuffer.allocate(10)
                .put(0x1d.toByte())
                .put(((delivery.value.toInt() shl 4) or 8).toByte())
                .putInt((total shl 15) or (sequence shl 11) or remaining)
                .putInt(1000)
                .array()
        )

    private fun pending() = O5PodStateManager.PendingDoseCommand(
        type = O5PodStateManager.PendingDoseType.BOLUS,
        requestedUnits = 1.0,
        startedAt = 1000L,
        sequenceNumber = 4
    )

    private fun started(persisted: Boolean) = manager(persisted).also {
        it.activationProgress = ActivationProgress.COMPLETED
        it.cumulativeBolusPulsesDelivered = 10
        it.updateFromDefaultStatusResponse(status(110, 0, delivery = DeliveryStatus.BASAL_ACTIVE))
        it.pendingDoseCommand = pending()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `active and repeated status add only bolus progress and leave concurrent basal in basal`(persisted: Boolean) {
        val state = started(persisted)
        state.updateFromDefaultStatusResponse(status(116, 16))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(4)
        assertThat(state.basalPulsesDelivered).isEqualTo(102.toShort())
        assertThat(state.basalDrift).isWithin(1e-9).of(0.1)
        assertThat(state.lastBolusDeliveredUnits).isNull()

        state.updateFromDefaultStatusResponse(status(116, 16))
        state.updateFromDefaultStatusResponse(status(119, 16))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
        assertThat(state.basalPulsesDelivered).isEqualTo(105.toShort())

        state.updateFromDefaultStatusResponse(status(126, 10))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(20.toShort())
        assertThat(state.basalPulsesDelivered).isEqualTo(106.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `completion status accounts before finalization and remains idempotent after restart`(persisted: Boolean) {
        var state = started(persisted)
        state.updateFromDefaultStatusResponse(status(114, 16))
        if (persisted) state = manager(true)
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(4)
        state.updateFromDefaultStatusResponse(status(114, 16))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())

        state.updateFromDefaultStatusResponse(status(130, 0, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(30.toShort())
        assertThat(state.basalPulsesDelivered).isEqualTo(100.toShort())
        assertThat(state.basalDrift).isWithin(1e-9).of(0.0)
        if (persisted) state = manager(true)
        state.completeBolus(1000L, 1.0, 1.0, 20)
        if (persisted) state = manager(true)
        state.completeBolus(1000L, 1.0, 1.0, 20)
        state.updateFromDefaultStatusResponse(status(130, 0, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(30.toShort())
        assertThat(state.pendingDoseCommand).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `finalization adds only progress not yet accounted`(persisted: Boolean) {
        var state = started(persisted)
        state.updateFromDefaultStatusResponse(status(114, 16))
        if (persisted) state = manager(true)
        state.completeBolus(1000L, 1.0, 0.4, 8)
        state.completeBolus(1000L, 1.0, 0.4, 8)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(18.toShort())
        assertThat(state.lastBolusDeliveredUnits).isEqualTo(0.4)
        assertThat(state.pendingDoseCommand).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `partial cancellation keeps progress through stop retries and restart`(persisted: Boolean) {
        var state = started(persisted)
        state.updateFromDefaultStatusResponse(status(114, 16))
        state.pendingDoseCommand = state.pendingDoseCommand!!.copy(bolusCancelRequested = true, bolusCancelSequenceNumber = 6)
        if (persisted) state = manager(true)
        state.updateFromDefaultStatusResponse(status(118, 12, sequence = 6, delivery = DeliveryStatus.BASAL_ACTIVE))
        state.pendingDoseCommand = state.pendingDoseCommand!!.copy(bolusCancelSequenceNumber = 7)
        state.updateFromDefaultStatusResponse(status(118, 12, sequence = 7, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(18.toShort())
        assertThat(state.basalPulsesDelivered).isEqualTo(100.toShort())
        if (persisted) state = manager(true)
        state.completeBolus(1000L, 1.0, 0.4, 8)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(18.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lost response can be accounted from matching completion status`(persisted: Boolean) {
        val state = started(persisted)
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isFalse()
        state.updateFromDefaultStatusResponse(status(130, 0, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(30.toShort())
        assertThat(state.basalPulsesDelivered).isEqualTo(100.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `active delivery is positive evidence even without a matching sequence`(persisted: Boolean) {
        val state = started(persisted)
        state.updateFromDefaultStatusResponse(status(114, 16, sequence = 9))
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `unmatched inactive and unknown statuses cannot account requested insulin`(persisted: Boolean) {
        val state = started(persisted)
        state.updateFromDefaultStatusResponse(status(110, 0, sequence = 9, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isFalse()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(10.toShort())

        state.updateFromDefaultStatusResponse(status(110, 0, delivery = DeliveryStatus.UNKNOWN))
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(0)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(10.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `stop sequence alone does not prove unknown bolus delivery`(persisted: Boolean) {
        var state = started(persisted)
        state.pendingDoseCommand = pending().copy(bolusCancelRequested = true, bolusCancelSequenceNumber = 6)
        if (persisted) state = manager(true)
        state.updateFromDefaultStatusResponse(status(110, 0, sequence = 6, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isFalse()
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(0)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(10.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `saved acceptance does not apply stale remaining pulses from unrelated status`(persisted: Boolean) {
        var state = started(persisted)
        state.updateFromDefaultStatusResponse(status(114, 16))
        if (persisted) state = manager(true)
        state.updateFromDefaultStatusResponse(status(114, 0, sequence = 9, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(4)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `remaining count cannot move progress backwards or beyond the requested bolus`(persisted: Boolean) {
        val state = started(persisted)
        state.updateFromDefaultStatusResponse(status(114, 16))
        state.updateFromDefaultStatusResponse(status(114, 18))
        state.updateFromDefaultStatusResponse(status(114, 21))
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(4)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `basal correction pulses stay basal in status and finalization`(persisted: Boolean) {
        val state = started(persisted)
        state.pendingDoseCommand = pending().copy(isBasalCorrection = true)
        state.updateFromDefaultStatusResponse(status(114, 16))
        state.updateFromDefaultStatusResponse(status(130, 0, delivery = DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(0)
        state.completeBolus(1000L, 1.0, 1.0, null)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(10.toShort())
        assertThat(state.basalPulsesDelivered).isEqualTo(120.toShort())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `alarm status shares the ordinary bolus progress accounting`(persisted: Boolean) {
        val state = started(persisted)
        val bytes = ByteArray(24)
        bytes[0] = 2
        bytes[1] = 22
        bytes[3] = 13
        bytes[4] = DeliveryStatus.SUSPENDED.value
        ByteBuffer.wrap(bytes).putShort(5, 12).put(7, 4).putShort(8, 118)
        val response = AlarmStatusResponse(bytes)
        state.updateFromAlarmStatusResponse(response)
        state.updateFromAlarmStatusResponse(response)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(18.toShort())
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(8)
        assertThat(state.basalPulsesDelivered).isEqualTo(100.toShort())
    }

    @Test
    fun `older pending state without progress starts at zero and survives replay`() {
        started(true)
        savedState = requireNotNull(savedState).replace(",\"accountedBolusPulses\":0", "")
        val restored = manager(true)
        assertThat(restored.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(0)
        restored.updateFromDefaultStatusResponse(status(114, 16))
        val restarted = manager(true)
        restarted.updateFromDefaultStatusResponse(status(114, 16))
        assertThat(restarted.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
    }
}
