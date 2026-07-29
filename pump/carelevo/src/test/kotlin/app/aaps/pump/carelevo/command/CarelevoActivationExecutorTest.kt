package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.pump.carelevo.ble.BleResponse
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.AlarmClearResponse
import app.aaps.pump.carelevo.ble.commands.InfusionThresholdResponse
import app.aaps.pump.carelevo.ble.commands.NoticeThresholdResponse
import app.aaps.pump.carelevo.ble.commands.PumpResumeResponse
import app.aaps.pump.carelevo.ble.commands.SafetyCheckCommand
import app.aaps.pump.carelevo.ble.commands.SafetyCheckResponse
import app.aaps.pump.carelevo.ble.commands.SimpleResultResponse
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.SafetyProgress
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchNeedleInsertionCheckUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchSafetyCheckUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import javax.inject.Provider

/**
 * JVM unit tests for [CarelevoActivationExecutor]. Every op is driven through the public [CarelevoActivationExecutor.execute]
 * entry point (the only public API besides the [CarelevoActivationExecutor.safetyProgress] flow). The
 * coroutine [CarelevoBleSession] and the persistence-only use cases are mocked; suspend calls are stubbed
 * with the module's `whenever { ... }` lambda form (see [app.aaps.pump.carelevo.CarelevoPumpPluginTestBase]).
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoActivationExecutorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var safetyCheckUseCase: CarelevoPatchSafetyCheckUseCase
    @Mock lateinit var needleCheckUseCase: CarelevoPatchNeedleInsertionCheckUseCase
    @Mock lateinit var setBasalUseCase: CarelevoSetBasalProgramUseCase
    @Mock lateinit var pumpStopUseCase: CarelevoPumpStopUseCase
    @Mock lateinit var pumpResumeUseCase: CarelevoPumpResumeUseCase
    @Mock lateinit var updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase
    @Mock lateinit var updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase
    @Mock lateinit var alarmClearRequestUseCase: AlarmClearRequestUseCase
    @Mock lateinit var alarmClearPatchDiscardUseCase: AlarmClearPatchDiscardUseCase
    @Mock lateinit var bleSession: CarelevoBleSession

    private val pumpEnactResultProvider = Provider<PumpEnactResult> { FakePumpEnactResult() }

    private lateinit var sut: CarelevoActivationExecutor

    @BeforeEach
    fun setUp() {
        sut = CarelevoActivationExecutor(
            aapsLogger = aapsLogger,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            safetyCheckUseCase = safetyCheckUseCase,
            needleCheckUseCase = needleCheckUseCase,
            setBasalUseCase = setBasalUseCase,
            pumpStopUseCase = pumpStopUseCase,
            pumpResumeUseCase = pumpResumeUseCase,
            updateMaxBolusDoseUseCase = updateMaxBolusDoseUseCase,
            updateLowInsulinNoticeAmountUseCase = updateLowInsulinNoticeAmountUseCase,
            alarmClearRequestUseCase = alarmClearRequestUseCase,
            alarmClearPatchDiscardUseCase = alarmClearPatchDiscardUseCase,
            bleSession = bleSession
        )
        // Happy default: a patch address is stored. Individual "no address" tests re-stub to null.
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(ADDRESS)
    }

    // region helpers ---------------------------------------------------------------------------------

    private fun stubProfilePresent() {
        val profile = mock<Profile>()
        whenever(carelevoPatch.profile).thenReturn(BehaviorSubject.createDefault(Optional.of(profile)))
    }

    private fun stubBasalPlan() {
        whenever(setBasalUseCase.buildBasalProgramPlan(any())).thenReturn(
            CarelevoSetBasalProgramUseCase.BasalProgramPlan(programs = List(3) { List(8) { 1.0 } }, segments = emptyList())
        )
    }

    /** Make the (suspend) generic [CarelevoBleSession.runSingle] return [response]. */
    private fun stubRunSingle(response: BleResponse) {
        whenever { bleSession.runSingle<BleResponse>(any(), any(), any()) }.thenReturn(response)
    }

    private fun stubAdditionalPriming(response: SimpleResultResponse) {
        whenever { bleSession.runAdditionalPriming(any()) }.thenReturn(response)
    }

    /** Make the (suspend) generic [CarelevoBleSession.runSingle] throw. */
    private fun stubRunSingleThrows(message: String = BOOM) {
        whenever { bleSession.runSingle<BleResponse>(any(), any(), any()) }.thenAnswer { throw RuntimeException(message) }
    }

    private fun stubAdditionalPrimingThrows(message: String = BOOM) {
        whenever { bleSession.runAdditionalPriming(any()) }.thenAnswer { throw RuntimeException(message) }
    }

    /** Drive the streaming safety check: feed [frames] to the executor's onFrame callback. */
    private fun stubSafetyFrames(vararg frames: SafetyCheckResponse) {
        whenever { bleSession.runSafetyCheck(any(), any()) }.thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val onFrame = inv.getArgument<Any>(1) as (SafetyCheckResponse) -> Unit
            frames.forEach { onFrame(it) }
            Unit
        }
    }

    private fun stubSafetyThrows(message: String = BOOM) {
        whenever { bleSession.runSafetyCheck(any(), any()) }.thenAnswer { throw RuntimeException(message) }
    }

    /** Live-collect [CarelevoActivationExecutor.safetyProgress]; Unconfined resumes the collector inline on emit. */
    private fun collectSafetyProgress(): Pair<MutableList<SafetyProgress>, Job> {
        val emissions = mutableListOf<SafetyProgress>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { sut.safetyProgress.collect { emissions.add(it) } }
        return emissions to job
    }

    private fun progressFrame(code: Int = SafetyCheckCommand.REP_REQUEST) = SafetyCheckResponse(resultCode = code, insulinVolume = 10, durationSeconds = 100)
    private fun successFrame() = SafetyCheckResponse(resultCode = SafetyCheckCommand.RESULT_SUCCESS, insulinVolume = 20, durationSeconds = 120)
    private fun errorFrame(code: Int = 2) = SafetyCheckResponse(resultCode = code, insulinVolume = 0, durationSeconds = 0)

    // endregion --------------------------------------------------------------------------------------

    // region execute dispatch

    @Test fun `execute returns null for an unknown custom command`() {
        assertThat(sut.execute(mock<CustomCommand>())).isNull()
    }

    // endregion

    // region setBasal

    @Test fun `setBasal returns profile-not-set when no profile is present`() {
        whenever(carelevoPatch.profile).thenReturn(BehaviorSubject.createDefault(Optional.empty<Profile>()))
        val result = sut.execute(CmdSetBasal())
        assertThat(result?.success).isFalse()
        assertThat(result?.enacted).isFalse()
        assertThat(result?.comment).isEqualTo("profile not set")
    }

    @Test fun `setBasal returns no-patch-address when profile present but address missing`() {
        stubProfilePresent()
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdSetBasal())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `setBasal success when programmed and persisted`() {
        stubProfilePresent()
        stubBasalPlan()
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(true)
        whenever(setBasalUseCase.persistBasalProgram(any())).thenReturn(true)
        val result = sut.execute(CmdSetBasal())
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `setBasal fails when programmed but persist fails`() {
        stubProfilePresent()
        stubBasalPlan()
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(true)
        whenever(setBasalUseCase.persistBasalProgram(any())).thenReturn(false)
        val result = sut.execute(CmdSetBasal())
        assertThat(result?.success).isFalse()
    }

    @Test fun `setBasal fails when the program is rejected and does not persist`() {
        stubProfilePresent()
        stubBasalPlan()
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(false)
        val result = sut.execute(CmdSetBasal())
        assertThat(result?.success).isFalse()
        verify(setBasalUseCase, never()).persistBasalProgram(any())
    }

    @Test fun `setBasal returns failed result on exception`() {
        stubProfilePresent()
        stubBasalPlan()
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenAnswer { throw RuntimeException(BOOM) }
        val result = sut.execute(CmdSetBasal())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region pumpStop

    @Test fun `pumpStop returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdPumpStop(durationMin = 60))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `pumpStop rejected when pump returns non-zero result`() {
        stubRunSingle(SimpleResultResponse(resultCode = 3))
        val result = sut.execute(CmdPumpStop(durationMin = 60))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("stop result 3")
        verify(pumpStopUseCase, never()).persistStopped(any())
    }

    @Test fun `pumpStop success when accepted and persisted`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        whenever(pumpStopUseCase.persistStopped(any())).thenReturn(true)
        val result = sut.execute(CmdPumpStop(durationMin = 60))
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `pumpStop fails when accepted but persist fails`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        whenever(pumpStopUseCase.persistStopped(any())).thenReturn(false)
        val result = sut.execute(CmdPumpStop(durationMin = 60))
        assertThat(result?.success).isFalse()
    }

    @Test fun `pumpStop returns failed result on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdPumpStop(durationMin = 60))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region pumpResume

    @Test fun `pumpResume returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdPumpResume())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `pumpResume rejected when pump returns non-zero result`() {
        stubRunSingle(PumpResumeResponse(resultCode = 5, mode = 1, subId = 0))
        val result = sut.execute(CmdPumpResume())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("resume result 5")
        verify(pumpResumeUseCase, never()).persistResumed()
    }

    @Test fun `pumpResume success when accepted and persisted`() {
        stubRunSingle(PumpResumeResponse(resultCode = 0, mode = 1, subId = 0))
        whenever(pumpResumeUseCase.persistResumed()).thenReturn(true)
        val result = sut.execute(CmdPumpResume())
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `pumpResume fails when accepted but persist fails`() {
        stubRunSingle(PumpResumeResponse(resultCode = 0, mode = 1, subId = 0))
        whenever(pumpResumeUseCase.persistResumed()).thenReturn(false)
        val result = sut.execute(CmdPumpResume())
        assertThat(result?.success).isFalse()
    }

    @Test fun `pumpResume returns failed result on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdPumpResume())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region buzzer

    @Test fun `buzzer returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdUpdateBuzzer(on = true))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `buzzer success when pump accepts`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        val result = sut.execute(CmdUpdateBuzzer(on = true))
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `buzzer fails when pump rejects`() {
        stubRunSingle(SimpleResultResponse(resultCode = 1))
        val result = sut.execute(CmdUpdateBuzzer(on = false))
        assertThat(result?.success).isFalse()
    }

    @Test fun `buzzer returns failed result on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdUpdateBuzzer(on = true))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region updateMaxBolus

    @Test fun `maxBolus defers with local persist when a bolus is running`() {
        whenever(updateMaxBolusDoseUseCase.isBolusRunning()).thenReturn(true)
        whenever(updateMaxBolusDoseUseCase.persistMaxBolusDose(any(), any())).thenReturn(true)
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isTrue()
        verify(updateMaxBolusDoseUseCase).persistMaxBolusDose(10.0, synced = false)
    }

    @Test fun `maxBolus deferred persist failure reports failure`() {
        whenever(updateMaxBolusDoseUseCase.isBolusRunning()).thenReturn(true)
        whenever(updateMaxBolusDoseUseCase.persistMaxBolusDose(any(), any())).thenReturn(false)
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isFalse()
    }

    @Test fun `maxBolus returns no-patch-address when not running and address missing`() {
        whenever(updateMaxBolusDoseUseCase.isBolusRunning()).thenReturn(false)
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `maxBolus success when pushed and persisted`() {
        stubRunSingle(InfusionThresholdResponse(type = 1, resultCode = 0))
        whenever(updateMaxBolusDoseUseCase.persistMaxBolusDose(any(), any())).thenReturn(true)
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isTrue()
        verify(updateMaxBolusDoseUseCase).persistMaxBolusDose(10.0, synced = true)
    }

    @Test fun `maxBolus fails but still persists deferred when pump rejects`() {
        stubRunSingle(InfusionThresholdResponse(type = 1, resultCode = 4))
        whenever(updateMaxBolusDoseUseCase.persistMaxBolusDose(any(), any())).thenReturn(true)
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isFalse()
        verify(updateMaxBolusDoseUseCase).persistMaxBolusDose(10.0, synced = false)
    }

    @Test fun `maxBolus fails when pushed but persist fails`() {
        stubRunSingle(InfusionThresholdResponse(type = 1, resultCode = 0))
        whenever(updateMaxBolusDoseUseCase.persistMaxBolusDose(any(), any())).thenReturn(false)
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isFalse()
    }

    @Test fun `maxBolus keeps value deferred and reports failure on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdUpdateMaxBolus(maxBolusDose = 10.0))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
        verify(updateMaxBolusDoseUseCase).persistMaxBolusDose(10.0, synced = false)
    }

    // endregion

    // region updateLowInsulinNotice

    @Test fun `lowInsulinNotice returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdUpdateLowInsulinNotice(hours = 30))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `lowInsulinNotice success when arrival persisted`() {
        stubRunSingle(NoticeThresholdResponse(thresholdType = 0, resultCode = 0))
        whenever(updateLowInsulinNoticeAmountUseCase.persistLowInsulinNoticeAmount(any(), any())).thenReturn(true)
        val result = sut.execute(CmdUpdateLowInsulinNotice(hours = 30))
        assertThat(result?.success).isTrue()
        verify(updateLowInsulinNoticeAmountUseCase).persistLowInsulinNoticeAmount(30, synced = true)
    }

    @Test fun `lowInsulinNotice fails when persist fails`() {
        stubRunSingle(NoticeThresholdResponse(thresholdType = 0, resultCode = 0))
        whenever(updateLowInsulinNoticeAmountUseCase.persistLowInsulinNoticeAmount(any(), any())).thenReturn(false)
        val result = sut.execute(CmdUpdateLowInsulinNotice(hours = 30))
        assertThat(result?.success).isFalse()
    }

    @Test fun `lowInsulinNotice keeps value deferred and reports failure on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdUpdateLowInsulinNotice(hours = 30))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
        verify(updateLowInsulinNoticeAmountUseCase).persistLowInsulinNoticeAmount(30, synced = false)
    }

    // endregion

    // region updateExpiredThreshold

    @Test fun `expiryThreshold returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdUpdateExpiredThreshold(hours = 48))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `expiryThreshold success when the frame arrives`() {
        stubRunSingle(NoticeThresholdResponse(thresholdType = 1, resultCode = 0))
        val result = sut.execute(CmdUpdateExpiredThreshold(hours = 48))
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `expiryThreshold returns failed result on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdUpdateExpiredThreshold(hours = 48))
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region discard

    @Test fun `discard returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdDiscard())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `discard tears down and succeeds when the stop write is accepted`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        val result = sut.execute(CmdDiscard())
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
        verify(carelevoPatch).discardTeardown()
    }

    @Test fun `discard does not tear down and fails when the stop write is rejected`() {
        stubRunSingle(SimpleResultResponse(resultCode = 1))
        val result = sut.execute(CmdDiscard())
        assertThat(result?.success).isFalse()
        verify(carelevoPatch, never()).discardTeardown()
    }

    @Test fun `discard swallows the write exception and fails without teardown`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdDiscard())
        assertThat(result?.success).isFalse()
        verify(carelevoPatch, never()).discardTeardown()
    }

    // endregion

    // region needleCheck

    @Test fun `needleCheck returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdNeedleCheck())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `needleCheck success when inserted and persisted`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        whenever(needleCheckUseCase.persistNeedleResult(any())).thenReturn(true)
        val result = sut.execute(CmdNeedleCheck())
        assertThat(result?.success).isTrue()
        verify(needleCheckUseCase).persistNeedleResult(true)
    }

    @Test fun `needleCheck fails when inserted but persist fails`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        whenever(needleCheckUseCase.persistNeedleResult(any())).thenReturn(false)
        val result = sut.execute(CmdNeedleCheck())
        assertThat(result?.success).isFalse()
    }

    @Test fun `needleCheck fails and persists not-inserted when pump reports non-zero`() {
        stubRunSingle(SimpleResultResponse(resultCode = 2))
        whenever(needleCheckUseCase.persistNeedleResult(any())).thenReturn(true)
        val result = sut.execute(CmdNeedleCheck())
        assertThat(result?.success).isFalse()
        verify(needleCheckUseCase).persistNeedleResult(false)
    }

    @Test fun `needleCheck returns failed result on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(CmdNeedleCheck())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region runSingleWrite (additionalPriming + timeZoneUpdate)

    @Test fun `additionalPriming returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdAdditionalPriming())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `additionalPriming success when accepted`() {
        stubAdditionalPriming(SimpleResultResponse(resultCode = 0))
        val result = sut.execute(CmdAdditionalPriming())
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `additionalPriming uses dedicated ble session path`() {
        stubAdditionalPriming(SimpleResultResponse(resultCode = 0))

        sut.execute(CmdAdditionalPriming())

        verifyBlocking(bleSession) { runAdditionalPriming(any()) }
    }

    @Test fun `additionalPriming fails when rejected`() {
        stubAdditionalPriming(SimpleResultResponse(resultCode = 7))
        val result = sut.execute(CmdAdditionalPriming())
        assertThat(result?.success).isFalse()
    }

    @Test fun `additionalPriming returns failed result on exception`() {
        stubAdditionalPrimingThrows()
        val result = sut.execute(CmdAdditionalPriming())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    @Test fun `timeZoneUpdate success when accepted`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        val result = sut.execute(CmdTimeZoneUpdate(insulinAmount = 0))
        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
    }

    @Test fun `timeZoneUpdate fails when rejected`() {
        stubRunSingle(SimpleResultResponse(resultCode = 1))
        val result = sut.execute(CmdTimeZoneUpdate(insulinAmount = 0))
        assertThat(result?.success).isFalse()
    }

    // endregion

    // region safetyCheck

    @Test fun `safetyCheck returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(CmdSafetyCheck())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `safetyCheck emits progress then success and returns success`() {
        stubSafetyFrames(progressFrame(), successFrame())
        whenever(safetyCheckUseCase.persistSafetyChecked()).thenReturn(true)
        val (emissions, job) = collectSafetyProgress()

        val result = sut.execute(CmdSafetyCheck())
        job.cancel()

        assertThat(result?.success).isTrue()
        assertThat(result?.enacted).isTrue()
        assertThat(emissions).hasSize(2)
        assertThat(emissions[0]).isInstanceOf(SafetyProgress.Progress::class.java)
        assertThat(emissions[1]).isInstanceOf(SafetyProgress.Success::class.java)
    }

    @Test fun `safetyCheck accepts the REP_REQUEST1 progress code`() {
        stubSafetyFrames(progressFrame(SafetyCheckCommand.REP_REQUEST1), successFrame())
        whenever(safetyCheckUseCase.persistSafetyChecked()).thenReturn(true)
        val result = sut.execute(CmdSafetyCheck())
        assertThat(result?.success).isTrue()
    }

    @Test fun `safetyCheck emits only one progress even with repeated progress frames`() {
        stubSafetyFrames(progressFrame(), progressFrame(SafetyCheckCommand.REP_REQUEST1), successFrame())
        whenever(safetyCheckUseCase.persistSafetyChecked()).thenReturn(true)
        val (emissions, job) = collectSafetyProgress()

        val result = sut.execute(CmdSafetyCheck())
        job.cancel()

        assertThat(result?.success).isTrue()
        assertThat(emissions.count { it is SafetyProgress.Progress }).isEqualTo(1)
        assertThat(emissions.last()).isInstanceOf(SafetyProgress.Success::class.java)
    }

    @Test fun `safetyCheck fails and emits error when the success frame cannot be persisted`() {
        stubSafetyFrames(successFrame())
        whenever(safetyCheckUseCase.persistSafetyChecked()).thenReturn(false)
        val (emissions, job) = collectSafetyProgress()

        val result = sut.execute(CmdSafetyCheck())
        job.cancel()

        assertThat(result?.success).isFalse()
        assertThat(emissions.single()).isInstanceOf(SafetyProgress.Error::class.java)
    }

    @Test fun `safetyCheck fails and emits error on a terminal error frame`() {
        stubSafetyFrames(errorFrame(code = 2))
        val (emissions, job) = collectSafetyProgress()

        val result = sut.execute(CmdSafetyCheck())
        job.cancel()

        assertThat(result?.success).isFalse()
        assertThat(emissions.single()).isInstanceOf(SafetyProgress.Error::class.java)
        verify(safetyCheckUseCase, never()).persistSafetyChecked()
    }

    @Test fun `safetyCheck returns failed result and emits error on exception`() {
        stubSafetyThrows()
        val (emissions, job) = collectSafetyProgress()

        val result = sut.execute(CmdSafetyCheck())
        job.cancel()

        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
        assertThat(emissions.single()).isInstanceOf(SafetyProgress.Error::class.java)
    }

    // endregion

    // region alarmClear

    @Test fun `alarmClear returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(alarmClearCmd())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `alarmClear success when cleared and persisted`() {
        whenever(alarmClearRequestUseCase.commandAlarmType(any())).thenReturn(1)
        stubRunSingle(AlarmClearResponse(subId = 0, cause = 1, resultCode = 0))
        whenever(alarmClearRequestUseCase.persistAlarmCleared(any())).thenReturn(true)
        val result = sut.execute(alarmClearCmd())
        assertThat(result?.success).isTrue()
        verify(alarmClearRequestUseCase).persistAlarmCleared(ALARM_ID)
    }

    @Test fun `alarmClear fails when cleared but persist fails`() {
        whenever(alarmClearRequestUseCase.commandAlarmType(any())).thenReturn(1)
        stubRunSingle(AlarmClearResponse(subId = 0, cause = 1, resultCode = 0))
        whenever(alarmClearRequestUseCase.persistAlarmCleared(any())).thenReturn(false)
        val result = sut.execute(alarmClearCmd())
        assertThat(result?.success).isFalse()
    }

    @Test fun `alarmClear fails and skips persist when the pump rejects`() {
        whenever(alarmClearRequestUseCase.commandAlarmType(any())).thenReturn(1)
        stubRunSingle(AlarmClearResponse(subId = 0, cause = 1, resultCode = 9))
        val result = sut.execute(alarmClearCmd())
        assertThat(result?.success).isFalse()
        verify(alarmClearRequestUseCase, never()).persistAlarmCleared(any())
    }

    @Test fun `alarmClear maps a null cause code to zero`() {
        whenever(alarmClearRequestUseCase.commandAlarmType(any())).thenReturn(0)
        stubRunSingle(AlarmClearResponse(subId = 0, cause = 0, resultCode = 0))
        whenever(alarmClearRequestUseCase.persistAlarmCleared(any())).thenReturn(true)
        // ALARM_UNKNOWN carries a null code → executor falls back to cause = 0 (valid for AlarmClearCommand).
        val result = sut.execute(CmdAlarmClear(ALARM_ID, AlarmCause.ALARM_UNKNOWN.alarmType, AlarmCause.ALARM_UNKNOWN))
        assertThat(result?.success).isTrue()
    }

    @Test fun `alarmClear returns failed result on exception`() {
        whenever(alarmClearRequestUseCase.commandAlarmType(any())).thenReturn(1)
        stubRunSingleThrows()
        val result = sut.execute(alarmClearCmd())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    // region alarmClearPatchDiscard

    @Test fun `alarmClearPatchDiscard returns no-patch-address when address missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        val result = sut.execute(alarmDiscardCmd())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo("no patch address")
    }

    @Test fun `alarmClearPatchDiscard success when discarded and persisted`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        whenever(alarmClearPatchDiscardUseCase.persistAlarmDiscarded(any())).thenReturn(true)
        val result = sut.execute(alarmDiscardCmd())
        assertThat(result?.success).isTrue()
        verify(alarmClearPatchDiscardUseCase).persistAlarmDiscarded(ALARM_ID)
    }

    @Test fun `alarmClearPatchDiscard fails when discarded but persist fails`() {
        stubRunSingle(SimpleResultResponse(resultCode = 0))
        whenever(alarmClearPatchDiscardUseCase.persistAlarmDiscarded(any())).thenReturn(false)
        val result = sut.execute(alarmDiscardCmd())
        assertThat(result?.success).isFalse()
    }

    @Test fun `alarmClearPatchDiscard fails and skips persist when the pump rejects`() {
        stubRunSingle(SimpleResultResponse(resultCode = 6))
        val result = sut.execute(alarmDiscardCmd())
        assertThat(result?.success).isFalse()
        verify(alarmClearPatchDiscardUseCase, never()).persistAlarmDiscarded(any())
    }

    @Test fun `alarmClearPatchDiscard returns failed result on exception`() {
        stubRunSingleThrows()
        val result = sut.execute(alarmDiscardCmd())
        assertThat(result?.success).isFalse()
        assertThat(result?.comment).isEqualTo(BOOM)
    }

    // endregion

    private fun alarmClearCmd() = CmdAlarmClear(ALARM_ID, AlarmCause.ALARM_ALERT_OUT_OF_INSULIN.alarmType, AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)
    private fun alarmDiscardCmd() = CmdAlarmClearPatchDiscard(ALARM_ID, AlarmCause.ALARM_WARNING_PUMP_CLOGGED.alarmType, AlarmCause.ALARM_WARNING_PUMP_CLOGGED)

    private companion object {

        private const val ADDRESS = "aa:bb:cc:dd:ee:ff"
        private const val ALARM_ID = "alarm-1"
        private const val BOOM = "boom"
    }

    /** Minimal [PumpEnactResult] so the provider returns a fresh, real result object per op. */
    private class FakePumpEnactResult : PumpEnactResult {

        override var success: Boolean = false
        override var enacted: Boolean = false
        override var comment: String = ""
        override var duration: Int = -1
        override var absolute: Double = -1.0
        override var percent: Int = -1
        override var isPercent: Boolean = false
        override var isTempCancel: Boolean = false
        override var bolusDelivered: Double = 0.0
        override var queued: Boolean = false

        override fun success(success: Boolean): PumpEnactResult = apply { this.success = success }
        override fun enacted(enacted: Boolean): PumpEnactResult = apply { this.enacted = enacted }
        override fun comment(comment: String): PumpEnactResult = apply { this.comment = comment }
        override fun comment(comment: Int): PumpEnactResult = apply { this.comment = comment.toString() }
        override fun duration(duration: Int): PumpEnactResult = apply { this.duration = duration }
        override fun absolute(absolute: Double): PumpEnactResult = apply { this.absolute = absolute }
        override fun percent(percent: Int): PumpEnactResult = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean): PumpEnactResult = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean): PumpEnactResult = apply { this.queued = queued }
    }
}
