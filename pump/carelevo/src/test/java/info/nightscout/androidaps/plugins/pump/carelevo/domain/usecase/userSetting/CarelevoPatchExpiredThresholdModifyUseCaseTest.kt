package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetThresholdNoticeResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoPatchExpiredThresholdModifyRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoPatchExpiredThresholdModifyUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoPatchExpiredThresholdModifyUseCase(patchObserver, patchRepository)

    @Test
    fun execute_returns_success_when_connected_and_patch_update_success() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetThresholdNotice(any())).thenAnswer {
            emitAsync(SetThresholdNoticeResultModel(type = 1, result = Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute(
            CarelevoPatchExpiredThresholdModifyRequestModel(
                patchState = PatchState.ConnectedBooted,
                patchExpiredThreshold = 72
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
