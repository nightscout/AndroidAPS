package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import io.reactivex.rxjava3.core.Observable
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

internal class CarelevoInfusionInfoMonitorUseCaseTest {

    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoInfusionInfoMonitorUseCase(infusionInfoRepository)

    @Test
    fun execute_maps_repository_values_to_success() {
        whenever(infusionInfoRepository.getInfusionInfo()).thenReturn(
            Observable.just(Optional.of(CarelevoInfusionInfoDomainModel()))
        )

        val result = sut.execute().blockingFirst()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }
}
