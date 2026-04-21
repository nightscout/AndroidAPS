package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoCreateUserSettingInfoUseCaseTest {

    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val sut = CarelevoCreateUserSettingInfoUseCase(userSettingInfoRepository)

    @Test
    fun execute_returns_success_when_create_succeeds() {
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)

        val result = sut.execute(
            CarelevoUserSettingInfoRequestModel(
                lowInsulinNoticeAmount = 30,
                maxBasalSpeed = 15.0,
                maxBolusDose = 10.0
            )
        ).blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }
}
