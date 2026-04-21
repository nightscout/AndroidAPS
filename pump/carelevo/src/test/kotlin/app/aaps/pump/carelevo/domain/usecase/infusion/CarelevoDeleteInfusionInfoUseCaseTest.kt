package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.infusion.model.CarelevoDeleteInfusionRequestModel
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoDeleteInfusionInfoUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoDeleteInfusionInfoUseCase(patchInfoRepository, infusionInfoRepository)

    @Test
    fun execute_returns_success_when_delete_and_patch_update_succeed() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(CarelevoPatchInfoDomainModel("AA:BB", DateTime.now(), DateTime.now(), mode = 1))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute(
            CarelevoDeleteInfusionRequestModel(
                isDeleteTempBasal = true,
                isDeleteImmeBolus = false,
                isDeleteExtendBolus = false
            )
        ).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }
}
