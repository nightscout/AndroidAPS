package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.ClientControlSendResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.AckPhase
import app.aaps.core.nssdk.localmodel.clientcontrol.AckStatus
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.MasterPairing
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressPhase
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * Tests the client-side round-trip coordinator: the channelFlow that turns a published command into a
 * Sending → MasterExecuting → terminal stream by matching the master's signed ACK on `commandCounter`,
 * with timeout, masterReachable early-abort, poll fallback, and single-in-flight.
 */
internal class ClientControlRoundTripTest {

    @Mock private lateinit var publisher: ClientControlPublisher
    @Mock private lateinit var pairingRepository: ClientPairingRepository
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var config: Config
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var bolusProgressData: BolusProgressData
    @Mock private lateinit var aapsLogger: AAPSLogger

    private val now = 1_700_000_000_000L
    private val counter = 5L
    private val clientId = "client-1"
    private val secret = ByteArray(32) { 0x17 }
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var reachable: MutableStateFlow<Boolean>
    private val notifications = MutableStateFlow<List<AapsNotification>>(emptyList())
    private lateinit var sut: ClientControlRoundTrip

    // Scope injected into the SUT — cancelled in tearDown() so the progress-stall watchdog (a delayed
    // coroutine armed on an Active progress frame) can't fire after the test and touch a cleared mock.
    private lateinit var appScope: CoroutineScope

    @AfterEach
    fun tearDown() {
        if (::appScope.isInitialized) appScope.cancel()
    }

