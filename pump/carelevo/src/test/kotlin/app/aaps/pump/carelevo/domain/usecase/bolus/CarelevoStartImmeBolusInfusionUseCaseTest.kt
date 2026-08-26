package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoStartImmeBolusInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoStartImmeBolusInfusionUseCase(patchInfoRepository, infusionInfoRepository)

    private fun patchInfo(address: String = "AA:BB:CC:DD:EE:FF"): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(address = address, createdAt = DateTime.now(), updatedAt = DateTime.now(), mode = 1)

    private fun capturedInfusion(): CarelevoImmeBolusInfusionInfoDomainModel {
        val captor = argumentCaptor<CarelevoImmeBolusInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateImmeBolusInfusionInfo(captor.capture())
        return captor.firstValue
    }

    private fun capturedPatch(): CarelevoPatchInfoDomainModel {
        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        return captor.firstValue
    }

    // ---------- guard / failure branches ----------

    @Test
    fun `returns false when no patch record exists and never writes infusion info`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 1, volume = 2.0, expectedTimeSeconds = 30)).isFalse()
        verify(infusionInfoRepository, never()).updateImmeBolusInfusionInfo(any())
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when imme bolus infusion update fails and never updates patch`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(false)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 1, volume = 2.0, expectedTimeSeconds = 30)).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when patch update fails`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 1, volume = 2.0, expectedTimeSeconds = 30)).isFalse()
    }

    @Test
    fun `returns false when patch read throws`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistImmeBolusStarted(actionSeq = 1, volume = 2.0, expectedTimeSeconds = 30)).isFalse()
        verify(infusionInfoRepository, never()).updateImmeBolusInfusionInfo(any())
    }

    @Test
    fun `returns false when infusion update throws`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistImmeBolusStarted(actionSeq = 1, volume = 2.0, expectedTimeSeconds = 30)).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    // ---------- success ----------

    @Test
    fun `success persists mode 3 imme bolus info carrying volume and expected duration`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo("11:22:33:44:55:66"))
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 9, volume = 3.5, expectedTimeSeconds = 42)).isTrue()

        val persisted = capturedInfusion()
        assertThat(persisted.address).isEqualTo("11:22:33:44:55:66")
        assertThat(persisted.mode).isEqualTo(3)
        assertThat(persisted.volume).isEqualTo(3.5)
        assertThat(persisted.infusionDurationSeconds).isEqualTo(42)
        assertThat(persisted.infusionId).isNotEmpty()
    }

    @Test
    fun `success stamps patch mode 3 and the bolus action seq`() {
        val original = patchInfo().copy(updatedAt = DateTime.now().minusHours(1))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(original)
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 9, volume = 3.5, expectedTimeSeconds = 42)).isTrue()

        val persisted = capturedPatch()
        assertThat(persisted.mode).isEqualTo(3)
        assertThat(persisted.bolusActionSeq).isEqualTo(9)
        assertThat(persisted.updatedAt.millis).isGreaterThan(original.updatedAt.millis)
    }

    @Test
    fun `success writes infusion info before the patch mode flip`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 0, volume = 1.0, expectedTimeSeconds = 10)).isTrue()

        val order = inOrder(infusionInfoRepository, patchInfoRepository)
        order.verify(infusionInfoRepository).updateImmeBolusInfusionInfo(any())
        order.verify(patchInfoRepository).updatePatchInfo(any())
    }

    @Test
    fun `each started bolus gets a distinct infusion id`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 1, volume = 1.0, expectedTimeSeconds = 10)).isTrue()
        assertThat(sut.persistImmeBolusStarted(actionSeq = 2, volume = 1.0, expectedTimeSeconds = 10)).isTrue()

        val captor = argumentCaptor<CarelevoImmeBolusInfusionInfoDomainModel>()
        verify(infusionInfoRepository, times(2)).updateImmeBolusInfusionInfo(captor.capture())
        assertThat(captor.firstValue.infusionId).isNotEqualTo(captor.secondValue.infusionId)
    }

    @Test
    fun `success accepts a zero expected time and a zero action seq`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusStarted(actionSeq = 0, volume = 0.05, expectedTimeSeconds = 0)).isTrue()

        assertThat(capturedInfusion().infusionDurationSeconds).isEqualTo(0)
        assertThat(capturedPatch().bolusActionSeq).isEqualTo(0)
    }
}
