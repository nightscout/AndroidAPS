package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoInfusionInfoMonitorUseCaseTest {

    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoInfusionInfoMonitorUseCase(infusionInfoRepository)

    @Test
    fun execute_maps_repository_values_to_success() {
        whenever(infusionInfoRepository.getInfusionInfo()).thenReturn(
            flowOf(CarelevoInfusionInfoDomainModel())
        )

        val result = runBlocking { sut.execute().first() }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun `no stored infusion is a success without data`() {
        whenever(infusionInfoRepository.getInfusionInfo()).thenReturn(flowOf(null))

        val result = runBlocking { sut.execute().first() }

        assertThat(result).isEqualTo(ResponseResult.Success(null))
    }

    @Test
    fun `every new infusion state is passed on in order`() {
        val first = CarelevoInfusionInfoDomainModel()
        val second = CarelevoInfusionInfoDomainModel(
            tempBasalInfusionInfo = CarelevoTempBasalInfusionInfoDomainModel(infusionId = "t", address = "AA:BB", mode = 2)
        )
        whenever(infusionInfoRepository.getInfusionInfo()).thenReturn(flowOf(first, second))

        val results = runBlocking { sut.execute().toList() }

        assertThat(results).containsExactly(ResponseResult.Success(first), ResponseResult.Success(second)).inOrder()
    }

    @Test
    fun `a repository that cannot give the stream is one error`() {
        val failure = IllegalStateException("no store")
        whenever(infusionInfoRepository.getInfusionInfo()).thenThrow(failure)

        val results = runBlocking { sut.execute().toList() }

        assertThat(results).containsExactly(ResponseResult.Error(failure))
    }
}
