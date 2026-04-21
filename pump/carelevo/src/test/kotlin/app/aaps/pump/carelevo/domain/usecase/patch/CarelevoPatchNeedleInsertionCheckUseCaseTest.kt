package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.CannulaInsertionResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.NeedleCheckFailed
import app.aaps.pump.carelevo.domain.model.patch.NeedleCheckSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoPatchNeedleInsertionCheckUseCaseTest {

    private val aapsLogger: AAPSLogger = mock()
    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoPatchNeedleInsertionCheckUseCase(
        aapsLogger = aapsLogger,
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        patchInfoRepository = patchInfoRepository
    )

    @Test
    fun execute_returns_success_payload_when_insertion_success() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestCannulaInsertionCheck()).thenAnswer {
            emitAsync(CannulaInsertionResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo(failedCount = 0))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        val data = (result as ResponseResult.Success).data
        assertThat(data).isEqualTo(NeedleCheckSuccess)
    }

    @Test
    fun execute_returns_failed_payload_when_insertion_failed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestCannulaInsertionCheck()).thenAnswer {
            emitAsync(CannulaInsertionResultModel(Result.FAILED))
            Single.just(RequestResult.Pending(true))
        }
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo(failedCount = 2))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        val data = (result as ResponseResult.Success).data
        assertThat(data).isEqualTo(NeedleCheckFailed(3))
    }

    @Test
    fun execute_returns_error_when_request_not_pending() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestCannulaInsertionCheck()).thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun samplePatchInfo(failedCount: Int): CarelevoPatchInfoDomainModel {
        return CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now().minusMinutes(1),
            needleFailedCount = failedCount
        )
    }

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            Thread.sleep(delayMs)
            patchEvent.onNext(event)
        }.start()
    }
}
