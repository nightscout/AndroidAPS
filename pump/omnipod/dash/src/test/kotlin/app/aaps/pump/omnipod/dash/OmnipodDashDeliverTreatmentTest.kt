package app.aaps.pump.omnipod.dash

import android.os.SystemClock
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
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.keys.DashStringNonPreferenceKey
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

/**
 * What `deliverTreatment` answers for a bolus, with the real pod state manager and a pod that is played by
 * the mocked [OmnipodDashManager].
 *
 * The answer decides what AAPS does next: on success the command queue stores the carbs of the meal, on a
 * failure it rings the bolus error alarm. So a bolus the pod never got must not be answered as a success.
 */
class OmnipodDashDeliverTreatmentTest {

    private lateinit var omnipodManager: OmnipodDashManager
    private lateinit var podStateManager: OmnipodDashPodStateManagerImpl
    private lateinit var sut: OmnipodDashPumpPlugin

    /**
     * The bolus command is sent, the link drops and the pod never gets the command. The status read after
     * the wait shows that the pod denied it. The pod still remembers the bolus before this one, and that
     * amount must not be reported as the amount of this bolus.
     */
    @Test
    fun `a bolus the pod never got is reported as failed with nothing delivered`() = runBlocking<Unit> {
        givenPod(nextCommandSequence = POD_LAST_COMMAND_SEQUENCE - 2, withEarlierBolus = true)
        givenBolusCommandIsSentButNotAnswered()

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        // The cause: only a confirmed bolus replaces lastBolus, so it is still the earlier one.
        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(EARLIER_BOLUS_HISTORY_ID)
        assertThat(podStateManager.activeCommand).isNull()
        assertThat(result.bolusDelivered).isEqualTo(0.0)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `a bolus the pod never got fails also when the pod has no earlier bolus`() = runBlocking<Unit> {
        givenPod(nextCommandSequence = POD_LAST_COMMAND_SEQUENCE - 2, withEarlierBolus = false)
        givenBolusCommandIsSentButNotAnswered()

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(result.bolusDelivered).isEqualTo(0.0)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    /** The normal case must stay as it is: the pod confirms the command and later reports it as delivered. */
    @Test
    fun `a bolus the pod confirmed is reported as delivered`() = runBlocking<Unit> {
        givenPod(nextCommandSequence = POD_LAST_COMMAND_SEQUENCE, withEarlierBolus = true)
        givenBolusCommandIsSentAndAnswered()

        val result = sut.deliverTreatment(DetailedBolusInfo().apply { insulin = REQUESTED_UNITS })

        assertThat(podStateManager.lastBolus?.historyId).isEqualTo(BOLUS_HISTORY_ID)
        assertThat(result.bolusDelivered).isEqualTo(REQUESTED_UNITS)
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    /**
     * Builds the plugin around a pod state that is loaded the way the app loads it: from the stored JSON.
     *
     * In a unit test `SystemClock.elapsedRealtime()` always gives 0. The stored state says the last status
     * came at -1, so a command made now (at 0) is newer than it, as it is on a phone.
     *
     * @param nextCommandSequence the sequence number the bolus command gets. The pod reports
     * [POD_LAST_COMMAND_SEQUENCE] as its last command, so only a command with that number counts as received.
     */
    private fun givenPod(nextCommandSequence: Int, withEarlierBolus: Boolean) {
        val earlierBolus =
            if (withEarlierBolus) """, "lastBolus": {"startTime": 1, "requestedUnits": $EARLIER_BOLUS_UNITS, "bolusUnitsRemaining": 0.0, """ +
                """"deliveryComplete": true, "historyId": $EARLIER_BOLUS_HISTORY_ID, "bolusType": "DEFAULT"}"""
            else ""
        val storedPodState = """{"activationProgress": "COMPLETED", "lastStatusResponseReceived": -1, """ +
            """"messageSequenceNumber": $nextCommandSequence$earlierBolus}"""
        val preferences: Preferences = mock()
        whenever(preferences.getIfExists(DashStringNonPreferenceKey.PodState)).thenReturn(storedPodState)
        whenever(preferences.get(DashStringNonPreferenceKey.PodState)).thenReturn(storedPodState)

        val rh: ResourceHelper = mock()
        whenever(rh.gs(any<Int>())).thenReturn("text")

        val history: DashHistory = mock()
        whenever(history.createRecord(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Single.just(BOLUS_HISTORY_ID))
        whenever(history.updateFromState(any())).thenReturn(Completable.complete())
        whenever(history.getById(BOLUS_HISTORY_ID)).thenReturn(
            HistoryRecord(
                id = BOLUS_HISTORY_ID, createdAt = 1_000L, date = 1_000L, commandType = OmnipodCommandType.SET_BOLUS, initialResult = InitialResult.SENT,
                record = BolusRecord(REQUESTED_UNITS, BolusType.DEFAULT), totalAmountDelivered = null, resolvedResult = null, resolvedAt = null
            )
        )

        val pumpSync: PumpSync = mock {
            onBlocking { expectedPumpState() } doReturn PumpSync.PumpState(null, null, null, null, "")
            onBlocking { syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) } doReturn true
        }

        omnipodManager = mock()
        // The status read: the pod is running, no bolus is active and its last command is POD_LAST_COMMAND_SEQUENCE.
        whenever(omnipodManager.getStatus(any())).thenReturn(
            Observable.defer {
                podStateManager.updateFromDefaultStatusResponse(podStatus())
                Observable.empty<PodEvent>()
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

    /** The command leaves the phone, then the link drops: no answer comes, and the pod never got it. */
    private fun givenBolusCommandIsSentButNotAnswered() {
        whenever(omnipodManager.bolus(any(), any(), any())).thenReturn(
            Observable.defer {
                markBolusCommandSent()
                Observable.concat(
                    Observable.just<PodEvent>(PodEvent.CommandSent(mock<Command>())),
                    Observable.error(IOException("link lost"))
                )
            }
        )
    }

    /** The pod answers the command with a status that names this command as its last one. */
    private fun givenBolusCommandIsSentAndAnswered() {
        whenever(omnipodManager.bolus(any(), any(), any())).thenReturn(
            Observable.defer {
                markBolusCommandSent()
                podStateManager.updateFromDefaultStatusResponse(podStatus())
                Observable.just<PodEvent>(PodEvent.CommandSent(mock<Command>()))
            }
        )
    }

    /** What the real manager does on its `CommandSent` event. */
    private fun markBolusCommandSent() {
        podStateManager.activeCommand!!.sentRealtime = SystemClock.elapsedRealtime()
        podStateManager.increaseMessageSequenceNumber()
    }

    /** A real status: basal active, no bolus pulses left, last programming command 5. */
    private fun podStatus() = DefaultStatusResponse(Hex.decodeHex("1D1800A02800000463FF"))

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

        const val POD_LAST_COMMAND_SEQUENCE = 5
        const val BOLUS_HISTORY_ID = 7L
        const val REQUESTED_UNITS = 0.5
        const val EARLIER_BOLUS_HISTORY_ID = 3L
        const val EARLIER_BOLUS_UNITS = 2.0
    }
}
