package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sizes of the markers on the IOB graph. They follow the rule of the BG graph ([tierOf]): against the
 * largest of their kind in the loaded range, measured from zero.
 */
class IobOverlaysTest {

    private val minTimestamp = 1_000_000_000L

    private fun minutes(count: Long) = minTimestamp + count * 60_000L

    private fun smb(atMinute: Long, amount: Double) = BolusGraphPoint(minutes(atMinute), amount, BolusType.SMB, isValid = true, label = "$amount U")

    private fun manual(atMinute: Long, amount: Double) = BolusGraphPoint(minutes(atMinute), amount, BolusType.NORMAL, isValid = true, label = "$amount U")

    private fun overlays(vararg boluses: BolusGraphPoint) =
        processIobOverlays(TreatmentGraphData(boluses.toList(), emptyList(), emptyList(), emptyList(), emptyList()), minTimestamp, minX = 0.0, maxX = 600.0)

    /** Measured from the smallest SMB, as before, these two were drawn as the smallest and the largest marker. */
    @Test
    fun `SMBs of about the same dose get the same size`() {
        val result = overlays(smb(10, 0.2), smb(20, 0.25))

        assertEquals(listOf(10.0, 20.0), result.largeSmbs.map { it.first })
        assertTrue(result.smallSmbs.isEmpty())
        assertTrue(result.mediumSmbs.isEmpty())
    }

    @Test
    fun `an SMB is sized against the largest SMB from zero`() {
        val result = overlays(smb(10, 0.1), smb(20, 0.5), smb(30, 0.9))

        assertEquals(listOf(10.0), result.smallSmbs.map { it.first })
        assertEquals(listOf(20.0), result.mediumSmbs.map { it.first })
        assertEquals(listOf(30.0), result.largeSmbs.map { it.first })
    }

    @Test
    fun `manual boluses are not counted with the SMBs and the largest one sets their scale`() {
        val result = overlays(smb(10, 0.3), manual(20, 1.0), manual(40, 6.0))

        assertEquals(listOf(10.0), result.largeSmbs.map { it.first })
        assertEquals(listOf(20.0 to 1.0, 40.0 to 6.0), result.normalBoluses)
        assertEquals(6.0, result.maxManualBolus)
        // The sizes the IOB graph draws them in, the same as on the BG graph
        assertEquals(MarkerTier.SMALL, tierOf(1.0, result.maxManualBolus))
        assertEquals(MarkerTier.LARGE, tierOf(6.0, result.maxManualBolus))
    }

    @Test
    fun `without a manual bolus there is no largest one`() {
        assertNull(overlays(smb(10, 0.3)).maxManualBolus)
    }

    @Test
    fun `tier against a known largest amount`() {
        assertEquals(MarkerTier.SMALL, tierOf(0.09, max = 0.3))
        // A third of the largest is already the middle size
        assertEquals(MarkerTier.MEDIUM, tierOf(0.1, max = 0.3))
        assertEquals(MarkerTier.LARGE, tierOf(0.3, max = 0.3))
        assertEquals(MarkerTier.MEDIUM, tierOf(1.0, max = null))
    }
}
