package app.aaps.pump.carelevo.domain.usecase.bolus

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
internal class CarelevoCancelImmeBolusInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoCancelImmeBolusInfusionUseCase(patchInfoRepository, infusionInfoRepository)

    private fun patchInfo(address: String = "AA:BB:CC:DD:EE:FF"): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(address = address, createdAt = DateTime.now(), updatedAt = DateTime.now(), mode = 3)

    private fun basalInfusion(isStop: Boolean): CarelevoBasalInfusionInfoDomainModel =
        CarelevoBasalInfusionInfoDomainModel(infusionId = "b", address = "AA", mode = 1, segments = emptyList(), isStop = isStop)

    private fun tempBasalInfusion(): CarelevoTempBasalInfusionInfoDomainModel =
        CarelevoTempBasalInfusionInfoDomainModel(infusionId = "t", address = "AA", mode = 2)

    private fun immeBolusInfusion(): CarelevoImmeBolusInfusionInfoDomainModel =
        CarelevoImmeBolusInfusionInfoDomainModel(infusionId = "i", address = "AA", mode = 3)

    private fun extendBolusInfusion(): CarelevoExtendBolusInfusionInfoDomainModel =
        CarelevoExtendBolusInfusionInfoDomainModel(infusionId = "e", address = "AA", mode = 5)

    private fun capturedPatch(): CarelevoPatchInfoDomainModel {
        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        return captor.firstValue
    }

    // ---------- guard / failure branches ----------

    @Test
    fun `returns false when delete imme bolus fails and never reads infusion info`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(false)

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
        verify(infusionInfoRepository, never()).getInfusionInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when infusion info is missing`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(null)

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
        verify(patchInfoRepository, never()).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when derived mode is null because no infusion records remain`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
        // mode derivation precedes the patch read in this use case
        verify(patchInfoRepository, never()).getPatchInfoBySync()
    }

    @Test
    fun `returns false when patch info is missing`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `returns false when patch update fails`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
    }

    @Test
    fun `returns false when delete throws`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
    }

    @Test
    fun `returns false when patch update throws`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenThrow(RuntimeException("boom"))

        assertThat(sut.persistImmeBolusCancelled()).isFalse()
    }

    // ---------- success + derivePatchMode branches ----------

    @Test
    fun `success derives BASAL_RUNNING mode 1 from a running basal`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusCancelled()).isTrue()
        assertThat(capturedPatch().mode).isEqualTo(1)
    }

    @Test
    fun `success derives BASAL_STOPPED mode 0 from a stopped basal`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = true)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusCancelled()).isTrue()
        assertThat(capturedPatch().mode).isEqualTo(0)
    }

    @Test
    fun `success derives TEMP_BASAL mode 2 when a temp basal is still running`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasalInfusion()))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusCancelled()).isTrue()
        assertThat(capturedPatch().mode).isEqualTo(2)
    }

    @Test
    fun `success derives EXTEND_BOLUS mode 5 when an extended bolus outranks a leftover imme bolus`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(
            CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = basalInfusion(isStop = false),
                immeBolusInfusionInfo = immeBolusInfusion(),
                extendBolusInfusionInfo = extendBolusInfusion()
            )
        )
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusCancelled()).isTrue()
        assertThat(capturedPatch().mode).isEqualTo(5)
    }

    @Test
    fun `success preserves the patch identity and only rewrites mode and updatedAt`() {
        val original = patchInfo("11:22:33:44:55:66").copy(bolusActionSeq = 7, insulinRemain = 120.0, updatedAt = DateTime.now().minusHours(1))
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(original)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.persistImmeBolusCancelled()).isTrue()

        val persisted = capturedPatch()
        assertThat(persisted.address).isEqualTo("11:22:33:44:55:66")
        assertThat(persisted.mode).isEqualTo(1)
        // bolusActionSeq is deliberately NOT cleared by the cancel persist
        assertThat(persisted.bolusActionSeq).isEqualTo(7)
        assertThat(persisted.insulinRemain).isEqualTo(120.0)
        assertThat(persisted.updatedAt.millis).isGreaterThan(original.updatedAt.millis)
    }
}
