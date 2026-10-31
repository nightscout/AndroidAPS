package app.aaps.core.ui.compose

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule behind the tablet text size, [typographyScaleFor].
 *
 * Most of these go over every size in a range instead of a list of devices: the next new shape of
 * screen is somewhere in that range, so it is already covered.
 */
class TypographyScaleTest {

    @Test
    fun `a phone keeps the normal text size`() {
        for (short in 320 until TABLET_MIN_SW_DP step 7) {
            for (long in short..2400 step 13) {
                assertEquals(1f, typographyScaleFor(short, long), "$short x $long")
            }
        }
    }

    @Test
    fun `a tablet with room for it gets the full tablet text size`() {
        // 16:10 and wider tablets, and the big 4:3 ones: the size the tablet layout was drawn for
        assertEquals(1.5f, typographyScaleFor(800, 1280))
        assertEquals(1.5f, typographyScaleFor(900, 1440))
        assertEquals(1.5f, typographyScaleFor(1024, 1366))
    }

    @Test
    fun `a folding phone opened up gets less than the full tablet size`() {
        // Issue #5355: each landscape column is 466 dp, which at 1.5 was narrower than any phone column
        assertEquals(1.25f, typographyScaleFor(704, 932))
    }

    @Test
    fun `the text never grows so much that a column is narrower than on a phone`() {
        for (short in TABLET_MIN_SW_DP..1600 step 3) {
            for (long in short..2800 step 11) {
                val scale = typographyScaleFor(short, long)
                assertTrue(scale in 1f..1.5f, "$short x $long gives $scale")
                if (scale > 1f) {
                    val room = min(short, long / 2)
                    assertTrue(room / scale >= PHONE_COLUMN_MIN_WIDTH_DP, "$short x $long: $room dp per column at $scale")
                }
            }
        }
    }

    @Test
    fun `more room never gives smaller text`() {
        for (short in TABLET_MIN_SW_DP..1400 step 5) {
            for (long in short..2400 step 17) {
                val scale = typographyScaleFor(short, long)
                assertTrue(typographyScaleFor(short + 5, maxOf(long, short + 5)) >= scale, "wider short side, $short x $long")
                assertTrue(typographyScaleFor(short, long + 17) >= scale, "longer long side, $short x $long")
            }
        }
    }
}
