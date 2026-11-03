package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.Bidi

/** A graph inside [LeftToRight] keeps its layout in a right-to-left language, and what is outside stays mirrored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeftToRightTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onlyTheContentIsLeftToRight() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(200.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                        Box(Modifier.size(10.dp).align(Alignment.TopStart).testTag("outside"))
                    }
                    LeftToRight {
                        Box(Modifier.fillMaxWidth()) {
                            Box(Modifier.size(10.dp).align(Alignment.TopStart).testTag("inside"))
                        }
                    }
                }
            }
        }

        val inside = compose.onNodeWithTag("inside").fetchSemanticsNode().boundsInRoot
        val outside = compose.onNodeWithTag("outside").fetchSemanticsNode().boundsInRoot
        assertThat(inside.left).isLessThan(outside.left)
    }

    /** The first line of the notification in Hebrew. Without the mark the sign moved: "0.4+". */
    @Test
    fun aMarkedDeltaKeepsItsSignInFrontInALineWithHebrewText() {
        val rate = "2.00 יח׳/שעה"

        val unmarked = "9.9 ↗ +0.4 • $rate"
        assertThat(Bidi(unmarked, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()).isFalse()

        val marked = "9.9 ↗ ${leftToRightText("+0.4")} • $rate"
        val bidi = Bidi(marked, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        assertThat(bidi.baseIsLeftToRight()).isTrue()
        // Level 0 is plain left to right, so the sign stays before the number
        assertThat(bidi.getLevelAt(marked.indexOf('+'))).isEqualTo(0)
        assertThat(bidi.getLevelAt(marked.indexOf("0.4"))).isEqualTo(0)
    }
}
