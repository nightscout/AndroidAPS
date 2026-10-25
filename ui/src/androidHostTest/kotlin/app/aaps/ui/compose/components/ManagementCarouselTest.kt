package app.aaps.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric composable test for [ManagementCarousel], for the two things a mouse user needs.
 *
 * Compose pagers ignore a mouse drag, and on a wide window a full-width card pushed its neighbours
 * out of view, so on desktop there was no way at all to change card. The carousel now limits the card
 * width and pages on a mouse drag. These tests keep both, and check that touch and clicks still behave
 * as before.
 *
 * The screen is 1000 dp wide so the carousel can be given any width up to that.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1000dp-h800dp")
class ManagementCarouselTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var pagerState: PagerState
    private var clicks = 0

    /** Three cards. Like the real screens, a click on a card scrolls to it. */
    private fun setCarousel(
        width: Dp,
        userScrollEnabled: Boolean = true,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme {
                    val state = rememberPagerState { 3 }
                    pagerState = state
                    val scope = rememberCoroutineScope()
                    Box(modifier = Modifier.width(width)) {
                        ManagementCarousel(state = state, userScrollEnabled = userScrollEnabled) { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("card${item.page}")
                                    .clickable {
                                        clicks++
                                        scope.launch { state.animateScrollToPage(item.page) }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    /** Press, move in [steps] steps of one frame each by [fraction] of the card width, release. */
    private fun MouseInjectionScope.drag(fraction: Float, steps: Int = 10) {
        moveTo(center)
        press()
        repeat(steps) { moveBy(Offset(width * fraction / steps, 0f)) }
        release()
    }

    @Test
    fun aCardOnAPhoneKeepsItsWidth() {
        setCarousel(width = 400.dp)
        // 400 dp less 64 dp on each side, as before the width limit.
        compose.onNodeWithTag("card0").assertWidthIsEqualTo(272.dp)
    }

    @Test
    fun theNextCardStaysInViewOnAWideWindow() {
        setCarousel(width = 900.dp)
        compose.onNodeWithTag("card0").assertWidthIsEqualTo(CarouselDefaults.MaxCardWidth)
        // Without the limit the neighbour, shrunk to 85 %, started beyond the right edge.
        val next = compose.onNodeWithTag("card1").assertIsDisplayed().getBoundsInRoot()
        assertThat(next.left).isLessThan(900.dp - 100.dp)
    }

    @Test
    fun aClickOnTheNextCardOnAWideWindowMovesToIt() {
        setCarousel(width = 900.dp)
        compose.onNodeWithTag("card1").performMouseInput { click(Offset(20f, height / 2f)) }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(1)
    }

    @Test
    fun aMouseDragToTheLeftMovesToTheNextCard() {
        setCarousel(width = 400.dp)
        compose.onNodeWithTag("card0").performMouseInput { drag(-0.6f) }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(1)
        // The drag must not count as a click on the card it started on.
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun aShortQuickMouseFlickMovesToTheNextCard() {
        // Too short to pass the middle, so only the speed can carry it on, as with a finger.
        setCarousel(width = 400.dp)
        compose.onNodeWithTag("card0").performMouseInput { drag(-0.25f, steps = 3) }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(1)
    }

    @Test
    fun aSlowShortMouseDragStaysOnTheCard() {
        setCarousel(width = 400.dp)
        compose.onNodeWithTag("card0").performMouseInput {
            moveTo(center)
            press()
            // A quarter of the card over half a second, then a pause before letting go.
            repeat(30) { moveBy(Offset(-width * 0.25f / 30, 0f), delayMillis = 16) }
            moveBy(Offset.Zero, delayMillis = 300)
            release()
        }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(0)
    }

    @Test
    fun aMouseDragBackMovesToThePreviousCard() {
        setCarousel(width = 400.dp)
        compose.runOnIdle { pagerState.requestScrollToPage(2) }
        compose.onNodeWithTag("card2").performMouseInput { drag(0.6f) }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(1)
    }

    @Test
    fun inRightToLeftAMouseDragToTheRightMovesToTheNextCard() {
        setCarousel(width = 400.dp, layoutDirection = LayoutDirection.Rtl)
        compose.onNodeWithTag("card0").performMouseInput { drag(0.6f) }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(1)
    }

    @Test
    fun aMouseClickWithoutMovingIsStillAClick() {
        setCarousel(width = 400.dp)
        compose.onNodeWithTag("card0").performMouseInput { click(center) }
        compose.waitForIdle()
        assertThat(clicks).isEqualTo(1)
        assertThat(pagerState.currentPage).isEqualTo(0)
    }

    @Test
    fun aMouseDragDoesNothingWhileSwipingIsBlocked() {
        // A screen blocks swiping while its editor holds unsaved changes; the mouse must obey too.
        setCarousel(width = 400.dp, userScrollEnabled = false)
        compose.onNodeWithTag("card0").performMouseInput { drag(-0.6f) }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(0)
    }

    @Test
    fun aFingerSwipeStillMovesOneCard() {
        setCarousel(width = 400.dp)
        compose.onNodeWithTag("card0").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(pagerState.currentPage).isEqualTo(1)
    }
}
