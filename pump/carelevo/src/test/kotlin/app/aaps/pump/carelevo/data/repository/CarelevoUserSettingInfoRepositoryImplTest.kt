package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

/**
 * Tests for [CarelevoUserSettingInfoRepositoryImpl] — delegates to
 * [CarelevoUserSettingInfoDataSource] and applies the entity <-> domain mappers. Covers the
 * present/null branches on the read paths and the domain->entity mapping on the update path.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoUserSettingInfoRepositoryImplTest {

    @Mock lateinit var dataSource: CarelevoUserSettingInfoDataSource

    private lateinit var sut: CarelevoUserSettingInfoRepositoryImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoUserSettingInfoRepositoryImpl(dataSource)
    }

    private fun entity() = CarelevoUserSettingInfoEntity(
        createdAt = created, updatedAt = updated, lowInsulinNoticeAmount = 10,
        maxBasalSpeed = 3.0, maxBolusDose = 15.0, needMaxBolusDoseSyncPatch = true
    )

    private fun domain() = CarelevoUserSettingInfoDomainModel(
        lowInsulinNoticeAmount = 10, maxBasalSpeed = 3.0, maxBolusDose = 15.0, needMaxBolusDoseSyncPatch = true
    )

    @Test
    fun `getUserSettingInfo maps a present entity to a present domain model`() {
        whenever(dataSource.getUserSettingInfo()).thenReturn(Observable.just(Optional.of(entity())))

        val result = sut.getUserSettingInfo().blockingFirst()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get().lowInsulinNoticeAmount).isEqualTo(10)
        assertThat(result.get().maxBasalSpeed).isEqualTo(3.0)
        assertThat(result.get().maxBolusDose).isEqualTo(15.0)
        assertThat(result.get().needMaxBolusDoseSyncPatch).isTrue()
        verify(dataSource).getUserSettingInfo()
    }

    @Test
    fun `getUserSettingInfo maps an empty optional to an empty optional`() {
        whenever(dataSource.getUserSettingInfo()).thenReturn(Observable.just(Optional.empty<CarelevoUserSettingInfoEntity>()))

        assertThat(sut.getUserSettingInfo().blockingFirst().isPresent).isFalse()
    }

    @Test
    fun `getUserSettingInfoBySync maps a present entity to a domain model`() {
        whenever(dataSource.getUserSettingInfoBySync()).thenReturn(entity())

        val result = sut.getUserSettingInfoBySync()

        assertThat(result).isNotNull()
        assertThat(result!!.lowInsulinNoticeAmount).isEqualTo(10)
        verify(dataSource).getUserSettingInfoBySync()
    }

    @Test
    fun `getUserSettingInfoBySync returns null when the data source has none`() {
        whenever(dataSource.getUserSettingInfoBySync()).thenReturn(null)

        assertThat(sut.getUserSettingInfoBySync()).isNull()
    }

    @Test
    fun `updateUserSettingInfo maps to an entity and returns the data source result`() {
        val captor = argumentCaptor<CarelevoUserSettingInfoEntity>()
        whenever(dataSource.updateUserSettingInfo(captor.capture())).thenReturn(true)

        assertThat(sut.updateUserSettingInfo(domain())).isTrue()
        assertThat(captor.firstValue.lowInsulinNoticeAmount).isEqualTo(10)
        assertThat(captor.firstValue.maxBolusDose).isEqualTo(15.0)
        assertThat(captor.firstValue.needMaxBolusDoseSyncPatch).isTrue()
    }

    @Test
    fun `updateUserSettingInfo propagates a false result`() {
        whenever(dataSource.updateUserSettingInfo(any())).thenReturn(false)

        assertThat(sut.updateUserSettingInfo(domain())).isFalse()
    }

    @Test
    fun `deleteUserSettingInfo delegates and returns data source result`() {
        whenever(dataSource.deleteUserSettingInfo()).thenReturn(true)

        assertThat(sut.deleteUserSettingInfo()).isTrue()
        verify(dataSource).deleteUserSettingInfo()
    }
}
