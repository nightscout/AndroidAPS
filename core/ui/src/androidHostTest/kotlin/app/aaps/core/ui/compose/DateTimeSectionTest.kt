package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The default test screen is 320 dp wide, too narrow for the 400 dp box below.
@Config(sdk = [35], qualifiers = "w480dp-h800dp")
class DateTimeSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dateAndTimeAreSideBySideWhenTheyFit() {
        setSection(fontScale = 1f)
        val date = compose.onNodeWithContentDescription(DATE, substring = true).getUnclippedBoundsInRoot()
        val time = compose.onNodeWithContentDescription(TIME, substring = true).getUnclippedBoundsInRoot()
        assertThat(time.top).isEqualTo(date.top)
        assertThat(time.left).isGreaterThan(date.right)
    }

    /** Side by side, a long date was cut. One under the other, each field has the full width. */
    @Test
    fun dateAndTimeAreStackedWhenTheDateDoesNotFit() {
        setSection(fontScale = BIG_FONT_SCALE)
        val date = compose.onNodeWithContentDescription(DATE, substring = true).getUnclippedBoundsInRoot()
        val time = compose.onNodeWithContentDescription(TIME, substring = true).getUnclippedBoundsInRoot()
        assertThat(time.top).isGreaterThan(date.bottom)
        assertThat(time.left).isEqualTo(date.left)
    }

    @Test
    fun tapsOpenTheRightPicker() {
        var dateClicks = 0
        var timeClicks = 0
        setSection(fontScale = 1f, onDateClick = { dateClicks++ }, onTimeClick = { timeClicks++ })
        compose.onNodeWithContentDescription(DATE, substring = true).performClick()
        compose.onNodeWithContentDescription(TIME, substring = true).performClick()
        assertThat(dateClicks).isEqualTo(1)
        assertThat(timeClicks).isEqualTo(1)
    }

    private fun setSection(fontScale: Float, onDateClick: () -> Unit = {}, onTimeClick: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                WithFontScale(fontScale) {
                    Box(Modifier.width(400.dp)) {
                        DateTimeSection(
                            dateString = DATE,
                            timeString = TIME,
                            eventTimeChanged = false,
                            onDateClick = onDateClick,
                            onTimeClick = onTimeClick
                        )
                    }
                }
            }
        }
    }

    private companion object {

        const val DATE = "10/11/2026"
        const val TIME = "21:51"
    }
}
