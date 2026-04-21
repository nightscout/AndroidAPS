package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.DiscardPatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoPatchDiscardUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val patchEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoPatchDiscardUseCase(
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        patchInfoRepository = patchInfoRepository,
        infusionInfoRepository = infusionInfoRepository,
        userSettingInfoRepository = userSettingInfoRepository
    )

    @Test
    fun execute_returns_success_when_discard_and_cleanup_succeed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_discard_request_is_not_pending() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_discard_result_failed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.FAILED))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_user_setting_missing() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(null)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_delete_patch_info_fails() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(false)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun sampleUserSetting(): CarelevoUserSettingInfoDomainModel {
        return CarelevoUserSettingInfoDomainModel(
            createdAt = DateTime.now().minusDays(1),
            updatedAt = DateTime.now().minusMinutes(10),
            lowInsulinNoticeAmount = 30,
            maxBasalSpeed = 15.0,
            maxBolusDose = 25.0,
            needLowInsulinNoticeAmountSyncPatch = true,
            needMaxBasalSpeedSyncPatch = true,
            needMaxBolusDoseSyncPatch = true
        )
    }

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            patchEvent.onNext(event)
        }.start()
    }
}
