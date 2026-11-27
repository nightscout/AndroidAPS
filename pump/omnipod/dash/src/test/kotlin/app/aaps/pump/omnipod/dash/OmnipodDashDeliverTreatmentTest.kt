package app.aaps.pump.omnipod.dash

import android.os.SystemClock
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.keys.DashStringNonPreferenceKey
import app.aaps.pump.omnipod.common.queue.command.CommandDeliverBasalCorrection
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * What `deliverTreatment` answers for a bolus, with the real pod state manager and a pod that is played by
 * the mocked [OmnipodDashManager].
 *
 * The answer decides what AAPS does next: on success the command queue stores the carbs of the meal, on a
 * failure it rings the bolus error alarm. So a bolus the pod never got must not be answered as a success,
 * and a bolus the pod did deliver must not be answered as a failure.
 */
class OmnipodDashDeliverTreatmentTest {

    private lateinit var omnipodManager: OmnipodDashManager
    private lateinit var podStateManager: OmnipodDashPodStateManagerImpl
    private lateinit var pumpSync: PumpSync
    private lateinit var sut: OmnipodDashPumpPlugin

    // What the played pod knows. A status read reports these values.
    private var podLastCommandSequence = 5
    private var podBolusPulsesRemaining = 0
    private var statusReadsThatFail = 0

    // region the pod never got the bolus command

