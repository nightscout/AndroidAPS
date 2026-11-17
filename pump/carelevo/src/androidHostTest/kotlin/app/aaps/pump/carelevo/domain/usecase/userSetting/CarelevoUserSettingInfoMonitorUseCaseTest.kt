package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoUserSettingInfoMonitorUseCaseTest {

    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val sut = CarelevoUserSettingInfoMonitorUseCase(userSettingInfoRepository)

    @Test
    fun execute_maps_repository_values_to_success() {
        whenever(userSettingInfoRepository.getUserSettingInfo()).thenReturn(
            flowOf(CarelevoUserSettingInfoDomainModel())
        )

        val result = runBlocking { sut.execute().first() }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun `no stored setting is a success without data`() {
        whenever(userSettingInfoRepository.getUserSettingInfo()).thenReturn(flowOf(null))

        val result = runBlocking { sut.execute().first() }

        assertThat(result).isEqualTo(ResponseResult.Success(null))
    }

    @Test
    fun `every new setting is passed on`() {
        val first = CarelevoUserSettingInfoDomainModel(maxBolusDose = 5.0)
        val second = CarelevoUserSettingInfoDomainModel(maxBolusDose = 7.0)
        whenever(userSettingInfoRepository.getUserSettingInfo()).thenReturn(flowOf(first, second))

        val results = runBlocking { sut.execute().toList() }

        assertThat(results).containsExactly(ResponseResult.Success(first), ResponseResult.Success(second)).inOrder()
    }

    @Test
    fun `a repository that cannot give the stream is one error`() {
        val failure = IllegalStateException("no store")
        whenever(userSettingInfoRepository.getUserSettingInfo()).thenThrow(failure)

        val results = runBlocking { sut.execute().toList() }

        assertThat(results).containsExactly(ResponseResult.Error(failure))
    }
}