    // Any command exercises the round-trip mechanics; SceneStop is a simple parameterized survivor.
    private val cmd = ClientControlActionDispatcher.Command.SceneStop(triggerChain = false)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        reachable = MutableStateFlow(true)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(pairingRepository.currentPairing()).thenReturn(MasterPairing(masterInstallId = "m", clientId = clientId, masterSecretEnc = "enc"))
        whenever(pairingRepository.secretBytesOrNull()).thenReturn(secret)
        whenever(nsClientV3Plugin.masterReachable).thenReturn(reachable)
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(notificationManager.notifications).thenReturn(notifications)
        whenever(bolusProgressData.state).thenReturn(MutableStateFlow<BolusProgressState?>(null))
        appScope = CoroutineScope(Dispatchers.Unconfined)
        sut = ClientControlRoundTrip(publisher, pairingRepository, Provider { nsClientV3Plugin }, nsClientRepository, config, dateUtil, notificationManager, rh, bolusProgressData, aapsLogger, appScope)
    }

    @Test
    fun onAckDoc_deliveryFailed_raisesUrgentAlarm() {
        // A late Delivery/Failed ack (an async bolus failure the master relays after its Done ack) → URGENT alarm.
        // The master-authored payload (title + pump detail) is shown as-is — not re-prefixed with the title.
        sut.onAckDoc(ackDoc(AckPhase.Delivery, AckStatus.Failed, reason = "ExecutionFailed", payload = "Treatment delivery error\npump fault"))

        verify(notificationManager).post(
            eq(NotificationId.BOLUS_DELIVERY_FAILED), any<String>(), any<NotificationLevel>(), any<Int>(),
            anyOrNull<Int>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
    }

    @Test
    fun clientDismissingBolusFailedAlarm_relaysDismissToMaster() = runTest {
        whenever(publisher.publish(ClientControlMessage.DismissAlarm)).thenReturn(ClientControlSendResult.NotPaired)
        // The relayed alarm shows on the client, then the user clears it → one fire-and-forget DismissAlarm to the master.
        notifications.value = listOf(AapsNotification(NotificationId.BOLUS_DELIVERY_FAILED, instanceKey = 0, text = "x", level = NotificationLevel.URGENT))
        notifications.value = emptyList()

        verify(publisher).publish(ClientControlMessage.DismissAlarm)
    }

    @Test
    fun clientDismissingUnrelatedAlarm_doesNotRelay() = runTest {
        // Only the bolus-delivery-failure alarm relays; clearing any other notification must NOT signal the master.
        notifications.value = listOf(AapsNotification(NotificationId.PUMP_ERROR, instanceKey = 0, text = "x", level = NotificationLevel.URGENT))
        notifications.value = emptyList()

        verify(publisher, never()).publish(ClientControlMessage.DismissAlarm)
    }

    @Test
    fun onProgressDoc_activePhase_startsAndUpdatesClientBolusProgress() = runTest {
        // A signed Active frame drives the client's OWN BolusProgressData → the existing dialog lights up.
        sut.onProgressDoc(progressDoc(ProgressPhase.Active, percent = 40, status = "Delivering 0.8U", insulin = 2.0, delivered = 0.8))

        verify(bolusProgressData).start(eq(2.0), eq(false), eq(false))
        verify(bolusProgressData).updateProgress(eq(40), eq("Delivering 0.8U"), any<PumpInsulin>())
    }

    @Test
    fun onProgressDoc_completePhase_completesClientBolusProgress() = runTest {
        sut.onProgressDoc(progressDoc(ProgressPhase.Complete, percent = 100))
        verify(bolusProgressData).completeAndAutoClear()
    }

    @Test
    fun onProgressDoc_clearedPhase_clearsClientBolusProgress() = runTest {
        sut.onProgressDoc(progressDoc(ProgressPhase.Cleared))
        verify(bolusProgressData).clear()
    }

    @Test
    fun onProgressDoc_forgedSignature_isIgnored() = runTest {
        sut.onProgressDoc(progressDoc(ProgressPhase.Active, percent = 50, signSecret = ByteArray(32) { 0x42 }))
        verify(bolusProgressData, never()).start(any<Double>(), any<Boolean>(), any<Boolean>())
    }

    @Test
    fun stopBolus_publishesStopBolusToMaster() = runTest {
        whenever(publisher.publish(ClientControlMessage.StopBolus)).thenReturn(ClientControlSendResult.NotPaired)
        sut.stopBolus()
        verify(publisher).publish(ClientControlMessage.StopBolus)
    }

    /** A signed master→client progress frame as the WS layer would hand it to onProgressDoc. */
    private fun progressDoc(
        phase: ProgressPhase, percent: Int = 0, status: String = "", insulin: Double = 2.0,
        delivered: Double = 0.0, ts: Long = now, signSecret: ByteArray = secret
    ): JSONObject {
        val env = ClientControlCrypto.signProgress(
            signSecret,
            ProgressEnvelope(clientId, phase, insulin, percent, status, delivered, stopDeliveryEnabled = true, timestamp = ts, signature = "")
        )
        return JSONObject().apply { put("progress", JSONObject(json.encodeToString(ProgressEnvelope.serializer(), env))) }
    }

    private suspend fun stubPublish(result: ClientControlSendResult, ctr: Long? = counter) {
        whenever(publisher.publishTracked(any(), any(), any())).thenReturn(ClientControlPublisher.TrackedPublish(result, ctr))
    }

    /** A signed ACK doc as the WS layer would hand it to onAckDoc. [signSecret] lets a test forge one. */
    private fun ackDoc(phase: AckPhase, status: AckStatus, reason: String? = null, payload: String? = null, ctr: Long = counter, signSecret: ByteArray = secret): JSONObject {
        val ack = ClientControlCrypto.signAck(signSecret, AckEnvelope(clientId, ctr, phase, status, reason, payload, timestamp = now, signature = ""))
        return JSONObject().apply { put("ack", JSONObject(json.encodeToString(AckEnvelope.serializer(), ack))) }
    }

    @Test
    fun successEmitsSendingMasterExecutingApplied() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        runCurrent() // Sending + publish + collectors subscribed, no time advanced (no timeout)
        sut.onAckDoc(ackDoc(AckPhase.Executing, AckStatus.Pending))
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok))
        runCurrent()
        job.join()
        assertThat(out).containsExactly(ActionProgress.Sending, ActionProgress.MasterExecuting, ActionProgress.Applied).inOrder()
    }

    @Test
    fun doneFailedEmitsRejectedWithReason() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Failed, reason = FailureReason.NoActiveProfile.name))
        runCurrent()
        job.join()
        assertThat(out.last()).isEqualTo(ActionProgress.Rejected(FailureReason.NoActiveProfile))
    }

    @Test
    fun expiredAckEmitsRejected() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Expired))
        runCurrent()
        job.join()
        assertThat(out.last()).isInstanceOf(ActionProgress.Rejected::class.java)
    }

    @Test
    fun notPairedEmitsRejectedWithoutWaiting() = runTest {
        stubPublish(ClientControlSendResult.NotPaired, ctr = null)
        val out = sut.dispatch(cmd).let { flow -> mutableListOf<ActionProgress>().also { l -> launch { flow.collect { l += it } }.join() } }
        assertThat(out).containsExactly(ActionProgress.Sending, ActionProgress.Rejected(FailureReason.NotPaired)).inOrder()
    }

    @Test
    fun timeoutWithNoAckEmitsUnconfirmed() = runTest {
        stubPublish(ClientControlSendResult.Success)
        whenever(nsAndroidClient.getSettings(any())).thenReturn(NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = null))
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        advanceUntilIdle() // past validUntil + margin → withTimeoutOrNull times out → pollAck (null) → Unconfirmed
        job.join()
        assertThat(out.last()).isInstanceOf(ActionProgress.Unconfirmed::class.java)
    }

    @Test
    fun masterUnreachableMidWaitEmitsUnconfirmed() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        runCurrent() // waiting, reachable still true
        reachable.value = false
        runCurrent()
        job.join()
        assertThat(out.last()).isEqualTo(ActionProgress.Unconfirmed(FailureReason.NotReachable))
    }

    /** Safety: a validly-shaped but WRONG-secret ACK must NOT resolve to Applied. */
    @Test
    fun forgedAckIsIgnoredAndTimesOutUnconfirmed() = runTest {
        stubPublish(ClientControlSendResult.Success)
        whenever(nsAndroidClient.getSettings(any())).thenReturn(NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = null))
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok, signSecret = ByteArray(32) { 0x42 })) // forged
        advanceUntilIdle()
        job.join()
        assertThat(out).doesNotContain(ActionProgress.Applied)
        assertThat(out.last()).isInstanceOf(ActionProgress.Unconfirmed::class.java)
    }

    /** A Done ACK for a different command (different counter) must not resolve this request. */
    @Test
    fun ackForDifferentCounterIsIgnored() = runTest {
        stubPublish(ClientControlSendResult.Success)
        whenever(nsAndroidClient.getSettings(any())).thenReturn(NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = null))
        val out = mutableListOf<ActionProgress>()
        val job = launch { sut.dispatch(cmd).collect { out += it } }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok, ctr = 99L)) // wrong correlation id
        advanceUntilIdle()
        job.join()
        assertThat(out).doesNotContain(ActionProgress.Applied)
        assertThat(out.last()).isInstanceOf(ActionProgress.Unconfirmed::class.java)
    }

    // ---- liveness (PING-PONG) ----

    @Test
    fun sendPingPublishesPingWithWantsAck() = runTest {
        whenever(publisher.publishTracked(any(), any(), any())).thenReturn(ClientControlPublisher.TrackedPublish(ClientControlSendResult.Success, counter))
        sut.sendPing()
        verify(publisher).publishTracked(eq(ClientControlMessage.Ping), any(), eq(true))
    }

    @Test
    fun validAckBumpsMasterSignal() = runTest {
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok))
        verify(nsClientV3Plugin).bumpMasterSignal(now) // dateUtil.now() == now
    }

    /** Safety: a forged ack must not be treated as a liveness signal either. */
    @Test
    fun forgedAckDoesNotBumpMasterSignal() = runTest {
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok, signSecret = ByteArray(32) { 0x42 }))
        verify(nsClientV3Plugin, never()).bumpMasterSignal(any())
    }

    // ---- run() drives the single app-level modal (pendingAction) ----

    @Test
    fun runSendsCommandAndClearsModalOnApplied() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val cmdPref = ClientControlActionDispatcher.Command.PreferenceEdit(mapOf("boluswizard_percentage" to ("80" to 100L)))
        val job = launch { sut.run(cmdPref, "update settings") }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok))
        advanceUntilIdle() // lets the MIN_MODAL_VISIBLE_MS hold elapse before the auto-dismiss
        job.join()
        verify(publisher).publishTracked(argThat { this is ClientControlMessage.PreferencesUpdate }, any(), any())
        assertThat(sut.pendingAction.value).isNull() // Applied → silent dismiss
    }

    @Test
    fun runReturnsAppliedTerminal() = runTest {
        stubPublish(ClientControlSendResult.Success)
        var terminal: ActionProgress? = null
        val job = launch { terminal = sut.run(cmd, "do thing") }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Ok))
        advanceUntilIdle()
        job.join()
        assertThat(terminal).isEqualTo(ActionProgress.Applied)
    }

    @Test
    fun runRejectedKeepsModalUntilDismiss() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val job = launch { sut.run(cmd, "do thing") }
        runCurrent()
        sut.onAckDoc(ackDoc(AckPhase.Done, AckStatus.Failed, reason = FailureReason.ExecutionFailed.name))
        advanceUntilIdle()
        job.join()
        assertThat(sut.pendingAction.value?.progress).isInstanceOf(ActionProgress.Rejected::class.java)
        sut.dismissActionProgress()
        assertThat(sut.pendingAction.value).isNull()
    }

    @Test
    fun dismissWhileWaitingClearsModalAndStopsRun() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val job = launch { sut.run(cmd, "do thing") }
        runCurrent() // waiting (Sending/MasterExecuting), modal up
        assertThat(sut.pendingAction.value?.progress).isInstanceOf(ActionProgress.Sending::class.java)
        sut.dismissActionProgress() // "stop waiting"
        advanceUntilIdle()
        job.join()
        assertThat(sut.pendingAction.value).isNull()
    }

    @Test
    fun secondConcurrentDispatchIsRejected() = runTest {
        stubPublish(ClientControlSendResult.Success)
        val out1 = mutableListOf<ActionProgress>()
        val job1 = launch { sut.dispatch(cmd).collect { out1 += it } }
        runCurrent() // first dispatch holds inFlight, waiting

        val out2 = mutableListOf<ActionProgress>()
        launch { sut.dispatch(cmd).collect { out2 += it } }.join()
        assertThat(out2).containsExactly(ActionProgress.Rejected(FailureReason.Busy))

        job1.cancel() // releases inFlight via finally
    }
}
