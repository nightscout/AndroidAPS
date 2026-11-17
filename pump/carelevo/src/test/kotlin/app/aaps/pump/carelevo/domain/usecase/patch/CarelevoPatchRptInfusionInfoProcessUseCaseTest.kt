package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoDefaultRequestModel
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class CarelevoPatchRptInfusionInfoProcessUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val sut = CarelevoPatchRptInfusionInfoProcessUseCase(patchInfoRepository)

    @Test
    fun execute_returns_success_for_full_report_request() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        val request = CarelevoPatchRptInfusionInfoRequestModel(
            runningMinute = 120,
            remains = 230.5,
            infusedTotalBasalAmount = 12.3,
            infusedTotalBolusAmount = 4.5,
            pumpState = 1,
            mode = 3,
            currentInfusedProgramVolume = 0.0,
            realInfusedTime = 0
        )

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_success_for_default_report_request() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        val request = CarelevoPatchRptInfusionInfoDefaultRequestModel(remains = 199.0)

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_for_invalid_request_type() {
        val invalidRequest = object : CarelevoUseCaseRequest {}

        val result = sut.execute(invalidRequest).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_patch_info_missing() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        val result = sut.execute(CarelevoPatchRptInfusionInfoDefaultRequestModel(remains = 99.0)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun `a full report stores every value it carries`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        val request = CarelevoPatchRptInfusionInfoRequestModel(
            runningMinute = 120, remains = 230.5, infusedTotalBasalAmount = 12.3, infusedTotalBolusAmount = 4.5,
            pumpState = 1, mode = 3, currentInfusedProgramVolume = 0.0, realInfusedTime = 0
        )

        sut.execute(request).blockingGet()

        val stored = argumentCaptor<CarelevoPatchInfoDomainModel>().also { verify(patchInfoRepository).updatePatchInfo(it.capture()) }.firstValue
        assertThat(stored.address).isEqualTo("94:b2:16:1d:2f:6d")
        assertThat(stored.mode).isEqualTo(3)
        assertThat(stored.runningMinutes).isEqualTo(120)
        assertThat(stored.insulinRemain).isEqualTo(230.5)
        assertThat(stored.infusedTotalBasalAmount).isEqualTo(12.3)
        assertThat(stored.infusedTotalBolusAmount).isEqualTo(4.5)
        assertThat(stored.pumpState).isEqualTo(1)
    }

    @Test
    fun `a default report changes only the insulin left`() {
        val before = samplePatchInfo()
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(before)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        sut.execute(CarelevoPatchRptInfusionInfoDefaultRequestModel(remains = 199.0)).blockingGet()

        val stored = argumentCaptor<CarelevoPatchInfoDomainModel>().also { verify(patchInfoRepository).updatePatchInfo(it.capture()) }.firstValue
        assertThat(stored).isEqualTo(before.copy(insulinRemain = 199.0))
    }

    @Test
    fun `a failed write is an error`() {
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        val result = sut.execute(CarelevoPatchRptInfusionInfoDefaultRequestModel(remains = 99.0)).blockingGet()

        assertThat((result as ResponseResult.Error).e.message).isEqualTo("update patch info is failed")
    }

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel {
        return CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = Clock.System.now() - 1.hours,
            updatedAt = Clock.System.now() - 1.minutes
        )
    }
}
