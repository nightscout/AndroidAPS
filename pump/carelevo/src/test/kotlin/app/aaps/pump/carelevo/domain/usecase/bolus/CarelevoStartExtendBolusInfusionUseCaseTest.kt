package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
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
internal class CarelevoStartExtendBolusInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoStartExtendBolusInfusionUseCase(patchInfoRepository, infusionInfoRepository)

    private fun patchInfo(address: String = "AA:BB:CC:DD:EE:FF"): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(address = address, createdAt = DateTime.now(), updatedAt = DateTime.now(), mode = 1)

    private fun capturedInfusion(): CarelevoExtendBolusInfusionInfoDomainModel {
        val captor = argumentCaptor<CarelevoExtendBolusInfusionInfoDomainModel>()
        verify(infusionInfoRepository).updateExtendBolusInfusionInfo(captor.capture())
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

        assertThat(sut.persistExtendBolusStarted(volume = 2.0, speed = 4.0, minutes = 30)).isFalse()
        verify(infusionInfoRepository, never()).updateExtendBolusInfusionInfo(any())
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when extend bolus infusion update fails and never updates patch`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(false)

        assertThat(sut.persistExtendBolusStarted(volume = 2.0, speed = 4.0, minutes = 30)).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when patch update fails`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistExtendBolusStarted(volume = 2.0, speed = 4.0, minutes = 30)).isFalse()
    }

    @Test
    fun `returns false when patch read throws`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistExtendBolusStarted(volume = 2.0, speed = 4.0, minutes = 30)).isFalse()
        verify(infusionInfoRepository, never()).updateExtendBolusInfusionInfo(any())
    }

    @Test
    fun `returns false when infusion update throws`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistExtendBolusStarted(volume = 2.0, speed = 4.0, minutes = 30)).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    // ---------- success ----------

    @Test
    fun `success persists mode 5 extend bolus info carrying volume speed and duration`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo("11:22:33:44:55:66"))
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistExtendBolusStarted(volume = 3.0, speed = 6.0, minutes = 30)).isTrue()

        val persisted = capturedInfusion()
        assertThat(persisted.address).isEqualTo("11:22:33:44:55:66")
        assertThat(persisted.mode).isEqualTo(5)
        assertThat(persisted.volume).isEqualTo(3.0)
        assertThat(persisted.speed).isEqualTo(6.0)
        assertThat(persisted.infusionDurationMin).isEqualTo(30)
        assertThat(persisted.infusionId).isNotEmpty()
    }

    @Test
    fun `success stamps patch mode 5 and leaves bolus action seq untouched`() {
        val original = patchInfo().copy(bolusActionSeq = 4, updatedAt = DateTime.now().minusHours(1))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(original)
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistExtendBolusStarted(volume = 3.0, speed = 6.0, minutes = 30)).isTrue()

        val persisted = capturedPatch()
        assertThat(persisted.mode).isEqualTo(5)
        // the extend-bolus start does NOT allocate an action seq (unlike the imme-bolus start)
        assertThat(persisted.bolusActionSeq).isEqualTo(4)
        assertThat(persisted.updatedAt.millis).isGreaterThan(original.updatedAt.millis)
    }

    @Test
    fun `success writes infusion info before the patch mode flip`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistExtendBolusStarted(volume = 1.0, speed = 2.0, minutes = 30)).isTrue()

        val order = inOrder(infusionInfoRepository, patchInfoRepository)
        order.verify(infusionInfoRepository).updateExtendBolusInfusionInfo(any())
        order.verify(patchInfoRepository).updatePatchInfo(any())
    }

    @Test
    fun `each started extend bolus gets a distinct infusion id`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistExtendBolusStarted(volume = 1.0, speed = 2.0, minutes = 30)).isTrue()
        assertThat(sut.persistExtendBolusStarted(volume = 1.0, speed = 2.0, minutes = 30)).isTrue()

        val captor = argumentCaptor<CarelevoExtendBolusInfusionInfoDomainModel>()
        verify(infusionInfoRepository, times(2)).updateExtendBolusInfusionInfo(captor.capture())
        assertThat(captor.firstValue.infusionId).isNotEqualTo(captor.secondValue.infusionId)
    }

    @Test
    fun `success stores the caller supplied speed verbatim without recomputing it from volume and minutes`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        // 2.5 U over 90 min would be 1.666.. U/h; the use case must persist exactly what it was handed
        assertThat(sut.persistExtendBolusStarted(volume = 2.5, speed = 1.5, minutes = 90)).isTrue()

        val persisted = capturedInfusion()
        assertThat(persisted.volume).isEqualTo(2.5)
        assertThat(persisted.speed).isEqualTo(1.5)
        assertThat(persisted.infusionDurationMin).isEqualTo(90)
    }
}
