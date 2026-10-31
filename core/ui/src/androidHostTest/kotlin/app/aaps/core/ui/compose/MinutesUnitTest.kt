package app.aaps.core.ui.compose

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The shared inputs show "2 h 20 min" under a minutes field because `asDuration` defaults to
 * [isMinutesUnit] of the unit label. When that default was simply `false`, every minutes field
 * outside Preferences lost the hint and nothing failed - so the rule itself is pinned here.
 */
class MinutesUnitTest {

    @Test
    fun `the minutes unit is a duration`() {
        assertThat(CoreUiStrings.units_min.isMinutesUnit()).isTrue()
    }

    @Test
    fun `other units and no unit are not`() {
        val none: TextRef? = null
        assertThat(none.isMinutesUnit()).isFalse()
        assertThat(CoreUiStrings.units_percent.isMinutesUnit()).isFalse()
        // The text alone does not make it the minutes unit: only the shared reference does.
        assertThat(TextRef.Literal("min").isMinutesUnit()).isFalse()
    }
}
