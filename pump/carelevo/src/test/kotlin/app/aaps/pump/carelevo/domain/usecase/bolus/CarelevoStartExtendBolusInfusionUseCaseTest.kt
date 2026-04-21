package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.SetBolusProgramResult
import app.aaps.pump.carelevo.domain.model.bt.StartExtendBolusResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoBolusRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.bolus.model.StartExtendBolusInfusionRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoStartExtendBolusInfusionUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val bolusRepository: CarelevoBolusRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val bolusEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoStartExtendBolusInfusionUseCase(
        patchObserver,
        bolusRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_start_succeeds() {
        whenever(patchObserver.bolusEvent).thenReturn(bolusEvent)
        whenever(bolusRepository.requestStartExtendBolus(any())).thenAnswer {
            emitAsync(StartExtendBolusResultModel(SetBolusProgramResult.SUCCESS, expectedTime = 120))
            Single.just(RequestResult.Pending(true))
        }
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(CarelevoPatchInfoDomainModel("AA:BB", DateTime.now(), DateTime.now(), mode = 1))
        whenever(infusionInfoRepository.updateExtendBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(StartExtendBolusInfusionRequestModel(volume = 1.0, minutes = 60)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            Thread.sleep(5)
            bolusEvent.onNext(event)
        }.start()
    }
}
