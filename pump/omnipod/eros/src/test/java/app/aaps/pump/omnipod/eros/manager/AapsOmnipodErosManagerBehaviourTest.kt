package app.aaps.pump.omnipod.eros.manager

import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.R as CommonR
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.definition.PodHistoryEntryType
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType
import app.aaps.pump.omnipod.eros.driver.definition.FaultEventCode
import app.aaps.pump.omnipod.eros.driver.exception.CrcMismatchException
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageAddressException
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageSequenceNumberException
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPacketTypeException
import app.aaps.pump.omnipod.eros.driver.exception.IllegalResponseException
import app.aaps.pump.omnipod.eros.driver.exception.MessageDecodingException
import app.aaps.pump.omnipod.eros.driver.exception.NonceOutOfSyncException
import app.aaps.pump.omnipod.eros.driver.exception.NonceResyncException
import app.aaps.pump.omnipod.eros.driver.exception.NotEnoughDataException
import app.aaps.pump.omnipod.eros.driver.exception.PodFaultException
import app.aaps.pump.omnipod.eros.driver.exception.PodReturnedErrorResponseException
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.driver.manager.OmnipodManager
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Behaviour pins for [AapsOmnipodErosManager], written against the **Java** implementation.
 *
 * This class is the Eros command layer - bolus, temporary basal, cannula, alerts - and it is the last
 * substantial Java file left in the DI migration. Before it is rewritten in Kotlin, the parts a
 * translation could quietly get wrong need to be nailed down against the original, so the tests
 * describe what the Java does rather than what a rewrite happens to do.
 *
 * Two areas are covered, both chosen because a mistake in them is silent rather than loud:
 *
 *  1. **[AapsOmnipodErosManager.translateException]** decides what the user is told when a pod command
 *     fails. Every branch is one `instanceof` against a driver exception, and the ordering matters -
 *     `IllegalResponseException` sits below `IllegalVersionResponseTypeException`, and several
 *     exceptions deliberately share one message. Reordering or merging branches during a rewrite would
 *     change the message with nothing failing to compile. The tests assert **which resource** is used,
 *     not its wording, so they pin the mapping without breaking on a copy edit.
 *  2. **The suspended fake TBR**, which is how the driver tells AAPS that delivery is suspended. If
 *     [AapsOmnipodErosManager.hasSuspendedFakeTbr] is wrong, the app either believes insulin is
 *     suspended when it is running, or misses that it is suspended - and its answer depends on a
 *     three-step lookup through `PumpSync` and the pod history that is easy to get subtly wrong.
 */
internal class AapsOmnipodErosManagerBehaviourTest {

    private val podStateManager: ErosPodStateManager = mock()
    private val erosHistory: ErosHistory = mock()
    private val pumpSync: PumpSync = mock()
    private val rh: ResourceHelper = mock()
    private val preferences: Preferences = mock()

    /** Injected now, so the whole command surface can be driven from a test. */
    private val delegate: OmnipodManager = mock()

    // RETURNS_SELF because PumpEnactResult is a builder - `.success(x).enacted(y)` has to chain. One
    // shared instance, so a test can verify what the command reported on it.
    private val enactResult: PumpEnactResult = mock(defaultAnswer = Mockito.RETURNS_SELF)

    /**
     * Resolves a string to the **id** that was asked for.
     *
     * The assertions then read `R.string.x`, which is what the mapping is really about; the wording of
     * the string is a translation concern and would only make these tests brittle.
     */
    private fun sut(): AapsOmnipodErosManager {
        // Both overloads: the Java calls `rh.gs(id, args)` with an empty array, which binds to the
        // vararg one with zero arguments - not to `gs(id)`.
        whenever(rh.gs(any<Int>())).doAnswer { it.getArgument<Int>(0).toString() }
        whenever(rh.gs(any<Int>(), anyVararg())).doAnswer { it.getArgument<Int>(0).toString() }
        return AapsOmnipodErosManager(
            delegate,
            podStateManager,
            erosHistory,
            mock<AapsOmnipodUtil>(),
            mock<AAPSLogger>(),
            mock<RxBus>(),
            preferences,
            rh,
            mock<OmnipodAlertUtil>(),
            pumpSync,
            mock<UiInteraction>(),
            mock<NotificationManager>(),
            { enactResult },
            mock<ConcentrationHelper>(),
            mock<BolusProgressData>()
        )
    }

    private fun expect(ex: Throwable, resourceId: Int) {
        assertThat(sut().translateException(ex)).isEqualTo(resourceId.toString())
    }

    // ---- translateException ----------------------------------------------------------------------

    @Test fun `crc mismatch reports the crc message`() =
        expect(CrcMismatchException(1, 2), R.string.omnipod_eros_error_crc_mismatch)

