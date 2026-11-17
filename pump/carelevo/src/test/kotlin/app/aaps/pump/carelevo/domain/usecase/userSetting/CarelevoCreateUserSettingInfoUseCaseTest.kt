package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

    @Test
    fun `it stores the three values of the request`() {
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)

        sut.execute(CarelevoUserSettingInfoRequestModel(lowInsulinNoticeAmount = 30, maxBasalSpeed = 15.0, maxBolusDose = 10.0)).blockingGet()

        val stored = argumentCaptor<CarelevoUserSettingInfoDomainModel>().also { verify(userSettingInfoRepository).updateUserSettingInfo(it.capture()) }.firstValue
        assertThat(stored.lowInsulinNoticeAmount).isEqualTo(30)
        assertThat(stored.maxBasalSpeed).isEqualTo(15.0)
        assertThat(stored.maxBolusDose).isEqualTo(10.0)
    }

    @Test
    fun `a failed write is an error`() {
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(false)

        val result = sut.execute(CarelevoUserSettingInfoRequestModel(lowInsulinNoticeAmount = 30, maxBasalSpeed = 15.0, maxBolusDose = 10.0)).blockingGet()

        assertThat((result as ResponseResult.Error).e.message).isEqualTo("create user setting info is failed")
    }

    @Test
    fun `a request of another type is an error and writes nothing`() {
        val result = sut.execute(object : CarelevoUseCaseRequest {}).blockingGet()

        assertThat((result as ResponseResult.Error).e.message).isEqualTo("request is not CarelevoUserSettingInfoRequestModel")
        verify(userSettingInfoRepository, never()).updateUserSettingInfo(any())
    }
}
