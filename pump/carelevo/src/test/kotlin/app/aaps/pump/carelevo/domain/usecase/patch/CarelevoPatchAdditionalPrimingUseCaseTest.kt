package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.AdditionalPrimingResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoPatchAdditionalPrimingUseCaseTest {

    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoPatchAdditionalPrimingUseCase(
        patchRepository = patchRepository,
        patchObserver = patchObserver
    )

    @Test
    fun execute_returns_success_when_priming_succeeds() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestAdditionalPriming())
            .thenAnswer {
                emitAsync(AdditionalPrimingResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_request_is_not_pending() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestAdditionalPriming())
            .thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_priming_result_is_failed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestAdditionalPriming())
            .thenAnswer {
                emitAsync(AdditionalPrimingResultModel(Result.FAILED))
                Single.just(RequestResult.Pending(true))
            }

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            Thread.sleep(delayMs)
            patchEvent.onNext(event)
        }.start()
    }
}
