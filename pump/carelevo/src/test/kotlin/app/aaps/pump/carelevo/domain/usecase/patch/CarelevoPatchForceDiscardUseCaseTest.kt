package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoPatchForceDiscardUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()

    private val sut = CarelevoPatchForceDiscardUseCase(
        patchInfoRepository = patchInfoRepository,
        infusionInfoRepository = infusionInfoRepository,
        userSettingInfoRepository = userSettingInfoRepository
    )

    @Test
    fun execute_returns_success_when_cleanup_succeeds() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_user_setting_missing() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(null)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_patch_delete_fails() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(false)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    private fun sampleUserSetting(): CarelevoUserSettingInfoDomainModel {
        return CarelevoUserSettingInfoDomainModel(
            createdAt = Clock.System.now() - 1.days,
            updatedAt = Clock.System.now() - 10.minutes,
            lowInsulinNoticeAmount = 30,
            maxBasalSpeed = 15.0,
            maxBolusDose = 25.0,
            needLowInsulinNoticeAmountSyncPatch = true,
            needMaxBasalSpeedSyncPatch = true,
            needMaxBolusDoseSyncPatch = true
        )
    }
}
