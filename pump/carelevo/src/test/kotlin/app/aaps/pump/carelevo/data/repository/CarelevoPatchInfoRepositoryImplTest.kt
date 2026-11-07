package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
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
 * Tests for [CarelevoPatchInfoRepositoryImpl] — delegates to [CarelevoPatchInfoDataSource] and
 * applies the entity <-> domain mappers. Covers the present/empty branches on the read paths and the
 * domain->entity mapping on the update path.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchInfoRepositoryImplTest {

    @Mock lateinit var dataSource: CarelevoPatchInfoDataSource

    private lateinit var sut: CarelevoPatchInfoRepositoryImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoPatchInfoRepositoryImpl(dataSource)
    }

    private fun entity() = CarelevoPatchInfoEntity(
        address = "AA:BB:CC:DD:EE:FF", createdAt = created, updatedAt = updated,
        manufactureNumber = "CARELEVO-001", insulinRemain = 55.0, mode = 1
    )

    private fun domain() = CarelevoPatchInfoDomainModel(
        address = "AA:BB:CC:DD:EE:FF", manufactureNumber = "CARELEVO-001", insulinRemain = 55.0, mode = 1
    )

    @Test
    fun `getPatchInfo maps a present entity to a present domain model`() {
        whenever(dataSource.getPatchInfo()).thenReturn(Observable.just(Optional.of(entity())))

        val result = sut.getPatchInfo().blockingFirst()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get().address).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(result.get().manufactureNumber).isEqualTo("CARELEVO-001")
        assertThat(result.get().insulinRemain).isEqualTo(55.0)
        verify(dataSource).getPatchInfo()
    }

    @Test
    fun `getPatchInfo maps an empty optional to an empty optional`() {
        whenever(dataSource.getPatchInfo()).thenReturn(Observable.just(Optional.empty<CarelevoPatchInfoEntity>()))

        assertThat(sut.getPatchInfo().blockingFirst().isPresent).isFalse()
    }

    @Test
    fun `getPatchInfoBySync maps a present entity to a domain model`() {
        whenever(dataSource.getPatchInfoBySync()).thenReturn(entity())

        val result = sut.getPatchInfoBySync()

        assertThat(result).isNotNull()
        assertThat(result!!.address).isEqualTo("AA:BB:CC:DD:EE:FF")
        verify(dataSource).getPatchInfoBySync()
    }

    @Test
    fun `getPatchInfoBySync returns null when the data source has none`() {
        whenever(dataSource.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.getPatchInfoBySync()).isNull()
    }

    @Test
    fun `updatePatchInfo maps to an entity and returns the data source result`() {
        val captor = argumentCaptor<CarelevoPatchInfoEntity>()
        whenever(dataSource.updatePatchInfo(captor.capture())).thenReturn(true)

        assertThat(sut.updatePatchInfo(domain())).isTrue()
        assertThat(captor.firstValue.address).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(captor.firstValue.manufactureNumber).isEqualTo("CARELEVO-001")
    }

    @Test
    fun `updatePatchInfo propagates a false result`() {
        whenever(dataSource.updatePatchInfo(any())).thenReturn(false)

        assertThat(sut.updatePatchInfo(domain())).isFalse()
    }

    @Test
    fun `deletePatchInfo delegates and returns data source result`() {
        whenever(dataSource.deletePatchInfo()).thenReturn(true)

        assertThat(sut.deletePatchInfo()).isTrue()
        verify(dataSource).deletePatchInfo()
    }
}
