package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.DiscardPatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.result.ResultFailed
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class AlarmClearPatchDiscardUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val alarmRepository: CarelevoAlarmInfoRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = AlarmClearPatchDiscardUseCase(
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        alarmRepository = alarmRepository,
        patchInfoRepository = patchInfoRepository,
        userSettingInfoRepository = userSettingInfoRepository,
        infusionInfoRepository = infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_discard_and_cleanup_succeed() {
        val request = alarmRequest()
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(eq(request.alarmId), eq(true), any())).thenReturn(Completable.complete())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)

        val result = sut.execute(request).blockingGet()

        assertThat(result).isEqualTo(ResponseResult.Success(ResultSuccess))
        verify(alarmRepository).markAcknowledged(eq(request.alarmId), eq(true), any())
    }

    @Test
    fun execute_returns_success_with_result_failed_when_discard_result_failed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.FAILED))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute(alarmRequest()).blockingGet()

        assertThat(result).isEqualTo(ResponseResult.Success(ResultFailed))
        verify(alarmRepository, never()).markAcknowledged(any(), any(), any())
    }

    @Test
    fun execute_returns_error_when_request_type_is_invalid() {
        val invalidRequest = object : CarelevoUseCaseRequest {}

        val result = sut.execute(invalidRequest).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_discard_request_is_not_pending() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute(alarmRequest()).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_user_setting_missing() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(any(), any(), any())).thenReturn(Completable.complete())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(null)

        val result = sut.execute(alarmRequest()).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_update_user_setting_fails() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(any(), any(), any())).thenReturn(Completable.complete())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(false)

        val result = sut.execute(alarmRequest()).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_delete_infusion_info_fails() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(any(), any(), any())).thenReturn(Completable.complete())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(false)

        val result = sut.execute(alarmRequest()).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_delete_patch_info_fails() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestDiscardPatch()).thenAnswer {
            emitAsync(DiscardPatchResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(any(), any(), any())).thenReturn(Completable.complete())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(false)

        val result = sut.execute(alarmRequest()).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun alarmRequest(): AlarmClearUseCaseRequest =
        AlarmClearUseCaseRequest(
            alarmId = "alarm-1",
            alarmType = AlarmCause.ALARM_WARNING_PATCH_ERROR.alarmType,
            alarmCause = AlarmCause.ALARM_WARNING_PATCH_ERROR
        )

    private fun sampleUserSetting(): CarelevoUserSettingInfoDomainModel =
        CarelevoUserSettingInfoDomainModel(
            createdAt = DateTime.now().minusDays(1),
            updatedAt = DateTime.now().minusMinutes(10),
            lowInsulinNoticeAmount = 30,
            maxBasalSpeed = 15.0,
            maxBolusDose = 25.0,
            needLowInsulinNoticeAmountSyncPatch = true,
            needMaxBasalSpeedSyncPatch = true,
            needMaxBolusDoseSyncPatch = true
        )

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            Thread.sleep(delayMs)
            patchEvent.onNext(event)
        }.start()
    }
}