    @Test fun `illegal packet type reports the invalid packet type message`() =
        expect(IllegalPacketTypeException(null, null), R.string.omnipod_eros_error_invalid_packet_type)

    @Test fun `nonce out of sync and nonce resync are different messages`() {
        expect(NonceOutOfSyncException(), R.string.omnipod_eros_error_nonce_out_of_sync)
        expect(NonceResyncException(), R.string.omnipod_eros_error_nonce_resync_failed)
    }

    @Test fun `message decoding and not enough data are different messages`() {
        expect(MessageDecodingException("boom"), R.string.omnipod_eros_error_message_decoding_failed)
        expect(NotEnoughDataException(byteArrayOf()), R.string.omnipod_eros_error_not_enough_data)
    }

    @Test fun `message sequence number and message address are different messages`() {
        expect(IllegalMessageSequenceNumberException(1, 2), R.string.omnipod_eros_error_invalid_message_sequence_number)
        expect(IllegalMessageAddressException(1, 2), R.string.omnipod_eros_error_invalid_message_address)
    }

    @Test fun `pod returned error response has its own message`() =
        expect(PodReturnedErrorResponseException(mock()), R.string.omnipod_eros_error_pod_returned_error_response)

    /**
     * `IllegalResponseException` is checked **after** `IllegalVersionResponseTypeException` and both map
     * to the same resource, so a rewrite that reorders or merges them looks correct either way. Pinned
     * so the shared answer is a decision rather than an accident.
     */
    @Test fun `an illegal response reports the invalid response message`() =
        expect(IllegalResponseException("SomeClass", MessageBlockType.ERROR_RESPONSE), R.string.omnipod_eros_error_invalid_response)

    /** A pod fault names the fault code and its number - the only branch that formats the fault in. */
    @Test fun `a pod fault reports the pod fault message`() {
        val ex: PodFaultException = mock()
        val detailedStatus: app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoDetailedStatus = mock()
        whenever(detailedStatus.faultEventCode).thenReturn(FaultEventCode.FAILED_FLASH_ERASE)
        whenever(ex.detailedStatus).thenReturn(detailedStatus)

        expect(ex, R.string.omnipod_eros_error_pod_fault)
    }

    /** Anything the chain does not recognise still produces a message rather than throwing. */
    @Test fun `an unrecognised exception falls back to the unexpected exception message`() =
        expect(IllegalStateException("nope"), CommonR.string.omnipod_common_error_unexpected_exception)

    // ---- the suspended fake TBR ------------------------------------------------------------------

    private fun pumpStateWith(pumpId: Long?): PumpSync.PumpState {
        val tbr: PumpSync.PumpState.TemporaryBasal? = pumpId?.let {
            mock<PumpSync.PumpState.TemporaryBasal>().apply { whenever(this.pumpId).thenReturn(it) }
        }
        return mock<PumpSync.PumpState>().apply {
            whenever(temporaryBasal).thenReturn(tbr)
            whenever(serialNumber).thenReturn("1234")
        }
    }

    private fun givenPumpState(state: PumpSync.PumpState) {
        pumpSync.stub { onBlocking { expectedPumpState() } doReturn state }
    }

    @Test fun `no temporary basal at all means no suspended fake tbr`() {
        givenPumpState(pumpStateWith(pumpId = null))

        assertThat(sut().hasSuspendedFakeTbr()).isFalse()
    }

    /**
     * A running TBR that AAPS did not get from a pump id cannot be matched to a history record, so it
     * must not be mistaken for the fake one - that would report the pod as suspended while it delivers.
     */
    @Test fun `a temporary basal with no pump id is not the fake one`() {
        val tbr: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(tbr.pumpId).thenReturn(null)
        val state: PumpSync.PumpState = mock()
        whenever(state.temporaryBasal).thenReturn(tbr)
        givenPumpState(state)

        assertThat(sut().hasSuspendedFakeTbr()).isFalse()
    }

    @Test fun `a temporary basal whose history record is a real tbr is not the fake one`() {
        givenPumpState(pumpStateWith(pumpId = 42L))
        whenever(erosHistory.findErosHistoryRecordByPumpId(42L))
            .thenReturn(ErosHistoryRecordEntity(0L, PodHistoryEntryType.SET_TEMPORARY_BASAL.code.toLong()))

        assertThat(sut().hasSuspendedFakeTbr()).isFalse()
    }

    @Test fun `a temporary basal whose history record is the fake suspend is the fake one`() {
        givenPumpState(pumpStateWith(pumpId = 42L))
        whenever(erosHistory.findErosHistoryRecordByPumpId(42L))
            .thenReturn(ErosHistoryRecordEntity(0L, PodHistoryEntryType.SET_FAKE_SUSPENDED_TEMPORARY_BASAL.code.toLong()))

        assertThat(sut().hasSuspendedFakeTbr()).isTrue()
    }

