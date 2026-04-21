package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.AppBuzzResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoPatchBuzzRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoPatchBuzzModifyUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoPatchBuzzModifyUseCase(patchObserver, patchRepository)

    @Test
    fun execute_returns_success_when_connected_and_patch_update_success() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetBuzzMode(any())).thenAnswer {
            emitAsync(AppBuzzResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute(
            CarelevoPatchBuzzRequestModel(
                patchState = PatchState.ConnectedBooted,
                settingsAlarmBuzz = true
            )
        ).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            Thread.sleep(5)
            patchEvent.onNext(event)
        }.start()
    }
}
