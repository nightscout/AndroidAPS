package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.SetTimeResultModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchTimeZoneRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoPatchTimeZoneUpdateUseCaseTest {

    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoPatchTimeZoneUpdateUseCase(
        patchRepository = patchRepository,
        patchObserver = patchObserver
    )

    @Test
    fun execute_returns_success_when_set_time_succeeds() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetTime(any())).thenAnswer {
            emitAsync(SetTimeResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute(CarelevoPatchTimeZoneRequestModel(insulinAmount = 300)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_set_time_not_pending() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetTime(any())).thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute(CarelevoPatchTimeZoneRequestModel(insulinAmount = 300)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_result_failed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetTime(any())).thenAnswer {
            emitAsync(SetTimeResultModel(Result.FAILED))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute(CarelevoPatchTimeZoneRequestModel(insulinAmount = 300)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            patchEvent.onNext(event)
        }.start()
    }
}
