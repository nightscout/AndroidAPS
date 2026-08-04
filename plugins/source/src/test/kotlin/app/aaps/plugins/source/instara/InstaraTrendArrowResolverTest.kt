package app.aaps.plugins.source.instara

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class InstaraTrendArrowResolverTest : TestBase() {

    @Mock lateinit var persistenceLayer: PersistenceLayer

    private fun gv(valueMgdl: Double) = GV(
        timestamp = 0L, value = valueMgdl, raw = null, noise = null,
        trendArrow = TrendArrow.NONE, sourceSensor = SourceSensor.INSTARA
    )

    @Test
    fun `valid non-NONE arrow from instara is used directly`() = runTest {
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "SingleUp", 100.0, 1234567890123L)
        assertThat(arrow).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `NONE with non-positive previous id falls back to flat`() = runTest {
        // currentSgvId 1 -> prevId 0 -> cannot look back
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 100.0, 1L)
        assertThat(arrow).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `NONE with missing previous value falls back to flat`() = runTest {
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(null)
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 100.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `rising fast is single up`() = runTest {
        // X = (100 - 90) / 18 = 0.56 > 0.44
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(gv(90.0))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 100.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `rising moderate is forty five up`() = runTest {
        // X = (100 - 95) / 18 = 0.28 in [0.22, 0.44]
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(gv(95.0))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 100.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.FORTY_FIVE_UP)
    }

    @Test
    fun `falling fast is single down`() = runTest {
        // X = (90 - 100) / 18 = -0.56 < -0.44
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(gv(100.0))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 90.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.SINGLE_DOWN)
    }

    @Test
    fun `falling moderate is forty five down`() = runTest {
        // X = (95 - 100) / 18 = -0.28 in [-0.44, -0.22]
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(gv(100.0))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 95.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
    }

    @Test
    fun `stable is flat`() = runTest {
        // X = 0
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(gv(100.0))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 100.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `db exception falls back to flat`() = runTest {
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenThrow(RuntimeException("db"))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "NONE", 100.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `invalid direction is treated as none and computed from db`() = runTest {
        // "garbage" -> fromString -> NONE -> compute from previous; prev 90 -> single up
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(gv(90.0))
        val arrow = InstaraTrendArrowResolver.resolve(persistenceLayer, "garbage", 100.0, 100L)
        assertThat(arrow).isEqualTo(TrendArrow.SINGLE_UP)
    }
}
