package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoBasalProfileUpdateCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var bleSession: CarelevoBleSession
    @Mock lateinit var setBasalProgramUseCase: CarelevoSetBasalProgramUseCase

    private lateinit var sut: CarelevoBasalProfileUpdateCoordinator
    private lateinit var profile: Profile

    private val pumpEnactResultProvider = Provider<PumpEnactResult> { FakePumpEnactResult() }

    private val programs = List(3) { List(8) { 1.0 } }

    private fun plan() = CarelevoSetBasalProgramUseCase.BasalProgramPlan(programs = programs, segments = emptyList())

    private fun enact(success: Boolean): PumpEnactResult = FakePumpEnactResult().success(success).enacted(success)

    private fun basalInfusion() = CarelevoBasalInfusionInfoDomainModel(
        infusionId = "basal-1", address = ADDRESS, mode = 1, segments = emptyList(), isStop = false
    )

    private fun extendBolusInfusion() = CarelevoExtendBolusInfusionInfoDomainModel(
        infusionId = "ext-1", address = ADDRESS, mode = 5
    )

    private fun tempBasalInfusion() = CarelevoTempBasalInfusionInfoDomainModel(
        infusionId = "tbr-1", address = ADDRESS, mode = 2
    )

    private fun stubInfusionInfo(model: CarelevoInfusionInfoDomainModel?) {
        val subject =
            if (model != null) BehaviorSubject.createDefault(Optional.of(model))
            else BehaviorSubject.create<Optional<CarelevoInfusionInfoDomainModel>>()
        whenever(carelevoPatch.infusionInfo).thenReturn(subject)
    }

    @BeforeEach
    fun setUp() {
        whenever(rh.gs(any<Int>())).thenReturn("Mocked")
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(ADDRESS)
        whenever(setBasalProgramUseCase.buildBasalProgramPlan(any())).thenReturn(plan())
        whenever(setBasalProgramUseCase.persistBasalProgram(any())).thenReturn(true)
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(true)
        // Default: no running infusion → basalInfusionInfo == null → SET (0x13) mode, no cancels.
        stubInfusionInfo(CarelevoInfusionInfoDomainModel())

        profile = mock()

        sut = CarelevoBasalProfileUpdateCoordinator(
            aapsLogger = aapsLogger,
            rh = rh,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            setBasalProgramUseCase = setBasalProgramUseCase
        )
    }

    // --- success paths -----------------------------------------------------------------------------

    @Test
    fun `set mode success uses the set opcode and marks enacted`() {
        var updated: Profile? = null

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updated = it })

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(updated).isSameInstanceAs(profile)
        verifyBlocking(bleSession) { runBasalProgram(eq(ADDRESS), eq(programs), eq(false)) }
        verify(setBasalProgramUseCase).persistBasalProgram(any())
    }

    @Test
    fun `update mode uses the change opcode when a basal program already exists`() {
        stubInfusionInfo(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion()))

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { })

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(bleSession) { runBasalProgram(eq(ADDRESS), any(), eq(true)) }
    }

    @Test
    fun `plan is built from the profile and forwarded to the program write`() {
        sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { })

        verify(setBasalProgramUseCase).buildBasalProgramPlan(profile)
        verifyBlocking(bleSession) { runBasalProgram(any(), eq(programs), any()) }
    }

    @Test
    fun `null infusion info is treated as set mode with no cancels`() {
        stubInfusionInfo(null)
        val extendCalls = AtomicInteger(0)
        val tempCalls = AtomicInteger(0)

        val result = sut.updateBasalProfile(
            profile,
            { extendCalls.incrementAndGet(); enact(true) },
            { tempCalls.incrementAndGet(); enact(true) },
            { }
        )

        assertThat(result.success).isTrue()
        assertThat(extendCalls.get()).isEqualTo(0)
        assertThat(tempCalls.get()).isEqualTo(0)
        verifyBlocking(bleSession) { runBasalProgram(any(), any(), eq(false)) }
    }

    @Test
    fun `no running infusion skips both cancel callbacks`() {
        val extendCalls = AtomicInteger(0)
        val tempCalls = AtomicInteger(0)

        val result = sut.updateBasalProfile(
            profile,
            { extendCalls.incrementAndGet(); enact(true) },
            { tempCalls.incrementAndGet(); enact(true) },
            { }
        )

        assertThat(result.success).isTrue()
        assertThat(extendCalls.get()).isEqualTo(0)
        assertThat(tempCalls.get()).isEqualTo(0)
    }

    @Test
    fun `running extended bolus is cancelled before the program write`() {
        stubInfusionInfo(CarelevoInfusionInfoDomainModel(extendBolusInfusionInfo = extendBolusInfusion()))
        val extendCalls = AtomicInteger(0)

        val result = sut.updateBasalProfile(profile, { extendCalls.incrementAndGet(); enact(true) }, { enact(true) }, { })

        assertThat(result.success).isTrue()
        assertThat(extendCalls.get()).isEqualTo(1)
        verifyBlocking(bleSession) { runBasalProgram(any(), any(), any()) }
    }

    @Test
    fun `running temp basal is cancelled before the program write`() {
        stubInfusionInfo(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasalInfusion()))
        val tempCalls = AtomicInteger(0)

        val result = sut.updateBasalProfile(profile, { enact(true) }, { tempCalls.incrementAndGet(); enact(true) }, { })

        assertThat(result.success).isTrue()
        assertThat(tempCalls.get()).isEqualTo(1)
        verifyBlocking(bleSession) { runBasalProgram(any(), any(), any()) }
    }

    @Test
    fun `both extended bolus and temp basal are cancelled when both are running`() {
        stubInfusionInfo(
            CarelevoInfusionInfoDomainModel(
                extendBolusInfusionInfo = extendBolusInfusion(),
                tempBasalInfusionInfo = tempBasalInfusion()
            )
        )
        val extendCalls = AtomicInteger(0)
        val tempCalls = AtomicInteger(0)

        val result = sut.updateBasalProfile(
            profile,
            { extendCalls.incrementAndGet(); enact(true) },
            { tempCalls.incrementAndGet(); enact(true) },
            { }
        )

        assertThat(result.success).isTrue()
        assertThat(extendCalls.get()).isEqualTo(1)
        assertThat(tempCalls.get()).isEqualTo(1)
        verifyBlocking(bleSession) { runBasalProgram(any(), any(), any()) }
    }

    // --- failure / guard paths ---------------------------------------------------------------------

    @Test
    fun `missing patch address fails without enacting and without updating the profile`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        var updated: Profile? = null

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updated = it })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(updated).isNull()
    }

    @Test
    fun `rejected program write fails and does not update the profile`() {
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(false)
        var updated: Profile? = null

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updated = it })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(updated).isNull()
        verify(setBasalProgramUseCase, never()).persistBasalProgram(any())
    }

    @Test
    fun `program write exception is mapped to a failed result`() {
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenAnswer { throw RuntimeException("ble boom") }
        var updated: Profile? = null

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updated = it })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(updated).isNull()
    }

    @Test
    fun `persist failure fails after a successful write and does not update the profile`() {
        whenever(setBasalProgramUseCase.persistBasalProgram(any())).thenReturn(false)
        var updated: Profile? = null

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updated = it })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(updated).isNull()
        verifyBlocking(bleSession) { runBasalProgram(any(), any(), any()) }
    }

    @Test
    fun `extended bolus cancel rejection fails before any program write`() {
        stubInfusionInfo(CarelevoInfusionInfoDomainModel(extendBolusInfusionInfo = extendBolusInfusion()))

        val result = sut.updateBasalProfile(profile, { enact(false) }, { enact(true) }, { })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(bleSession, never()) { runBasalProgram(any(), any(), any()) }
    }

    @Test
    fun `temp basal cancel rejection fails before any program write`() {
        stubInfusionInfo(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasalInfusion()))

        val result = sut.updateBasalProfile(profile, { enact(true) }, { enact(false) }, { })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(bleSession, never()) { runBasalProgram(any(), any(), any()) }
    }

    // --- debounce ----------------------------------------------------------------------------------

    @Test
    fun `repeated call within the debounce window is skipped after a success`() {
        var updateCount = 0
        val first = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updateCount++ })
        assertThat(first.success).isTrue()
        assertThat(first.enacted).isTrue()

        val second = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { updateCount++ })

        // Benign debounce: success=true but NOT enacted, and the pump work happened only once.
        assertThat(second.success).isTrue()
        assertThat(second.enacted).isFalse()
        assertThat(updateCount).isEqualTo(1)
        verifyBlocking(bleSession, times(1)) { runBasalProgram(any(), any(), any()) }
    }

    @Test
    fun `repeated call within the debounce window is skipped after a failure`() {
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(false)

        val first = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { })
        assertThat(first.success).isFalse()

        val second = sut.updateBasalProfile(profile, { enact(true) }, { enact(true) }, { })

        assertThat(second.success).isTrue()
        assertThat(second.enacted).isFalse()
        verifyBlocking(bleSession, times(1)) { runBasalProgram(any(), any(), any()) }
    }

    private companion object {

        private const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }

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
