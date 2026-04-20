package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoFinishImmeBolusInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()

    private val sut = CarelevoFinishImmeBolusInfusionUseCase(
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_cleanup_succeeds() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
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
}
