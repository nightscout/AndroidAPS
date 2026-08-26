package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
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
internal class CarelevoStartTempBasalInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoStartTempBasalInfusionUseCase(patchInfoRepository, infusionInfoRepository)

    private fun patchInfo(address: String = "AA:BB:CC:DD:EE:FF"): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(address = address, createdAt = DateTime.now(), updatedAt = DateTime.now(), mode = 1)

    private fun request(
        isUnit: Boolean = false,
        speed: Double? = 1.5,
        percent: Int? = 50,
        minutes: Int = 30
    ) = StartTempBasalInfusionRequestModel(isUnit = isUnit, speed = speed, percent = percent, minutes = minutes)

    // ---------- guard / failure branches ----------

    @Test
    fun `returns false when no patch record exists and never writes infusion info`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistTempBasalStarted(request())).isFalse()
        verify(infusionInfoRepository, never()).updateTempBasalInfusionInfo(any())
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when temp basal infusion update fails and never updates patch`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateTempBasalInfusionInfo(any())).thenReturn(false)

        assertThat(sut.persistTempBasalStarted(request())).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when patch update fails`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateTempBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistTempBasalStarted(request())).isFalse()
    }

    @Test
    fun `returns false when infusion update throws`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateTempBasalInfusionInfo(any())).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistTempBasalStarted(request())).isFalse()
    }

    // ---------- success ----------

    @Test
    fun `success persists mode 2 temp basal info and patch mode 2`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo("11:22:33:44:55:66"))
        whenever(infusionInfoRepository.updateTempBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalStarted(request(isUnit = false, speed = 2.0, percent = 75, minutes = 45))).isTrue()

        val infusionCaptor = argumentCaptor<CarelevoTempBasalInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateTempBasalInfusionInfo(infusionCaptor.capture())
        val persisted = infusionCaptor.firstValue
        assertThat(persisted.address).isEqualTo("11:22:33:44:55:66")
        assertThat(persisted.mode).isEqualTo(2)
        assertThat(persisted.percent).isEqualTo(75)
        assertThat(persisted.speed).isEqualTo(2.0)
        assertThat(persisted.infusionDurationMin).isEqualTo(45)

        val patchCaptor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(patchCaptor.capture())
        assertThat(patchCaptor.firstValue.mode).isEqualTo(2)
    }

    @Test
    fun `success passes through null percent and null speed`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateTempBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalStarted(request(isUnit = true, speed = null, percent = null, minutes = 60))).isTrue()

        val infusionCaptor = argumentCaptor<CarelevoTempBasalInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateTempBasalInfusionInfo(infusionCaptor.capture())
        val persisted = infusionCaptor.firstValue
        assertThat(persisted.percent).isNull()
        assertThat(persisted.speed).isNull()
        assertThat(persisted.infusionDurationMin).isEqualTo(60)
        assertThat(persisted.mode).isEqualTo(2)
    }
}