    /**
     * The bolus command is sent, the link drops and the pod never gets the command. The status read after
     * the wait shows that the pod denied it. The pod still remembers the bolus before this one, and that
     * amount must not be reported as the amount of this bolus.
     */
    @Test
    fun `a bolus the pod never got is reported as failed with nothing delivered`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false)

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        // The cause: only a confirmed bolus replaces lastBolus, so it is still the earlier one.
        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(EARLIER_BOLUS_HISTORY_ID)
        assertThat(podStateManager.activeCommand).isNull()
        assertThat(result.bolusDelivered).isEqualTo(0.0)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    /** The bolus error alarm shows the comment of the result, so the reason must be in it. */
    @Test
    fun `a bolus the pod never got tells the user why it failed`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false)

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo(NOT_ACCEPTED_TEXT)
    }

    /**
     * A guard only: this case failed before the fix too, because there was no earlier bolus to read and the
     * code ran into a null. It must stay a failure.
     */
    @Test
    fun `a bolus the pod never got fails also when the pod has no earlier bolus`() = runBlocking<Unit> {
        givenPod(withEarlierBolus = false)
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false)

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(result.bolusDelivered).isEqualTo(0.0)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    /** The bolus was announced to AAPS with the full amount when it was sent. The denial must set it to 0 U. */
    @Test
    fun `a bolus the pod never got is synced as 0 U`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false)

        sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(BOLUS_CREATED_AT), argThat { cU == 0.0 }, isNull(), eq(BOLUS_HISTORY_ID), any(), any()) }
    }

    @Test
    fun `an SMB the pod never got is reported as failed with nothing delivered`() = runBlocking<Unit> {
        givenPod(bolusType = BolusType.SMB)
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false)

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS; bolusType = BS.Type.SMB })

        assertThat(result.bolusDelivered).isEqualTo(0.0)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    /**
     * A basal correction is one pulse sent as a bolus, and it uses the same wait. It is never announced to
     * AAPS as a bolus, so its denial must not be synced as one either.
     */
    @Test
    fun `a basal correction the pod never got is reported as failed and is not synced`() = runBlocking<Unit> {
        // 220 basal pulses are 11.00 U and 11.05 U were expected: one pulse too little, so a correction is due.
        givenPod(
            bolusType = BolusType.BASAL_CORRECTION,
            bolusUnits = PodConstants.POD_PULSE_BOLUS_UNITS,
            moreStoredState = """, "pulsesDelivered": 320, "bolusPulsesDelivered": 100, "basalExpected": 11.05"""
        )
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false)

        val result = sut.executeCustomCommand(CommandDeliverBasalCorrection())

        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(EARLIER_BOLUS_HISTORY_ID)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) }
    }

    /**
     * A guard: the user presses stop while the command is sent, and the bolus then ends in an error (here
     * the pod never got it). On dev the error result was built before the bolus started, so it read
     * "cancelled" as false and the answer was: failed, not enacted, 0 U. That must stay, because a success
     * would let the command queue store the carbs and skip the bolus error.
     */
    @Test
    fun `a bolus that fails after the user cancelled it is still reported as failed`() = runBlocking<Unit> {
        givenPod(withEarlierBolus = false)
        givenBolusCommandIsSentButNotAnswered(podGetsIt = false, userCancelsDuringSend = true)
        givenCancelCommandIsSentAndAnswered(pulsesNotDelivered = 0)

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isEqualTo(0.0)
    }

    // endregion

    // region the pod got the bolus command

    /** The normal case must stay as it is: the pod confirms the command and later reports it as delivered. */
    @Test
    fun `a bolus the pod confirmed is reported as delivered`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentAndAnswered()

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(BOLUS_HISTORY_ID)
        assertThat(result.bolusDelivered).isEqualTo(REQUESTED_UNITS)
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    /**
     * The pod gets the bolus, but its answer is lost. Only the status read after the wait confirms the
     * command. That read is applied before the new lastBolus is made, so the new lastBolus is not marked
     * complete yet. The bolus was delivered and must not be reported as failed: the user could bolus again.
     */
    @Test
    fun `a bolus confirmed only by the status read after the wait is reported as delivered`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentButNotAnswered(podGetsIt = true)

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(BOLUS_HISTORY_ID)
        assertThat(podStateManager.lastBolus?.deliveryComplete).isTrue()
        assertThat(result.bolusDelivered).isEqualTo(REQUESTED_UNITS)
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    /**
     * As above, but the confirming status read is the last try, so no read is left to mark the bolus
     * complete. The pod accepted the command and is not delivering any more, so the requested amount is
     * reported. It is also the amount that was synced when the bolus was sent.
     */
    @Test
    fun `a bolus confirmed by the last status read is reported with the requested amount`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentButNotAnswered(podGetsIt = true)
        statusReadsThatFail = 4

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(BOLUS_HISTORY_ID)
        assertThat(result.bolusDelivered).isEqualTo(REQUESTED_UNITS)
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    /** The user stops the bolus during the wait. The pod reports the pulses it did not deliver. */
    @Test
    fun `a bolus the user cancelled is reported with the part that was delivered`() = runBlocking<Unit> {
        givenPod()
        givenBolusCommandIsSentAndAnswered()
        givenCancelCommandIsSentAndAnswered(pulsesNotDelivered = 4)
        sut.waitForDelivery = { sut.stopBolusDelivering() }

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        // 0.5 U were asked for and 4 pulses (0.2 U) were not delivered.
        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(BOLUS_HISTORY_ID)
        assertThat(result.bolusDelivered).isWithin(TOLERANCE).of(0.3)
        assertThat(result.success).isTrue()
        verifyBlocking(pumpSync) {
            syncBolusWithPumpId(eq(BOLUS_CREATED_AT), argThat { abs(cU - 0.3) < TOLERANCE }, eq(BS.Type.NORMAL), eq(BOLUS_HISTORY_ID), any(), any())
        }
    }

    // endregion

    /**
     * Builds the plugin around a pod state that is loaded the way the app loads it: from the stored JSON.
     *
     * In a unit test `SystemClock.elapsedRealtime()` always gives 0. The stored state says the last status
     * came at -1, so a command made now (at 0) is newer than it, as it is on a phone.
     *
     * The next command gets sequence number 3. The pod reports 5 as its last command until it gets a new one.
     */
    private fun givenPod(
        withEarlierBolus: Boolean = true,
        bolusType: BolusType = BolusType.DEFAULT,
        bolusUnits: Double = REQUESTED_UNITS,
        moreStoredState: String = ""
    ) {
        val earlierBolus =
            if (withEarlierBolus) """, "lastBolus": {"startTime": 1, "requestedUnits": $EARLIER_BOLUS_UNITS, "bolusUnitsRemaining": 0.0, """ +
                """"deliveryComplete": true, "historyId": $EARLIER_BOLUS_HISTORY_ID, "bolusType": "DEFAULT"}"""
            else ""
        val storedPodState = """{"activationProgress": "COMPLETED", "lastStatusResponseReceived": -1, """ +
            """"messageSequenceNumber": 3$earlierBolus$moreStoredState}"""
        val preferences: Preferences = mock()
        whenever(preferences.getIfExists(DashStringNonPreferenceKey.PodState)).thenReturn(storedPodState)
        whenever(preferences.get(DashStringNonPreferenceKey.PodState)).thenReturn(storedPodState)

        val rh: ResourceHelper = mock()
        whenever(rh.gs(any<Int>())).thenReturn("text")
        whenever(rh.gs(R.string.omnipod_dash_bolus_not_accepted)).thenReturn(NOT_ACCEPTED_TEXT)

        val history: DashHistory = mock()
        whenever(history.createRecord(eq(OmnipodCommandType.SET_BOLUS), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Single.just(BOLUS_HISTORY_ID))
        whenever(history.createRecord(eq(OmnipodCommandType.CANCEL_BOLUS), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Single.just(CANCEL_HISTORY_ID))
        whenever(history.updateFromState(any())).thenReturn(Completable.complete())
        whenever(history.getById(BOLUS_HISTORY_ID)).thenReturn(historyRecord(BOLUS_HISTORY_ID, OmnipodCommandType.SET_BOLUS, BolusRecord(bolusUnits, bolusType)))
        whenever(history.getById(CANCEL_HISTORY_ID)).thenReturn(historyRecord(CANCEL_HISTORY_ID, OmnipodCommandType.CANCEL_BOLUS, null))

        pumpSync = mock {
            onBlocking { expectedPumpState() } doReturn PumpSync.PumpState(null, null, null, null, "")
            onBlocking { syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) } doReturn true
        }

        omnipodManager = mock()
        whenever(omnipodManager.getStatus(any())).thenReturn(
            Observable.defer {
                if (statusReadsThatFail > 0) {
                    statusReadsThatFail--
                    Observable.error(IOException("no link"))
                } else {
                    podStateManager.updateFromDefaultStatusResponse(podStatus())
                    Observable.empty<PodEvent>()
                }
            }
        )

        podStateManager = OmnipodDashPodStateManagerImpl(mock<AAPSLogger>(), mock<RxBus>(), preferences)
        sut = OmnipodDashPumpPlugin(
            aapsLogger = mock<AAPSLogger>(),
            rh = rh,
            preferences = preferences,
            commandQueue = mock<CommandQueue>(),
            omnipodManager = omnipodManager,
            podStateManager = podStateManager,
            history = history,
            pumpSync = pumpSync,
            rxBus = mock<RxBus>(),
            aapsSchedulers = mock<AapsSchedulers>(),
            uiInteraction = mock<UiInteraction>(),
            notificationManager = mock<NotificationManager>(),
            pumpEnactResultProvider = { FakePumpEnactResult() },
            bolusProgressData = mock<BolusProgressData>(),
            dashHistoryDatabase = mock<DashHistoryDatabase>(),
            protectionCheck = mock<ProtectionCheck>(),
            blePreCheck = mock<BlePreCheck>()
        )
        // Do not wait the real delivery time.
        sut.waitForDelivery = { }
    }

    /**
     * The command leaves the phone, then the link drops and no answer comes.
     *
     * @param podGetsIt false: the pod never got the command. true: the pod got it and only its answer is lost.
     * @param userCancelsDuringSend true: the user presses stop while the command is on its way.
     */
    private fun givenBolusCommandIsSentButNotAnswered(podGetsIt: Boolean, userCancelsDuringSend: Boolean = false) {
        whenever(omnipodManager.bolus(any(), any(), any())).thenReturn(
            Observable.defer {
                markCommandSent()
                if (podGetsIt) podGetsCommand()
                if (userCancelsDuringSend) sut.stopBolusDelivering()
                Observable.concat(
                    Observable.just<PodEvent>(PodEvent.CommandSent(mock<Command>())),
                    Observable.error(IOException("link lost"))
                )
            }
        )
    }

    /** The pod answers the bolus command with a status that names this command as its last one. */
    private fun givenBolusCommandIsSentAndAnswered() {
        whenever(omnipodManager.bolus(any(), any(), any())).thenReturn(
            Observable.defer {
                markCommandSent()
                podGetsCommand()
                podStateManager.updateFromDefaultStatusResponse(podStatus())
                Observable.just<PodEvent>(PodEvent.CommandSent(mock<Command>()))
            }
        )
    }

    /** The pod stops the bolus and answers with the pulses it did not deliver. */
    private fun givenCancelCommandIsSentAndAnswered(pulsesNotDelivered: Int) {
        whenever(omnipodManager.stopBolus(any())).thenReturn(
            Observable.defer {
                markCommandSent()
                podGetsCommand()
                podBolusPulsesRemaining = pulsesNotDelivered
                podStateManager.updateFromDefaultStatusResponse(podStatus())
                Observable.just<PodEvent>(PodEvent.CommandSent(mock<Command>()))
            }
        )
    }

    /** What the real manager does on its `CommandSent` event. */
    private fun markCommandSent() {
        podStateManager.activeCommand!!.sentRealtime = SystemClock.elapsedRealtime()
        podStateManager.increaseMessageSequenceNumber()
    }

    private fun podGetsCommand() {
        podLastCommandSequence = podStateManager.activeCommand!!.sequence.toInt()
    }

    /**
     * A status as the pod sends it: pod running, basal active and no bolus active, 320 pulses delivered in
     * total, plus the last command and the bolus pulses left that the played pod has now.
     */
    private fun podStatus(): DefaultStatusResponse {
        val pulsesCommandAndBolus = (320 shl 15) or (podLastCommandSequence shl 11) or podBolusPulsesRemaining
        return DefaultStatusResponse(ByteBuffer.allocate(10).put(0x1D).put(0x18).putInt(pulsesCommandAndBolus).putInt(0x000463FF).array())
    }

    private fun historyRecord(id: Long, commandType: OmnipodCommandType, record: BolusRecord?) = HistoryRecord(
        id = id, createdAt = BOLUS_CREATED_AT, date = BOLUS_CREATED_AT, commandType = commandType, initialResult = InitialResult.SENT,
        record = record, totalAmountDelivered = null, resolvedResult = null, resolvedAt = null
    )

    private class FakePumpEnactResult : PumpEnactResult {

        override var success = false
        override var enacted = false
        override var comment = ""
        override var duration = -1
        override var absolute = -1.0
        override var percent = -1
        override var isPercent = false
        override var isTempCancel = false
        override var bolusDelivered = 0.0
        override var queued = false
        override var cancelled = false

        override fun success(success: Boolean) = apply { this.success = success }
        override fun cancelled(cancelled: Boolean) = apply { this.cancelled = cancelled }
        override fun enacted(enacted: Boolean) = apply { this.enacted = enacted }
        override fun comment(comment: String) = apply { this.comment = comment }
        override fun comment(ref: TextRef) = this
        override fun duration(duration: Int) = apply { this.duration = duration }
        override fun absolute(absolute: Double) = apply { this.absolute = absolute }
        override fun percent(percent: Int) = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean) = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean) = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double) = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean) = apply { this.queued = queued }
    }

    private companion object {

        const val BOLUS_HISTORY_ID = 7L
        const val CANCEL_HISTORY_ID = 8L
        const val BOLUS_CREATED_AT = 1_000L
        const val REQUESTED_UNITS = 0.5
        const val EARLIER_BOLUS_HISTORY_ID = 3L
        const val EARLIER_BOLUS_UNITS = 2.0
        const val TOLERANCE = 1e-9
        const val NOT_ACCEPTED_TEXT = "the pod did not accept the bolus"
    }
}
