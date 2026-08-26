package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoSetBasalProgramUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoSetBasalProgramUseCase(patchInfoRepository, infusionInfoRepository)

    private fun patchInfo(address: String = "AA:BB:CC:DD:EE:FF"): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = address,
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            mode = 0
        )

    private fun profileWith(vararg values: Profile.ProfileValue): Profile = mock<Profile>().also {
        whenever(it.getBasalValues()).thenReturn(arrayOf(*values))
    }

    // ---------- buildBasalProgramPlan ----------

    @Test
    fun `buildBasalProgramPlan produces 24 segments and three eight-slot programs for a single segment`() {
        val profile = profileWith(Profile.ProfileValue(0, 1.0))

        val plan = sut.buildBasalProgramPlan(profile)

        assertThat(plan.segments).hasSize(24)
        assertThat(plan.programs).hasSize(3)
        plan.programs.forEach { assertThat(it).hasSize(8) }
        assertThat(plan.programs[0]).containsExactly(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        assertThat(plan.segments.map { it.speed }.distinct()).containsExactly(1.0)
    }

    @Test
    fun `buildBasalProgramPlan maps a two-segment profile across the chunk boundary`() {
        // 0-12h at 1.0, 12-24h at 2.0 (43200s = 720min = 12h).
        val profile = profileWith(
            Profile.ProfileValue(0, 1.0),
            Profile.ProfileValue(43_200, 2.0)
        )

        val plan = sut.buildBasalProgramPlan(profile)

        assertThat(plan.segments).hasSize(24)
        // hours 0..11 -> 1.0, hours 12..23 -> 2.0
        assertThat(plan.segments[0].speed).isEqualTo(1.0)
        assertThat(plan.segments[11].speed).isEqualTo(1.0)
        assertThat(plan.segments[12].speed).isEqualTo(2.0)
        assertThat(plan.segments[23].speed).isEqualTo(2.0)
        // chunk boundary at index 8..15 (program 1): first four 1.0, last four 2.0
        assertThat(plan.programs[0]).containsExactly(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0).inOrder()
        assertThat(plan.programs[1]).containsExactly(1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0).inOrder()
        assertThat(plan.programs[2]).containsExactly(2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0).inOrder()
    }

    @Test
    fun `buildBasalProgramPlan yields zero-speed segments for an empty profile`() {
        val profile = profileWith()

        val plan = sut.buildBasalProgramPlan(profile)

        assertThat(plan.segments).hasSize(24)
        assertThat(plan.programs).hasSize(3)
        assertThat(plan.segments.map { it.speed }.distinct()).containsExactly(0.0)
        assertThat(plan.programs[0]).containsExactly(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    // ---------- persistBasalProgram ----------

    @Test
    fun `persistBasalProgram returns false when no patch record exists`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistBasalProgram(emptyList())).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
        verify(infusionInfoRepository, never()).updateBasalInfusionInfo(any())
    }

    @Test
    fun `persistBasalProgram returns false when patch update fails`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistBasalProgram(emptyList())).isFalse()
        verify(infusionInfoRepository, never()).updateBasalInfusionInfo(any())
    }

    @Test
    fun `persistBasalProgram returns false when basal infusion update fails`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(false)

        assertThat(sut.persistBasalProgram(emptyList())).isFalse()
    }

    @Test
    fun `persistBasalProgram returns true and persists mode 1 with mapped segments`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo("11:22:33:44:55:66"))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)

        val segments = listOf(CarelevoBasalSegmentDomainModel(startTime = 60, endTime = 120, speed = 1.5))
        assertThat(sut.persistBasalProgram(segments)).isTrue()

        val patchCaptor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(patchCaptor.capture())
        assertThat(patchCaptor.firstValue.mode).isEqualTo(1)
        assertThat(patchCaptor.firstValue.address).isEqualTo("11:22:33:44:55:66")

        val infusionCaptor = argumentCaptor<CarelevoBasalInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateBasalInfusionInfo(infusionCaptor.capture())
        val persisted = infusionCaptor.firstValue
        assertThat(persisted.address).isEqualTo("11:22:33:44:55:66")
        assertThat(persisted.mode).isEqualTo(1)
        assertThat(persisted.isStop).isFalse()
        assertThat(persisted.segments).hasSize(1)
        assertThat(persisted.segments[0].startTime).isEqualTo(60)
        assertThat(persisted.segments[0].endTime).isEqualTo(120)
        assertThat(persisted.segments[0].speed).isEqualTo(1.5)
    }

    @Test
    fun `persistBasalProgram returns false when updateBasalInfusionInfo throws`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistBasalProgram(emptyList())).isFalse()
    }
}
