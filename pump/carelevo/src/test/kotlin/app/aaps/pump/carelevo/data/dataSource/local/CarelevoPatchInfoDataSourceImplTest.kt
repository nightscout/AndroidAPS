package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoPatchInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
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
 * Delegation tests for [CarelevoPatchInfoDataSourceImpl] — a thin pass-through over
 * [CarelevoPatchInfoDao]. Every method forwards to the DAO and returns its result verbatim.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchInfoDataSourceImplTest {

    @Mock lateinit var dao: CarelevoPatchInfoDao

    private lateinit var sut: CarelevoPatchInfoDataSourceImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoPatchInfoDataSourceImpl(dao)
    }

    private fun entity() = CarelevoPatchInfoEntity(
        address = "AA:BB:CC:DD:EE:FF", createdAt = created, updatedAt = updated,
        manufactureNumber = "CARELEVO-001", insulinRemain = 60.0
    )

    @Test
    fun `getPatchInfo returns the dao stream unchanged`() {
        val stream = Observable.just(Optional.of(entity()))
        whenever(dao.getPatchInfo()).thenReturn(stream)

        assertThat(sut.getPatchInfo()).isSameInstanceAs(stream)
        verify(dao).getPatchInfo()
    }

    @Test
    fun `getPatchInfoBySync forwards the dao entity`() {
        val entity = entity()
        whenever(dao.getPatchInfoBySync()).thenReturn(entity)

        assertThat(sut.getPatchInfoBySync()).isSameInstanceAs(entity)
        verify(dao).getPatchInfoBySync()
    }

    @Test
    fun `getPatchInfoBySync forwards a null`() {
        whenever(dao.getPatchInfoBySync()).thenReturn(null)

        assertThat(sut.getPatchInfoBySync()).isNull()
    }

    @Test
    fun `updatePatchInfo forwards the entity and returns true`() {
        val entity = entity()
        whenever(dao.updatePatchInfo(entity)).thenReturn(true)

        assertThat(sut.updatePatchInfo(entity)).isTrue()
        verify(dao).updatePatchInfo(eq(entity))
    }

    @Test
    fun `updatePatchInfo forwards the entity and returns false`() {
        val entity = entity()
        whenever(dao.updatePatchInfo(entity)).thenReturn(false)

        assertThat(sut.updatePatchInfo(entity)).isFalse()
    }

    @Test
    fun `deletePatchInfo delegates and returns dao result`() {
        whenever(dao.deletePatchInfo()).thenReturn(true)

        assertThat(sut.deletePatchInfo()).isTrue()
        verify(dao).deletePatchInfo()
    }
}
