package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

internal class CarelevoUserSettingInfoMonitorUseCaseTest {

    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val sut = CarelevoUserSettingInfoMonitorUseCase(userSettingInfoRepository)

    @Test
    fun execute_maps_repository_values_to_success() {
        whenever(userSettingInfoRepository.getUserSettingInfo()).thenReturn(
            Observable.just(Optional.of(CarelevoUserSettingInfoDomainModel()))
        )

        val result = sut.execute().blockingFirst()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }
}
