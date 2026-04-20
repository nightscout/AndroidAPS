package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoDeleteUserSettingInfoUseCaseTest {

    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val sut = CarelevoDeleteUserSettingInfoUseCase(userSettingInfoRepository)

    @Test
    fun execute_returns_success_when_delete_succeeds() {
        whenever(userSettingInfoRepository.deleteUserSettingInfo()).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }
}