    /** No history record for that pump id - the TBR came from somewhere else, so it is not the fake. */
    @Test fun `a temporary basal with no history record is not the fake one`() {
        givenPumpState(pumpStateWith(pumpId = 42L))
        whenever(erosHistory.findErosHistoryRecordByPumpId(42L)).thenReturn(null)

        assertThat(sut().hasSuspendedFakeTbr()).isFalse()
    }

    /** Both guards are "if not exists" / "if exists": calling them against the wrong state does nothing. */
    @Test fun `creating the fake tbr when one already exists writes nothing`() {
        givenPumpState(pumpStateWith(pumpId = 42L))
        whenever(erosHistory.findErosHistoryRecordByPumpId(42L))
            .thenReturn(ErosHistoryRecordEntity(0L, PodHistoryEntryType.SET_FAKE_SUSPENDED_TEMPORARY_BASAL.code.toLong()))

        sut().createSuspendedFakeTbrIfNotExists()

        verifyBlocking(pumpSync, never()) {
            syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), anyOrNull())
        }
    }

    @Test fun `cancelling the fake tbr when none exists writes nothing`() {
        givenPumpState(pumpStateWith(pumpId = null))

        sut().cancelSuspendedFakeTbrIfExists()

        verifyBlocking(pumpSync, never()) {
            syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
        }
    }

    // ---- command paths ---------------------------------------------------------------------------

    /**
     * Every command follows one shape: run it on the delegate, then write a history record and report
     * the outcome. These pin that shape rather than the pod protocol, because the shape is what a
     * rewrite restructures - and a lost history record or an inverted success flag is invisible.
     */
    private fun historyRecords(): List<ErosHistoryRecordEntity> {
        val captor = argumentCaptor<ErosHistoryRecordEntity>()
        verify(erosHistory, atLeastOnce()).create(captor.capture())
        return captor.allValues
    }

    @Test fun `acknowledging alerts records a success when the pod accepts it`() {
        val result = sut().acknowledgeAlerts()

        verify(result).success(true)
        verify(result).enacted(true)
        assertThat(historyRecords().single().isSuccess).isTrue()
        assertThat(historyRecords().single().podEntryTypeCode)
            .isEqualTo(PodHistoryEntryType.ACKNOWLEDGE_ALERTS.code.toLong())
    }

    /**
     * The failure branch has to do three things and a rewrite can drop any one silently: report failure,
     * carry the translated message back to the user, and still leave a record in the pod history.
     */
    @Test fun `a failed command reports the translated message and still records the failure`() {
        whenever(delegate.acknowledgeAlerts()).thenThrow(NonceOutOfSyncException())

        val result = sut().acknowledgeAlerts()

        verify(result).success(false)
        verify(result).enacted(false)
        verify(result).comment(R.string.omnipod_eros_error_nonce_out_of_sync.toString())
        assertThat(historyRecords().single().isSuccess).isFalse()
    }

    /**
     * Telling AAPS the TBR is gone is a separate call from cancelling it on the pod. If a rewrite keeps
     * the pod call and loses this one, the pod stops the TBR while the app goes on showing it running.
     */
    @Test fun `cancelling a temporary basal tells AAPS the tbr stopped`() {
        sut().cancelTemporaryBasal()

        verifyBlocking(pumpSync) {
            syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
        }
    }

    @Test fun `a failed temporary basal cancel does not tell AAPS the tbr stopped`() {
        whenever(delegate.cancelTemporaryBasal(any())).thenThrow(NonceOutOfSyncException())

        val result = sut().cancelTemporaryBasal()

        verify(result).success(false)
        verifyBlocking(pumpSync, never()) {
            syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
        }
    }

    /**
     * Discarding the pod has to raise the fake suspended TBR, because from that moment nothing is
     * delivering. Without it AAPS keeps calculating as though basal were still running.
     */
    @Test fun `discarding the pod raises the suspended fake tbr`() {
        givenPumpState(pumpStateWith(pumpId = null))

        sut().discardPodState()

        verifyBlocking(pumpSync) {
            syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), anyOrNull())
        }
    }

    // ---- serial number ---------------------------------------------------------------------------

    /** "-" rather than an address, so a screen never shows a stale serial from a discarded pod. */
    @Test fun `serial number is a dash until the pod is initialized`() {
        whenever(podStateManager.isPodInitialized).thenReturn(false)

        assertThat(sut().serialNumber()).isEqualTo("-")
    }

    @Test fun `serial number is the pod address once initialized`() {
        whenever(podStateManager.isPodInitialized).thenReturn(true)
        whenever(podStateManager.address).thenReturn(520760124)

        assertThat(sut().serialNumber()).isEqualTo("520760124")
    }
}
