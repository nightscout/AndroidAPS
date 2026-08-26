package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
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
 * Tests for [CarelevoInfusionInfoRepositoryImpl] — delegates to [CarelevoInfusionInfoDataSource] and
 * applies the entity <-> domain mappers. Covers the nullable/optional branches on the read paths and
 * the domain->entity mapping on the write paths.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoInfusionInfoRepositoryImplTest {

    @Mock lateinit var dataSource: CarelevoInfusionInfoDataSource

    private lateinit var sut: CarelevoInfusionInfoRepositoryImpl

    @BeforeEach
    fun setUp() {
        sut = CarelevoInfusionInfoRepositoryImpl(dataSource)
    }

    private fun basalDomain() = CarelevoBasalInfusionInfoDomainModel(
        infusionId = "b-1", address = "AA", mode = 1, segments = emptyList(), isStop = false
    )

    private fun tempBasalDomain() = CarelevoTempBasalInfusionInfoDomainModel(
        infusionId = "t-1", address = "AA", mode = 2, percent = 150
    )

    private fun immeDomain() = CarelevoImmeBolusInfusionInfoDomainModel(
        infusionId = "i-1", address = "AA", mode = 3, volume = 2.0
    )

    private fun extendDomain() = CarelevoExtendBolusInfusionInfoDomainModel(
        infusionId = "e-1", address = "AA", mode = 5, volume = 3.0
    )

    // region read paths

    @Test
    fun `getInfusionInfo maps a present entity to a present domain model`() {
        whenever(dataSource.getInfusionInfo())
            .thenReturn(Observable.just(Optional.of(CarelevoInfusionInfoEntity())))

        val result = sut.getInfusionInfo().blockingFirst()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get().basalInfusionInfo).isNull()
        verify(dataSource).getInfusionInfo()
    }

    @Test
    fun `getInfusionInfo maps an empty optional to an empty optional`() {
        whenever(dataSource.getInfusionInfo()).thenReturn(Observable.just(Optional.empty<CarelevoInfusionInfoEntity>()))

        assertThat(sut.getInfusionInfo().blockingFirst().isPresent).isFalse()
    }

    @Test
    fun `getInfusionInfoBySync maps a present entity to a domain model`() {
        whenever(dataSource.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoEntity())

        val result = sut.getInfusionInfoBySync()

        assertThat(result).isNotNull()
        assertThat(result!!.basalInfusionInfo).isNull()
        verify(dataSource).getInfusionInfoBySync()
    }

    @Test
    fun `getInfusionInfoBySync returns null when the data source has none`() {
        whenever(dataSource.getInfusionInfoBySync()).thenReturn(null)

        assertThat(sut.getInfusionInfoBySync()).isNull()
    }

    // endregion

    // region write paths

    @Test
    fun `updateBasalInfusionInfo maps to an entity and returns the data source result`() {
        val captor = argumentCaptor<CarelevoBasalInfusionInfoEntity>()
        whenever(dataSource.updateBasalInfusionInfo(captor.capture())).thenReturn(true)

        assertThat(sut.updateBasalInfusionInfo(basalDomain())).isTrue()
        assertThat(captor.firstValue.infusionId).isEqualTo("b-1")
        assertThat(captor.firstValue.mode).isEqualTo(1)
    }

    @Test
    fun `updateBasalInfusionInfo propagates a false result`() {
        whenever(dataSource.updateBasalInfusionInfo(any())).thenReturn(false)

        assertThat(sut.updateBasalInfusionInfo(basalDomain())).isFalse()
    }

    @Test
    fun `updateTempBasalInfusionInfo maps to an entity and returns the data source result`() {
        val captor = argumentCaptor<CarelevoTempBasalInfusionInfoEntity>()
        whenever(dataSource.updateTempBasalInfusionInfo(captor.capture())).thenReturn(true)

        assertThat(sut.updateTempBasalInfusionInfo(tempBasalDomain())).isTrue()
        assertThat(captor.firstValue.infusionId).isEqualTo("t-1")
        assertThat(captor.firstValue.percent).isEqualTo(150)
    }

    @Test
    fun `updateImmeBolusInfusionInfo maps to an entity and returns the data source result`() {
        val captor = argumentCaptor<CarelevoImmeBolusInfusionInfoEntity>()
        whenever(dataSource.updateImmeBolusInfusionInfo(captor.capture())).thenReturn(true)

        assertThat(sut.updateImmeBolusInfusionInfo(immeDomain())).isTrue()
        assertThat(captor.firstValue.infusionId).isEqualTo("i-1")
        assertThat(captor.firstValue.volume).isEqualTo(2.0)
    }

    @Test
    fun `updateExtendBolusInfusionInfo maps to an entity and returns the data source result`() {
        val captor = argumentCaptor<CarelevoExtendBolusInfusionInfoEntity>()
        whenever(dataSource.updateExtendBolusInfusionInfo(captor.capture())).thenReturn(false)

        assertThat(sut.updateExtendBolusInfusionInfo(extendDomain())).isFalse()
        assertThat(captor.firstValue.infusionId).isEqualTo("e-1")
        assertThat(captor.firstValue.volume).isEqualTo(3.0)
    }

    @Test
    fun `deleteBasalInfusionInfo delegates and returns data source result`() {
        whenever(dataSource.deleteBasalInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteBasalInfusionInfo()).isTrue()
        verify(dataSource).deleteBasalInfusionInfo()
    }

    @Test
    fun `deleteTempBasalInfusionInfo delegates and returns data source result`() {
        whenever(dataSource.deleteTempBasalInfusionInfo()).thenReturn(false)

        assertThat(sut.deleteTempBasalInfusionInfo()).isFalse()
        verify(dataSource).deleteTempBasalInfusionInfo()
    }

    @Test
    fun `deleteImmeBolusInfusionInfo delegates and returns data source result`() {
        whenever(dataSource.deleteImmeBolusInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteImmeBolusInfusionInfo()).isTrue()
        verify(dataSource).deleteImmeBolusInfusionInfo()
    }

    @Test
    fun `deleteExtendBolusInfusionInfo delegates and returns data source result`() {
        whenever(dataSource.deleteExtendBolusInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteExtendBolusInfusionInfo()).isTrue()
        verify(dataSource).deleteExtendBolusInfusionInfo()
    }

    @Test
    fun `deleteInfusionInfo delegates and returns data source result`() {
        whenever(dataSource.deleteInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteInfusionInfo()).isTrue()
        verify(dataSource).deleteInfusionInfo()
    }

    // endregion
}
