package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class CarelevoPumpResumeUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoPumpResumeUseCase(patchInfoRepository, infusionInfoRepository)

    private fun basalInfo(
        mode: Int = 0,
        isStop: Boolean = true
    ): CarelevoBasalInfusionInfoDomainModel =
        CarelevoBasalInfusionInfoDomainModel(
            infusionId = "inf-1",
            address = "AA:BB:CC:DD:EE:FF",
            mode = mode,
            segments = emptyList(),
            isStop = isStop
        )

    private fun infusionInfo(basal: CarelevoBasalInfusionInfoDomainModel? = basalInfo()): CarelevoInfusionInfoDomainModel =
        CarelevoInfusionInfoDomainModel(basalInfusionInfo = basal)

    private fun patchInfo(): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "AA:BB:CC:DD:EE:FF",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            isStopped = true,
            stopMinutes = 30,
            stopMode = 0,
            isForceStopped = true,
            mode = 0
        )

    // ---- success ----

    @Test
    fun `persistResumed returns true when all steps succeed`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistResumed()).isTrue()
    }

    // ---- guard branches (each returns false) ----

    @Test
    fun `persistResumed returns false when infusion info is null`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(null)

        assertThat(sut.persistResumed()).isFalse()
        verify(infusionInfoRepository, never()).updateBasalInfusionInfo(any())
        verify(patchInfoRepository, never()).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `persistResumed returns false when basal infusion info is null`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo(basal = null))

        assertThat(sut.persistResumed()).isFalse()
        verify(infusionInfoRepository, never()).updateBasalInfusionInfo(any())
        verify(patchInfoRepository, never()).getPatchInfoBySync()
    }

    @Test
    fun `persistResumed returns false when updateBasalInfusionInfo fails`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(false)

        assertThat(sut.persistResumed()).isFalse()
        // Short-circuits before touching the patch record.
        verify(patchInfoRepository, never()).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `persistResumed returns false when patch info is null`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistResumed()).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `persistResumed returns the updatePatchInfo result when the patch write fails`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistResumed()).isFalse()
    }

    // ---- persisted-content assertions ----

    @Test
    fun `persistResumed writes the basal copy with mode 1 and isStop false`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo(basal = basalInfo(mode = 0, isStop = true)))
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        sut.persistResumed()

        val captor = argumentCaptor<CarelevoBasalInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateBasalInfusionInfo(captor.capture())
        assertThat(captor.firstValue.mode).isEqualTo(1)
        assertThat(captor.firstValue.isStop).isFalse()
        // Identity fields preserved by copy().
        assertThat(captor.firstValue.infusionId).isEqualTo("inf-1")
        assertThat(captor.firstValue.address).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `persistResumed writes the patch copy clearing all stop fields with mode 1`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        sut.persistResumed()

        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.isStopped).isFalse()
        assertThat(saved.stopMinutes).isNull()
        assertThat(saved.stopMode).isNull()
        assertThat(saved.isForceStopped).isNull()
        assertThat(saved.mode).isEqualTo(1)
    }
}
