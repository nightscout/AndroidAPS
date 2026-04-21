package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.AlertAlarmSetResultModel
import app.aaps.pump.carelevo.domain.model.bt.AppAuthAckResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchInformationInquiryDetailModel
import app.aaps.pump.carelevo.domain.model.bt.PatchInformationInquiryModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.RetrieveAddressResultModel
import app.aaps.pump.carelevo.domain.model.bt.ThresholdSetResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoConnectNewPatchUseCaseTest {

    private val aapsLogger: AAPSLogger = mock()
    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val request = CarelevoConnectNewPatchRequestModel(
        volume = 300,
        expiry = 120,
        remains = 30,
        maxBasalSpeed = 15.0,
        maxVolume = 25.0,
        isBuzzOn = true
    )

    private val sut = CarelevoConnectNewPatchUseCase(
        aapsLogger = aapsLogger,
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        patchInfoRepository = patchInfoRepository
    )

    @Test
    fun execute_success_on_first_round_updates_patch_info() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestRetrieveMacAddress(any()))
            .thenAnswer {
                emitAsync(RetrieveAddressResultModel(address = "00A1B2C3D4E5F6A7B8C9D0E1", checkSum = "AB"))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchRepository.requestAppAuth(any()))
            .thenAnswer {
                emitAsync(AppAuthAckResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchRepository.requestSetTime(any()))
            .thenAnswer {
                emitAsync(PatchInformationInquiryModel(Result.SUCCESS, "EO12507099001"), delayMs = 10L)
                emitAsync(PatchInformationInquiryDetailModel(Result.SUCCESS, "T166", "2603051200", "6776514848"), delayMs = 20L)
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchRepository.requestSetAlertAlarmMode(any()))
            .thenAnswer {
                emitAsync(AlertAlarmSetResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchRepository.requestSetThreshold(any()))
            .thenAnswer {
                emitAsync(ThresholdSetResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        verify(patchRepository, times(1)).requestSetTime(any())
        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        assertThat(captor.firstValue.manufactureNumber).isEqualTo("EO12507099001")
        assertThat(captor.firstValue.firmwareVersion).isEqualTo("T166")
        assertThat(captor.firstValue.bootDateTime).isEqualTo("2603051200")
        assertThat(captor.firstValue.bootDateTimeUtcMillis).isNotNull()
    }

    @Test
    fun execute_retries_round_when_serial_is_empty() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestRetrieveMacAddress(any()))
            .thenAnswer {
                emitAsync(RetrieveAddressResultModel(address = "00A1B2C3D4E5F6A7B8C9D0E1", checkSum = "AB"))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchRepository.requestAppAuth(any()))
            .thenAnswer {
                emitAsync(AppAuthAckResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }

        val round = AtomicInteger(0)
        whenever(patchRepository.requestSetTime(any()))
            .thenAnswer {
                val current = round.incrementAndGet()
                if (current == 1) {
                    emitAsync(PatchInformationInquiryModel(Result.SUCCESS, ""), delayMs = 10L)
                    emitAsync(PatchInformationInquiryDetailModel(Result.SUCCESS, "T165", "2603051200", "6776514848"), delayMs = 20L)
                } else {
                    emitAsync(PatchInformationInquiryModel(Result.SUCCESS, "EO12507099001"), delayMs = 10L)
                    emitAsync(PatchInformationInquiryDetailModel(Result.SUCCESS, "T166", "2603051201", "6776514848"), delayMs = 20L)
                }
                Single.just(RequestResult.Pending(true))
            }

        whenever(patchRepository.requestSetAlertAlarmMode(any()))
            .thenAnswer {
                emitAsync(AlertAlarmSetResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchRepository.requestSetThreshold(any()))
            .thenAnswer {
                emitAsync(ThresholdSetResultModel(Result.SUCCESS))
                Single.just(RequestResult.Pending(true))
            }
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(request).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        verify(patchRepository, times(2)).requestSetTime(any())
        verify(patchInfoRepository).updatePatchInfo(any())
    }

    private fun emitAsync(
        event: PatchResultModel,
        delayMs: Long = 5L
    ) {
        Thread {
            Thread.sleep(delayMs)
            patchEvent.onNext(event)
        }.start()
    }
}
