package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.SetInfusionThresholdResultModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.carelevo.domain.model.bt.Result

internal class CarelevoUpdateMaxBolusDoseUseCaseTest {

    private val aapsLogger: AAPSLogger = mock()
    private val patchObserver: CarelevoPatchObserver = mock()
    private val patchRepository: CarelevoPatchRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val patchEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoUpdateMaxBolusDoseUseCase(
        aapsLogger,
        patchObserver,
        patchRepository,
        infusionInfoRepository,
        userSettingInfoRepository
    )

    @Test
    fun execute_returns_success_when_connected_and_patch_update_success() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(patchObserver.patchEvent).thenReturn(patchEvent)
        whenever(patchRepository.requestSetThresholdMaxDose(any())).thenAnswer {
            emitAsync(SetInfusionThresholdResultModel(Result.SUCCESS, type = 1))
            Single.just(RequestResult.Pending(true))
        }
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)

        val result = sut.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = PatchState.ConnectedBooted,
                maxBolusDose = 15.0
            )
        ).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun sampleUserSetting(): CarelevoUserSettingInfoDomainModel =
        CarelevoUserSettingInfoDomainModel(
            createdAt = DateTime.now().minusDays(1),
            updatedAt = DateTime.now().minusMinutes(5),
            lowInsulinNoticeAmount = 30,
            maxBasalSpeed = 15.0,
            maxBolusDose = 10.0
        )

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            patchEvent.onNext(event)
        }.start()
    }
}
