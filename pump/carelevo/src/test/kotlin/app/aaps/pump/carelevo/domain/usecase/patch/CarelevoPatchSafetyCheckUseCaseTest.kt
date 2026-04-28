package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResult
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.type.SafetyProgress
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoPatchSafetyCheckUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val patchEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoPatchSafetyCheckUseCase(
        patchObserver = patchObserver,
        patchRepository = patchRepository,
        patchInfoRepository = patchInfoRepository
    )

    @Test
    fun execute_emits_progress_then_success_when_ack_received() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSafetyCheck()).thenAnswer {
            Thread {
                patchEvent.onNext(SafetyCheckResultModel(SafetyCheckResult.REP_REQUEST, volume = 300, durationSeconds = 1))
                patchEvent.onNext(SafetyCheckResultModel(SafetyCheckResult.SUCCESS, volume = 300, durationSeconds = 0))
            }.start()
            Single.just(RequestResult.Pending(true))
        }
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val events = sut.execute().toList().blockingGet()

        assertThat(events).hasSize(2)
        assertThat(events[0]).isInstanceOf(SafetyProgress.Progress::class.java)
        assertThat(events[1]).isInstanceOf(SafetyProgress.Success::class.java)
    }

    @Test
    fun execute_emits_error_when_request_not_pending() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSafetyCheck()).thenReturn(Single.just(RequestResult.Success(true)))

        val event = sut.execute().blockingFirst()

        assertThat(event).isInstanceOf(SafetyProgress.Error::class.java)
    }

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel {
        return CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now().minusMinutes(1)
        )
    }

    private fun emitAsync(event: PatchResultModel, delayMs: Long = 5L) {
        Thread {
            patchEvent.onNext(event)
        }.start()
    }
}
