package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoInfusionInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
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
 * Delegation tests for [CarelevoInfusionInfoDataSourceImpl] — a thin pass-through over
 * [CarelevoInfusionInfoDao]. No mapping happens here, so every method must simply forward its
 * argument and return the DAO's result verbatim (including the boolean success flags).
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoInfusionInfoDataSourceImplTest {

    @Mock lateinit var dao: CarelevoInfusionInfoDao

    private lateinit var sut: CarelevoInfusionInfoDataSourceImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoInfusionInfoDataSourceImpl(dao)
    }

    private fun basal() = CarelevoBasalInfusionInfoEntity(
        infusionId = "b-1", address = "AA", mode = 1, createdAt = created, updatedAt = updated,
        segments = emptyList(), isStop = false
    )

    private fun tempBasal() = CarelevoTempBasalInfusionInfoEntity(
        infusionId = "t-1", address = "AA", mode = 2, createdAt = created, updatedAt = updated
    )

    private fun imme() = CarelevoImmeBolusInfusionInfoEntity(
        infusionId = "i-1", address = "AA", mode = 3, createdAt = created, updatedAt = updated
    )

    private fun extend() = CarelevoExtendBolusInfusionInfoEntity(
        infusionId = "e-1", address = "AA", mode = 5, createdAt = created, updatedAt = updated
    )

    @Test
    fun `getInfusionInfo returns the dao stream unchanged`() {
        val stream = Observable.just(Optional.of(CarelevoInfusionInfoEntity()))
        whenever(dao.getInfusionInfo()).thenReturn(stream)

        assertThat(sut.getInfusionInfo()).isSameInstanceAs(stream)
        verify(dao).getInfusionInfo()
    }

    @Test
    fun `getInfusionInfoBySync forwards the dao entity`() {
        val entity = CarelevoInfusionInfoEntity(basalInfusionInfo = basal())
        whenever(dao.getInfusionInfoBySync()).thenReturn(entity)

        assertThat(sut.getInfusionInfoBySync()).isSameInstanceAs(entity)
        verify(dao).getInfusionInfoBySync()
    }

    @Test
    fun `getInfusionInfoBySync forwards a null`() {
        whenever(dao.getInfusionInfoBySync()).thenReturn(null)

        assertThat(sut.getInfusionInfoBySync()).isNull()
    }

    @Test
    fun `updateBasalInfusionInfo forwards and returns true`() {
        val info = basal()
        whenever(dao.updateBasalInfusionInfo(info)).thenReturn(true)

        assertThat(sut.updateBasalInfusionInfo(info)).isTrue()
        verify(dao).updateBasalInfusionInfo(eq(info))
    }

    @Test
    fun `updateBasalInfusionInfo forwards and returns false`() {
        val info = basal()
        whenever(dao.updateBasalInfusionInfo(info)).thenReturn(false)

        assertThat(sut.updateBasalInfusionInfo(info)).isFalse()
    }

    @Test
    fun `updateTempBasalInfusionInfo forwards and returns dao result`() {
        val info = tempBasal()
        whenever(dao.updateTempBasalInfusionInfo(info)).thenReturn(true)

        assertThat(sut.updateTempBasalInfusionInfo(info)).isTrue()
        verify(dao).updateTempBasalInfusionInfo(eq(info))
    }

    @Test
    fun `updateImmeBolusInfusionInfo forwards and returns dao result`() {
        val info = imme()
        whenever(dao.updateImmeBolusInfusionInfo(info)).thenReturn(true)

        assertThat(sut.updateImmeBolusInfusionInfo(info)).isTrue()
        verify(dao).updateImmeBolusInfusionInfo(eq(info))
    }

    @Test
    fun `updateExtendBolusInfusionInfo forwards and returns dao result`() {
        val info = extend()
        whenever(dao.updateExtendBolusInfusionInfo(info)).thenReturn(false)

        assertThat(sut.updateExtendBolusInfusionInfo(info)).isFalse()
        verify(dao).updateExtendBolusInfusionInfo(eq(info))
    }

    @Test
    fun `deleteBasalInfusionInfo delegates and returns dao result`() {
        whenever(dao.deleteBasalInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteBasalInfusionInfo()).isTrue()
        verify(dao).deleteBasalInfusionInfo()
    }

    @Test
    fun `deleteTempBasalInfusionInfo delegates and returns dao result`() {
        whenever(dao.deleteTempBasalInfusionInfo()).thenReturn(false)

        assertThat(sut.deleteTempBasalInfusionInfo()).isFalse()
        verify(dao).deleteTempBasalInfusionInfo()
    }

    @Test
    fun `deleteImmeBolusInfusionInfo delegates and returns dao result`() {
        whenever(dao.deleteImmeBolusInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteImmeBolusInfusionInfo()).isTrue()
        verify(dao).deleteImmeBolusInfusionInfo()
    }

    @Test
    fun `deleteExtendBolusInfusionInfo delegates and returns dao result`() {
        whenever(dao.deleteExtendBolusInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteExtendBolusInfusionInfo()).isTrue()
        verify(dao).deleteExtendBolusInfusionInfo()
    }

    @Test
    fun `deleteInfusionInfo delegates and returns dao result`() {
        whenever(dao.deleteInfusionInfo()).thenReturn(true)

        assertThat(sut.deleteInfusionInfo()).isTrue()
        verify(dao).deleteInfusionInfo()
    }
}
