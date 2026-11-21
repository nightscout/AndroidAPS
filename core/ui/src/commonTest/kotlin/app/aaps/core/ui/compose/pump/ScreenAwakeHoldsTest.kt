package app.aaps.core.ui.compose.pump

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenAwakeHoldsTest {

    private val applied = mutableListOf<Boolean>()
    private val holds = ScreenAwakeHolds { applied += it }

    @Test
    fun `one hold keeps the screen on until it is released`() {
        holds.hold()
        holds.release()

        assertEquals(listOf(true, false), applied)
    }

    /** The setting and a pump wizard both hold it: leaving the wizard must not turn the setting off. */
    @Test
    fun `the screen stays on while anybody still holds it`() {
        holds.hold()
        holds.hold()
        holds.release()

        assertEquals(listOf(true), applied)

        holds.release()

        assertEquals(listOf(true, false), applied)
    }

    @Test
    fun `a release without a hold does nothing and does not break the count`() {
        holds.release()
        holds.hold()

        assertEquals(listOf(true), applied)
    }
}
