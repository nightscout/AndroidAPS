package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoPatchCannulaInsertionConfirmUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()

    private val sut = CarelevoPatchCannulaInsertionConfirmUseCase(
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        patchInfoRepository = patchInfoRepository
    )

    @Test
    fun execute_returns_success_when_confirm_and_patch_update_succeed() {
        whenever(patchRepository.requestConfirmCannulaInsertionCheck(true))
            .thenReturn(Single.just(RequestResult.Pending(true)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_confirm_is_not_pending() {
        whenever(patchRepository.requestConfirmCannulaInsertionCheck(true))
            .thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_pending_data_is_false() {
        whenever(patchRepository.requestConfirmCannulaInsertionCheck(true))
            .thenReturn(Single.just(RequestResult.Pending(false)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_patch_info_is_null() {
        whenever(patchRepository.requestConfirmCannulaInsertionCheck(true))
            .thenReturn(Single.just(RequestResult.Pending(true)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_patch_update_fails() {
        whenever(patchRepository.requestConfirmCannulaInsertionCheck(true))
            .thenReturn(Single.just(RequestResult.Pending(true)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel {
        return CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now().minusMinutes(5),
            manufactureNumber = "EO12507099001",
            firmwareVersion = "T166",
            bootDateTime = "2603051158",
            modelName = "6776514848",
            insulinAmount = 300,
            insulinRemain = 300.0,
            thresholdInsulinRemain = 30,
            thresholdExpiry = 116,
            thresholdMaxBasalSpeed = 15.0,
            thresholdMaxBolusDose = 25.0
        )
    }
}
