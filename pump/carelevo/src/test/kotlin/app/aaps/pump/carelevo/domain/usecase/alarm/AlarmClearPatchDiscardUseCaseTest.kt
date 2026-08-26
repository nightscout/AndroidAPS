package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Covers [AlarmClearPatchDiscardUseCase.persistAlarmDiscarded]: the happy path plus every
 * failure point that must abort the discard bookkeeping and report false.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AlarmClearPatchDiscardUseCaseTest {

    @Mock lateinit var alarmRepository: CarelevoAlarmInfoRepository
    @Mock lateinit var patchInfoRepository: CarelevoPatchInfoRepository
    @Mock lateinit var userSettingInfoRepository: CarelevoUserSettingInfoRepository
    @Mock lateinit var infusionInfoRepository: CarelevoInfusionInfoRepository

    private lateinit var sut: AlarmClearPatchDiscardUseCase

    private val storedUserSetting = CarelevoUserSettingInfoDomainModel(
        createdAt = DateTime.parse("2026-03-01T08:00:00Z"),
        updatedAt = DateTime.parse("2026-03-02T08:00:00Z"),
        lowInsulinNoticeAmount = 20,
        maxBasalSpeed = 2.5,
        maxBolusDose = 10.0,
        needLowInsulinNoticeAmountSyncPatch = true,
        needMaxBasalSpeedSyncPatch = true,
        needMaxBolusDoseSyncPatch = true
    )

    @BeforeEach
    fun setUp() {
        sut = AlarmClearPatchDiscardUseCase(
            alarmRepository = alarmRepository,
            patchInfoRepository = patchInfoRepository,
            userSettingInfoRepository = userSettingInfoRepository,
            infusionInfoRepository = infusionInfoRepository
        )
        givenAllWritesSucceed()
    }

    private fun givenAllWritesSucceed() {
        whenever(alarmRepository.removeAlarm(any())).thenReturn(Completable.complete())
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(storedUserSetting)
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(true)
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(true)
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(true)
    }

    // ---------- happy path ----------

    @Test
    fun persistAlarmDiscarded_returns_true_when_all_writes_succeed() {
        assertThat(sut.persistAlarmDiscarded("alarm-1")).isTrue()
    }

    @Test
    fun persistAlarmDiscarded_acks_alarm_then_clears_settings_then_deletes_records_in_order() {
        sut.persistAlarmDiscarded("alarm-1")

        inOrder(alarmRepository, userSettingInfoRepository, infusionInfoRepository, patchInfoRepository) {
            verify(alarmRepository).removeAlarm(eq("alarm-1"))
            verify(userSettingInfoRepository).updateUserSettingInfo(any())
            verify(infusionInfoRepository).deleteInfusionInfo()
            verify(patchInfoRepository).deletePatchInfo()
        }
    }

    @Test
    fun persistAlarmDiscarded_clears_all_three_patch_sync_flags() {
        sut.persistAlarmDiscarded("alarm-1")

        val captor = argumentCaptor<CarelevoUserSettingInfoDomainModel>()
        verify(userSettingInfoRepository).updateUserSettingInfo(captor.capture())

        with(captor.firstValue) {
            assertThat(needMaxBolusDoseSyncPatch).isFalse()
            assertThat(needMaxBasalSpeedSyncPatch).isFalse()
            assertThat(needLowInsulinNoticeAmountSyncPatch).isFalse()
        }
    }

    @Test
    fun persistAlarmDiscarded_preserves_user_setting_values_and_bumps_updatedAt() {
        val before = DateTime.now()

        sut.persistAlarmDiscarded("alarm-1")

        val captor = argumentCaptor<CarelevoUserSettingInfoDomainModel>()
        verify(userSettingInfoRepository).updateUserSettingInfo(captor.capture())

        with(captor.firstValue) {
            // The user's configured limits survive a discard; only the sync flags reset.
            assertThat(lowInsulinNoticeAmount).isEqualTo(20)
            assertThat(maxBasalSpeed).isEqualTo(2.5)
            assertThat(maxBolusDose).isEqualTo(10.0)
            assertThat(createdAt).isEqualTo(storedUserSetting.createdAt)
            assertThat(updatedAt.isBefore(before)).isFalse()
        }
    }

    // ---------- failure paths ----------

    @Test
    fun persistAlarmDiscarded_returns_false_when_alarm_removal_errors() {
        whenever(alarmRepository.removeAlarm(any())).thenReturn(Completable.error(RuntimeException("db down")))

        assertThat(sut.persistAlarmDiscarded("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmDiscarded_skips_all_bookkeeping_when_alarm_removal_errors() {
        whenever(alarmRepository.removeAlarm(any())).thenReturn(Completable.error(RuntimeException("db down")))

        sut.persistAlarmDiscarded("alarm-1")

        verify(userSettingInfoRepository, never()).updateUserSettingInfo(any())
        verify(infusionInfoRepository, never()).deleteInfusionInfo()
        verify(patchInfoRepository, never()).deletePatchInfo()
    }

    @Test
    fun persistAlarmDiscarded_returns_false_when_no_user_setting_record_exists() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(null)

        assertThat(sut.persistAlarmDiscarded("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmDiscarded_does_not_delete_records_when_no_user_setting_record_exists() {
        whenever(userSettingInfoRepository.getUserSettingInfoBySync()).thenReturn(null)

        sut.persistAlarmDiscarded("alarm-1")

        verify(userSettingInfoRepository, never()).updateUserSettingInfo(any())
        verify(infusionInfoRepository, never()).deleteInfusionInfo()
        verify(patchInfoRepository, never()).deletePatchInfo()
    }

    @Test
    fun persistAlarmDiscarded_returns_false_when_user_setting_update_fails() {
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(false)

        assertThat(sut.persistAlarmDiscarded("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmDiscarded_does_not_delete_records_when_user_setting_update_fails() {
        whenever(userSettingInfoRepository.updateUserSettingInfo(any())).thenReturn(false)

        sut.persistAlarmDiscarded("alarm-1")

        verify(infusionInfoRepository, never()).deleteInfusionInfo()
        verify(patchInfoRepository, never()).deletePatchInfo()
    }

    @Test
    fun persistAlarmDiscarded_returns_false_when_infusion_delete_fails() {
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(false)

        assertThat(sut.persistAlarmDiscarded("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmDiscarded_does_not_delete_patch_when_infusion_delete_fails() {
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenReturn(false)

        sut.persistAlarmDiscarded("alarm-1")

        verify(patchInfoRepository, never()).deletePatchInfo()
    }

    @Test
    fun persistAlarmDiscarded_returns_false_when_patch_delete_fails() {
        whenever(patchInfoRepository.deletePatchInfo()).thenReturn(false)

        assertThat(sut.persistAlarmDiscarded("alarm-1")).isFalse()

        // The alarm ack and the earlier writes already ran: the failure is reported, not rolled back.
        verify(alarmRepository).removeAlarm(eq("alarm-1"))
        verify(infusionInfoRepository).deleteInfusionInfo()
    }

    @Test
    fun persistAlarmDiscarded_returns_false_when_a_repository_throws() {
        whenever(infusionInfoRepository.deleteInfusionInfo()).thenThrow(IllegalStateException("boom"))

        assertThat(sut.persistAlarmDiscarded("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmDiscarded_acks_the_requested_alarm_id() {
        sut.persistAlarmDiscarded("alarm-42")

        verify(alarmRepository).removeAlarm(eq("alarm-42"))
    }
}
