package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoDefaultRequestModel
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel {
        return CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now().minusMinutes(1)
        )
    }
}
