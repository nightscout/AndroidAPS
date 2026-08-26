package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
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
internal class CarelevoCancelTempBasalInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoCancelTempBasalInfusionUseCase(patchInfoRepository, infusionInfoRepository)

    private fun patchInfo(): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(address = "AA:BB:CC:DD:EE:FF", createdAt = DateTime.now(), updatedAt = DateTime.now(), mode = 2)

    private fun basalInfusion(isStop: Boolean): CarelevoBasalInfusionInfoDomainModel =
        CarelevoBasalInfusionInfoDomainModel(infusionId = "b", address = "AA", mode = 1, segments = emptyList(), isStop = isStop)

    private fun tempBasalInfusion(): CarelevoTempBasalInfusionInfoDomainModel =
        CarelevoTempBasalInfusionInfoDomainModel(infusionId = "t", address = "AA", mode = 2)

    private fun immeBolusInfusion(): CarelevoImmeBolusInfusionInfoDomainModel =
        CarelevoImmeBolusInfusionInfoDomainModel(infusionId = "i", address = "AA", mode = 3)

    private fun extendBolusInfusion(): CarelevoExtendBolusInfusionInfoDomainModel =
        CarelevoExtendBolusInfusionInfoDomainModel(infusionId = "e", address = "AA", mode = 5)

    private fun capturedMode(): Int {
        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        return captor.firstValue.mode!!
    }

    // ---------- guard / failure branches ----------

    @Test
    fun `returns false when delete temp basal fails and never reads infusion info`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(false)

        assertThat(sut.persistTempBasalCancelled()).isFalse()
        verify(infusionInfoRepository, never()).getInfusionInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when infusion info is missing`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(null)

        assertThat(sut.persistTempBasalCancelled()).isFalse()
        verify(patchInfoRepository, never()).getPatchInfoBySync()
    }

    @Test
    fun `returns false when derived mode is null because no infusion records remain`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())

        assertThat(sut.persistTempBasalCancelled()).isFalse()
        verify(patchInfoRepository, never()).getPatchInfoBySync()
    }

    @Test
    fun `returns false when patch info is missing`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistTempBasalCancelled()).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when patch update fails`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistTempBasalCancelled()).isFalse()
    }

    @Test
    fun `returns false when delete throws`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistTempBasalCancelled()).isFalse()
    }

    // ---------- success + derivePatchMode branches ----------

    @Test
    fun `success derives BASAL_RUNNING mode 1 from a running basal`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalCancelled()).isTrue()
        assertThat(capturedMode()).isEqualTo(1)
    }

    @Test
    fun `success derives BASAL_STOPPED mode 0 from a stopped basal`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = true)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalCancelled()).isTrue()
        assertThat(capturedMode()).isEqualTo(0)
    }

    @Test
    fun `success derives TEMP_BASAL mode 2 when a temp basal is still present`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasalInfusion()))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalCancelled()).isTrue()
        assertThat(capturedMode()).isEqualTo(2)
    }

    @Test
    fun `success derives IMME_BOLUS mode 3 when an immediate bolus is present`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(immeBolusInfusionInfo = immeBolusInfusion()))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalCancelled()).isTrue()
        assertThat(capturedMode()).isEqualTo(3)
    }

    @Test
    fun `success derives EXTEND_BOLUS mode 5 when an extended bolus is present`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(extendBolusInfusionInfo = extendBolusInfusion()))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistTempBasalCancelled()).isTrue()
        assertThat(capturedMode()).isEqualTo(5)
    }
}
