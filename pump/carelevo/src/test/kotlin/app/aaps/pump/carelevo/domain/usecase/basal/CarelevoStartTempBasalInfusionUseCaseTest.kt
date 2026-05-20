package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramResult
import app.aaps.pump.carelevo.domain.model.bt.StartTempBasalProgramResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoBasalRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.ReplaySubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoStartTempBasalInfusionUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val basalRepository: CarelevoBasalRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val basalEvent = ReplaySubject.create<PatchResultModel>()

    private val sut = CarelevoStartTempBasalInfusionUseCase(
        patchObserver,
        basalRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_in_unit_mode() {
        whenever(patchObserver.basalEvent).thenReturn(basalEvent)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(basalRepository.requestStartTempBasalProgramByUnit(any())).thenAnswer {
            emitAsync(StartTempBasalProgramResultModel(SetBasalProgramResult.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(infusionInfoRepository.updateTempBasalInfusionInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(
            StartTempBasalInfusionRequestModel(
                isUnit = true,
                speed = 1.0,
                minutes = 30
            )
        ).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_unit_speed_missing() {
        val result = sut.execute(
            StartTempBasalInfusionRequestModel(
                isUnit = true,
                speed = null,
                minutes = 30
            )
        ).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "AA:BB:CC:DD:EE:FF",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            mode = 1
        )

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            basalEvent.onNext(event)
        }.start()
    }
}
