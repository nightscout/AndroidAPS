package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal class CarelevoPatchForceDiscardUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository = mock()
    private val pumpSync: PumpSync = mock()

    private val sut = CarelevoPatchForceDiscardUseCase(
        patchInfoRepository = patchInfoRepository,
        infusionInfoRepository = infusionInfoRepository,
        userSettingInfoRepository = userSettingInfoRepository,
        pumpSync = pumpSync
    )

    /** The records of the discarded patch end with the discard (D9). */
    @Test
    fun execute_returns_success_when_cleanup_succeeds() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)

        val result = runBlocking { sut.execute() }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        verify(pumpSync).connectNewPump(true)
    }

    @Test
    fun execute_returns_error_when_user_setting_missing() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(null)

        val result = runBlocking { sut.execute() }

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    @Test
    fun execute_returns_error_when_patch_delete_fails() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(false)

        val result = runBlocking { sut.execute() }

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }

    /** A patch with its needle in and no infusion record is an activation to finish: never leave that behind. */
    @Test
    fun execute_deletes_the_patch_before_its_infusion_record() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)

        runBlocking { sut.execute() }

        inOrder(patchInfoRepository, infusionInfoRepository) {
            verify(patchInfoRepository).deletePatchInfo()
            verify(infusionInfoRepository).deleteInfusionInfo()
        }
    }

    @Test
    fun `the kept user setting no longer waits for a patch sync`() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)

        runBlocking { sut.execute() }

        val stored = argumentCaptor<CarelevoUserSettingInfoDomainModel>().also { verify(userSettingInfoRepository).updateUserSettingInfo(it.capture()) }.firstValue
        assertThat(stored.needLowInsulinNoticeAmountSyncPatch).isFalse()
        assertThat(stored.needMaxBasalSpeedSyncPatch).isFalse()
        assertThat(stored.needMaxBolusDoseSyncPatch).isFalse()
        assertThat(stored.maxBolusDose).isEqualTo(25.0)
    }

    @Test
    fun `a failed user setting write stops before anything is deleted`() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(false)

        val result = runBlocking { sut.execute() }

        assertThat((result as ResponseResult.Error).e.message).isEqualTo("update user setting info is failed")
        verify(patchInfoRepository, never()).deletePatchInfo()
        verify(infusionInfoRepository, never()).deleteInfusionInfo()
        verify(pumpSync, never()).connectNewPump(any())
    }

    @Test
    fun `a failed infusion delete is an error and ends no pump records`() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(sampleUserSetting())
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(false)

        val result = runBlocking { sut.execute() }

        assertThat((result as ResponseResult.Error).e.message).isEqualTo("delete infusion info is failed")
        verify(pumpSync, never()).connectNewPump(any())
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
