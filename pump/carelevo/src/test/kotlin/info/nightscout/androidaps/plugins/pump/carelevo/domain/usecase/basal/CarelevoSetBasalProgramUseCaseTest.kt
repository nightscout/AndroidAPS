package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoSetBasalProgramUseCaseTest {

    private val aapsLogger: AAPSLogger = mock()
    private val patchObserver: CarelevoPatchObserver = mock()
    private val basalRepository: CarelevoBasalRepository = mock()
    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val basalEvent = PublishSubject.create<PatchResultModel>()

    private val sut = CarelevoSetBasalProgramUseCase(
        aapsLogger,
        patchObserver,
        basalRepository,
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_all_steps_succeed() {
        whenever(patchObserver.basalEvent).thenReturn(basalEvent)
        whenever(basalRepository.requestSetBasalProgramV2(any())).thenAnswer {
            emitAsync(SetBasalProgramResultModel(SetBasalProgramResult.SUCCESS))
            Single.just(RequestResult.Pending(true))
        }
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(samplePatchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.updateBasalInfusionInfo(any())).thenReturn(true)

        val result = sut.execute(SetBasalProgramRequestModel(sampleProfile())).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    private fun sampleProfile(): Profile {
        val profile: Profile = mock()
        whenever(profile.getBasalValues()).thenReturn(
            arrayOf(
                Profile.ProfileValue(0, 1.0),
                Profile.ProfileValue(3600, 1.0)
            )
        )
        return profile
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
            Thread.sleep(5)
            basalEvent.onNext(event)
        }.start()
    }
}
