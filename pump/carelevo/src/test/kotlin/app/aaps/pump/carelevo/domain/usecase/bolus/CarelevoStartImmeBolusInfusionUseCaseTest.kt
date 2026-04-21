package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.SetBolusProgramResult
import app.aaps.pump.carelevo.domain.model.bt.StartImmeBolusResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoBolusRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoStartImmeBolusInfusionUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val bolusRepository: CarelevoBolusRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val bolusEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoStartImmeBolusInfusionUseCase(
        patchObserver,
        bolusRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_all_steps_succeed() {
        whenever(patchObserver.bolusEvent).thenReturn(bolusEvent)
        whenever(bolusRepository.requestStartImmeBolus(any())).thenAnswer {
            emitAsync(StartImmeBolusResultModel(SetBolusProgramResult.SUCCESS, actionId = 1, expectedTime = 60, remains = 100.0))
            Single.just(RequestResult.Pending(true))
        }
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(infusionInfoRepository.updateImmeBolusInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(StartImmeBolusInfusionRequestModel(actionSeq = 1, volume = 1.0)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel("AA:BB:CC:DD:EE:FF", DateTime.now().minusHours(1), DateTime.now(), mode = 1)

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            bolusEvent.onNext(event)
        }.start()
    }
}
