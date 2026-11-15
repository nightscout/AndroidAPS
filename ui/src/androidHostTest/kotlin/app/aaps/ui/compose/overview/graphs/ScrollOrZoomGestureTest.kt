package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "use scroll or zoom on BG chart" objective must be completed only by a real gesture. These tests check
 * that a tap does not count, that a scroll and a pinch do, and that a child which consumes the drag (as the
 * Vico chart does) does not hide the gesture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScrollOrZoomGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private var calls = 0

    private fun setContent() {
        compose.setContent {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .testTag("chart")
                    .onScrollOrZoomGesture { calls++ }
            ) {
                // Consumes the drag, like the chart's own scroll handling.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(rememberDraggableState { }, Orientation.Horizontal)
                )
            }
        }
    }

    @Test
    fun tap_isNotReported() {
        setContent()
        compose.onNodeWithTag("chart").performTouchInput { click() }
        compose.waitForIdle()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun scroll_isReportedAtStartAndEnd() {
        setContent()
        compose.onNodeWithTag("chart").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun pinch_isReported() {
        setContent()
        compose.onNodeWithTag("chart").performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f), end0 = center - Offset(200f, 0f),
                start1 = center + Offset(20f, 0f), end1 = center + Offset(200f, 0f)
            )
        }
        compose.waitForIdle()
        assertThat(calls).isEqualTo(2)
    }
}
