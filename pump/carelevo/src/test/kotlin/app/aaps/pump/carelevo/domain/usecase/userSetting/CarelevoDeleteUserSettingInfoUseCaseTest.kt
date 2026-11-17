package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoDeleteUserSettingInfoUseCaseTest {

    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val sut = CarelevoDeleteUserSettingInfoUseCase(userSettingInfoRepository)

    @Test
    fun execute_returns_success_when_delete_succeeds() {
        whenever(userSettingInfoRepository.deleteUserSettingInfo()).thenReturn(true)

        val result = runBlocking { sut.execute() }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun `a failed delete is an error`() {
        whenever(userSettingInfoRepository.deleteUserSettingInfo()).thenReturn(false)

        val result = runBlocking { sut.execute() }

        assertThat((result as ResponseResult.Error).e.message).isEqualTo("delete user setting info is failed")
    }
}
