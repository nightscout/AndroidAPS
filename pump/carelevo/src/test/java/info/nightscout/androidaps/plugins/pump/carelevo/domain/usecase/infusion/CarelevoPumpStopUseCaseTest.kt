package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StopPumpResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.model.CarelevoPumpStopRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoPumpStopUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val patchEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoPumpStopUseCase(
        patchObserver,
        patchRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_stop_and_updates_succeed() {
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestStopPump(any())).thenAnswer {
            emitAsync(StopPumpResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
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
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(CarelevoPatchInfoDomainModel("AA:BB", DateTime.now(), DateTime.now(), mode = 1))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(CarelevoPumpStopRequestModel(30)).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            Thread.sleep(5)
            patchEvent.onNext(event)
        }.start()
    }
}
