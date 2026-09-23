package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoUserSettingInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

/**
 * Delegation tests for [CarelevoUserSettingInfoDataSourceImpl] — a thin pass-through over
 * [CarelevoUserSettingInfoDao]. Note the data-source method names differ from the DAO names
 * (`getUserSettingInfo` -> `getUserSetting`, etc.), so these tests also guard the correct wiring.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoUserSettingInfoDataSourceImplTest {

    @Mock lateinit var dao: CarelevoUserSettingInfoDao

    private lateinit var sut: CarelevoUserSettingInfoDataSourceImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoUserSettingInfoDataSourceImpl(dao)
    }

    private fun entity() = CarelevoUserSettingInfoEntity(
        createdAt = created, updatedAt = updated, lowInsulinNoticeAmount = 10,
        maxBasalSpeed = 3.0, maxBolusDose = 15.0
    )

    @Test
    fun `getUserSettingInfo returns the dao stream unchanged`() {
        val stream = Observable.just(Optional.of(entity()))
        whenever(dao.getUserSetting()).thenReturn(stream)

        assertThat(sut.getUserSettingInfo()).isSameInstanceAs(stream)
        verify(dao).getUserSetting()
    }

    @Test
    fun `getUserSettingInfoBySync forwards the dao entity`() {
        val entity = entity()
        whenever(dao.getUserSettingBySync()).thenReturn(entity)

        assertThat(sut.getUserSettingInfoBySync()).isSameInstanceAs(entity)
        verify(dao).getUserSettingBySync()
    }

    @Test
    fun `getUserSettingInfoBySync forwards a null`() {
        whenever(dao.getUserSettingBySync()).thenReturn(null)

        assertThat(sut.getUserSettingInfoBySync()).isNull()
    }

    @Test
    fun `updateUserSettingInfo forwards the entity and returns true`() {
        val entity = entity()
        whenever(dao.updateUserSetting(entity)).thenReturn(true)

        assertThat(sut.updateUserSettingInfo(entity)).isTrue()
        verify(dao).updateUserSetting(eq(entity))
    }

    @Test
    fun `updateUserSettingInfo forwards the entity and returns false`() {
        val entity = entity()
        whenever(dao.updateUserSetting(entity)).thenReturn(false)

        assertThat(sut.updateUserSettingInfo(entity)).isFalse()
    }

    @Test
    fun `deleteUserSettingInfo delegates and returns dao result`() {
        whenever(dao.deleteUserSetting()).thenReturn(true)

        assertThat(sut.deleteUserSettingInfo()).isTrue()
        verify(dao).deleteUserSetting()
    }
}
