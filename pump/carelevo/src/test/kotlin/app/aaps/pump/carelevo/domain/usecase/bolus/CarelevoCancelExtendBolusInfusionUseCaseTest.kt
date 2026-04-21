package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.CancelExtendBolusResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoBolusRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoCancelExtendBolusInfusionUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val bolusRepository: CarelevoBolusRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val bolusEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoCancelExtendBolusInfusionUseCase(
        patchObserver,
        bolusRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_cancel_succeeds() {
        whenever(patchObserver.bolusEvent).thenReturn(bolusEvent)
        whenever(bolusRepository.requestCancelExtendBolus()).thenAnswer {
            emitAsync(CancelExtendBolusResultModel(Result.SUCCESS, infusedAmount = 0.6))
            Single.just(RequestResult.Pending(true))
        }
        whenever(infusionInfoRepository.deleteExtendBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(
            CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = CarelevoBasalInfusionInfoDomainModel(
                    infusionId = "basal-1",
                    address = "AA:BB",
                    mode = 1,
                    segments = listOf(CarelevoBasalSegmentInfusionInfoDomainModel(startTime = 0, endTime = 1, speed = 1.0)),
                    isStop = false
                )
            )
        )
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(CarelevoPatchInfoDomainModel("AA:BB", DateTime.now(), DateTime.now(), mode = 1))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            bolusEvent.onNext(event)
        }.start()
    }
}
