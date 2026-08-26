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

internal class CarelevoPumpStopUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoPumpStopUseCase(patchInfoRepository, infusionInfoRepository)

    private fun basalInfo(
        mode: Int = 1,
        isStop: Boolean = false
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
            isStopped = false,
            stopMinutes = null,
            stopMode = null,
            isForceStopped = null,
            mode = 1
        )

    // ---- success ----

    @Test
    fun `persistStopped returns true when all steps succeed`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistStopped(30)).isTrue()
    }

    // ---- guard branches (each returns false) ----

    @Test
    fun `persistStopped returns false when infusion info is null`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(null)

        assertThat(sut.persistStopped(30)).isFalse()
        verify(infusionInfoRepository, never()).updateBasalInfusionInfo(any())
        verify(patchInfoRepository, never()).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `persistStopped returns false when basal infusion info is null`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo(basal = null))

        assertThat(sut.persistStopped(30)).isFalse()
        verify(infusionInfoRepository, never()).updateBasalInfusionInfo(any())
        verify(patchInfoRepository, never()).getPatchInfoBySync()
    }

    @Test
    fun `persistStopped returns false when updateBasalInfusionInfo fails`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(false)

        assertThat(sut.persistStopped(30)).isFalse()
        // Short-circuits before touching the patch record.
        verify(patchInfoRepository, never()).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `persistStopped returns false when patch info is null`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistStopped(30)).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `persistStopped returns the updatePatchInfo result when the patch write fails`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistStopped(30)).isFalse()
    }

    // ---- persisted-content assertions ----

    @Test
    fun `persistStopped writes the basal copy with mode 0 and isStop true`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo(basal = basalInfo(mode = 1, isStop = false)))
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        sut.persistStopped(30)

        val captor = argumentCaptor<CarelevoBasalInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateBasalInfusionInfo(captor.capture())
        assertThat(captor.firstValue.mode).isEqualTo(0)
        assertThat(captor.firstValue.isStop).isTrue()
        // Identity fields preserved by copy().
        assertThat(captor.firstValue.infusionId).isEqualTo("inf-1")
        assertThat(captor.firstValue.address).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `persistStopped writes the patch copy with stop fields and the given duration`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        sut.persistStopped(45)

        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.isStopped).isTrue()
        assertThat(saved.stopMinutes).isEqualTo(45)
        assertThat(saved.stopMode).isEqualTo(0)
        assertThat(saved.isForceStopped).isFalse()
        assertThat(saved.mode).isEqualTo(0)
    }

    @Test
    fun `persistStopped propagates a zero duration into the patch stopMinutes`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo())
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        sut.persistStopped(0)

        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        assertThat(captor.firstValue.stopMinutes).isEqualTo(0)
    }
}
