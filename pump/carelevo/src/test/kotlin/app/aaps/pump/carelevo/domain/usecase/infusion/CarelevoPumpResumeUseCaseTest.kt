package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.ResumePumpResultModel
import app.aaps.pump.carelevo.domain.model.bt.StopPumpResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
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

internal class CarelevoPumpResumeUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoPumpResumeUseCase(
        patchObserver,
        patchRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_resume_and_updates_succeed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestResumePump(any())).thenAnswer {
            emitAsync(ResumePumpResultModel(StopPumpResult.BY_REQ, InfusionModeResult.BASAL, 0))
            Single.just(RequestResult.Pending(true))
        }
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(
            CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = CarelevoBasalInfusionInfoDomainModel(
                    infusionId = "basal-1",
                    address = "AA:BB",
                    mode = 0,
                    segments = listOf(CarelevoBasalSegmentInfusionInfoDomainModel(startTime = 0, endTime = 1, speed = 1.0)),
                    isStop = true
                )
            )
        )
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(CarelevoPatchInfoDomainModel("AA:BB", DateTime.now(), DateTime.now(), mode = 0))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            Thread.sleep(5)
            patchEvent.onNext(event)
        }.start()
    }
}
