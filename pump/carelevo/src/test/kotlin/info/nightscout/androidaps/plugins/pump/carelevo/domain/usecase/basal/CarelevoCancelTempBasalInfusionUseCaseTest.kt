package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CancelTempBasalProgramResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoCancelTempBasalInfusionUseCaseTest {

    private val patchObserver: CarelevoPatchObserver = mock()
    private val basalRepository: CarelevoBasalRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val basalEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoCancelTempBasalInfusionUseCase(
        patchObserver,
        basalRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_cancel_and_cleanup_succeed() {
        whenever(patchObserver.basalEvent).thenReturn(basalEvent)
        whenever(basalRepository.requestCancelTempBasalProgram()).thenAnswer {
            emitAsync(CancelTempBasalProgramResultModel(Result.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(
            CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = sampleBasalInfusion()
            )
        )
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun samplePatchInfo(): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "AA:BB:CC:DD:EE:FF",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            mode = 1
        )

    private fun sampleBasalInfusion(): CarelevoBasalInfusionInfoDomainModel =
        CarelevoBasalInfusionInfoDomainModel(
            infusionId = "basal-1",
            address = "AA:BB:CC:DD:EE:FF",
            mode = 1,
            segments = listOf(
                CarelevoBasalSegmentInfusionInfoDomainModel(startTime = 0, endTime = 1, speed = 1.0)
            ),
            isStop = false
        )

    private fun emitAsync(event: PatchResultModel) {
        Thread {
            Thread.sleep(5)
            basalEvent.onNext(event)
        }.start()
    }
}
