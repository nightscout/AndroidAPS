package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetAlarmClearResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class AlarmClearRequestUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val alarmRepository: CarelevoAlarmInfoRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = AlarmClearRequestUseCase(
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        alarmRepository = alarmRepository
    )

    @Test
    fun execute_returns_success_and_marks_acknowledged_for_alert() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_ALERT_LOW_BATTERY)
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetAlarmClear(any())).thenAnswer {
            emitAsync(SetAlarmClearResultModel(Result.SUCCESS, subId = 0, cause = request.alarmCause.code ?: 0))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(eq(request.alarmId), eq(true), any())).thenReturn(Completable.complete())

        val result = sut.execute(request).blockingGet()

        assertThat(result).isEqualTo(ResponseResult.Success(ResultSuccess))
        verify(patchRepository).requestSetAlarmClear(argThat { alarmType == 162 && causeId == (request.alarmCause.code ?: 0) })
        verify(alarmRepository).markAcknowledged(eq(request.alarmId), eq(true), any())
    }

    @Test
    fun execute_uses_notice_alarm_type_command_for_notice_alarm() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_NOTICE_LOW_INSULIN)
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetAlarmClear(any())).thenAnswer {
            emitAsync(SetAlarmClearResultModel(Result.SUCCESS, subId = 0, cause = request.alarmCause.code ?: 0))
            Single.just(RequestResult.Pending(true))
        }
        whenever(alarmRepository.markAcknowledged(eq(request.alarmId), eq(true), any())).thenReturn(Completable.complete())

        sut.execute(request).blockingGet()

        verify(patchRepository).requestSetAlarmClear(argThat { alarmType == 163 && causeId == (request.alarmCause.code ?: 0) })
    }

    @Test
    fun execute_returns_success_with_result_failed_when_patch_returns_failed() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_ALERT_LOW_BATTERY)
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetAlarmClear(any())).thenAnswer {
            emitAsync(SetAlarmClearResultModel(Result.FAILED, subId = 0, cause = request.alarmCause.code ?: 0))
            Single.just(RequestResult.Pending(true))
        }

        val result = sut.execute(request).blockingGet()

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
    fun execute_returns_error_for_unsupported_alarm_type() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_WARNING_LOW_INSULIN)

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_request_is_not_pending() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_ALERT_LOW_BATTERY)
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetAlarmClear(any())).thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_request_returns_failure() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_ALERT_LOW_BATTERY)
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetAlarmClear(any())).thenReturn(Single.just(RequestResult.Failure("failed")))

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_request_returns_error() {
        val request = alarmRequest(alarmCause = AlarmCause.ALARM_ALERT_LOW_BATTERY)
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetAlarmClear(any())).thenReturn(Single.just(RequestResult.Error(IllegalStateException("boom"))))

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun alarmRequest(alarmCause: AlarmCause): AlarmClearUseCaseRequest =
        AlarmClearUseCaseRequest(
            alarmId = "alarm-1",
            alarmType = alarmCause.alarmType,
            alarmCause = alarmCause
        )

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            Thread.sleep(delayMs)
            patchEvent.onNext(event)
        }.start()
    }
}
