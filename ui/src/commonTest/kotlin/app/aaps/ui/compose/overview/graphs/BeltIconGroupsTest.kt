package app.aaps.ui.compose.overview.graphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which belt icons are drawn and which join the one before, see [beltIconGroups]. */
class BeltIconGroupsTest {

    /** 2 px per minute and a 16 px icon: icons closer than 8 minutes overlap. */
    private val pxPerX = 2f
    private val iconWidthPx = 16f

    @Test
    fun `icons far apart are each drawn alone`() {
        assertEquals(listOf(0 to 1, 1 to 1), beltIconGroups(listOf(10.0, 30.0), pxPerX, iconWidthPx))
    }

    @Test
    fun `icons that would overlap become one icon with the number of events`() {
        assertEquals(listOf(0 to 3), beltIconGroups(listOf(10.0, 12.0, 17.0), pxPerX, iconWidthPx))
    }

    @Test
    fun `an icon is measured against the one drawn so a long row does not join into one`() {
        // 10 and 15 overlap, 20 is 10 minutes from the drawn 10 and stands alone
        assertEquals(listOf(0 to 2, 2 to 1), beltIconGroups(listOf(10.0, 15.0, 20.0), pxPerX, iconWidthPx))
    }

    @Test
    fun `zoomed in the same icons stand apart`() {
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1), beltIconGroups(listOf(10.0, 12.0, 17.0), pxPerX = 10f, iconWidthPx = iconWidthPx))
    }

    @Test
    fun `no events give no icons`() {
        assertTrue(beltIconGroups(emptyList(), pxPerX, iconWidthPx).isEmpty())
    }
}
