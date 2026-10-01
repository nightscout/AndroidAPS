package app.aaps.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.aaps.core.ui.compose.AapsSpacing
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * Shared metrics for [ManagementCarousel]. Keeping them here means all management screens
 * stay visually in sync instead of each repeating its own literals.
 */
object CarouselDefaults {

    /** Height of a carousel card. Most screens use this; profile management uses a shorter card. */
    val CardHeight: Dp = 200.dp

    /** Horizontal peek so the neighbouring cards stay partially visible. */
    val ContentPadding: Dp = 64.dp

    /** Gap between adjacent cards. */
    val PageSpacing: Dp = 16.dp

    /** Scale applied to a fully off-centre card; the centred card renders at 1f. */
    const val MIN_SCALE = 0.85f

    /** Alpha applied to a fully off-centre card; the centred card renders at 1f. */
    const val MIN_ALPHA = 0.5f
}

/**
 * State handed to a card slot of [ManagementCarousel].
 *
 * Passed as a single object rather than loose parameters so future carousel features can add
 * fields without breaking every call site.
 *
 * @param page Index of the card being composed. In reorder mode this is the position in the
 *        *working* order, which is not the index of the underlying data.
 * @param isSelected True when this card is the settled, centred one.
 * @param isReordering True while reorder mode is on. Cards keep their normal size and rendering;
 *        the carousel puts the move controls below the row rather than on the card. Exposed so a
 *        card can drop its own click handling for the duration.
 */
data class CarouselItemState(
    val page: Int,
    val isSelected: Boolean,
    val isReordering: Boolean = false
)

/**
 * Optional reorder ("sort") mode for [ManagementCarousel].
 *
 * While [isActive] the carousel replaces its page indicator with move-earlier / move-later buttons
 * and a position readout, and follows the card as it changes position. Cards render exactly as they
 * do normally: stepping a card does not require seeing where it will land, which a drag would, so
 * there is no need to shrink them.
 *
 * The caller owns the working order and commits it once the mode is left; this config has no save
 * hook by design, so a long reshuffle costs one write rather than one per step.
 *
 * @param isActive True while reorder mode is on screen.
 * @param itemCount Number of cards in the working order. Taken from the caller rather than read off
 *        the pager, because the underlying list can grow while the mode is open (an NS push, an
 *        automation) and a move past the end of the *working* order would be rejected.
 * @param onMove Move the card at `fromPage` to `toPage`, returning whether the move was applied.
 *        A rejected move must return false, so the carousel does not scroll as though something
 *        happened. Always a single step from the buttons, but declared for arbitrary index pairs.
 * @param moveEarlierLabel Content description for the move-earlier button. Hoisted as a [String]
 *        because the carousel has no access to the caller's string resources.
 * @param moveLaterLabel Content description for the move-later button.
 * @param positionLabel Visible "3 / 7" style readout for a position.
 * @param positionDescription Spoken form of the same, e.g. "Position 3 of 7". Announced politely on
 *        every move, which is the only feedback a screen-reader user gets that a press did anything.
 * @param canMove False for a pinned card, by page. A move is offered only when both the card and the
 *        position it would trade with are movable, so a pinned card can neither be moved nor
 *        displaced. **No caller passes this yet** — it exists for the temp-target carousel, whose
 *        standalone active card is pinned at page 0. Until one does, every card is movable.
 */
data class CarouselReorderConfig(
    val isActive: Boolean,
    val itemCount: Int,
    val onMove: (fromPage: Int, toPage: Int) -> Boolean,
    val moveEarlierLabel: String,
    val moveLaterLabel: String,
    val positionLabel: (page: Int) -> String,
    val positionDescription: (page: Int) -> String,
    val canMove: (page: Int) -> Boolean = { true }
)

/**
 * Horizontally paged card carousel used by the management screens (profile, temp target,
 * insulin, quick wizard), plus the [PageIndicatorDots] underneath it.
 *
 * The scale/alpha de-emphasis of off-centre cards is applied by this composable, so card
 * composables do not need to know they live in a carousel.
 *
 * [state] is hoisted rather than created here: each screen drives page changes with its own
 * side effects (unsaved-changes vetoes, pinned cards, programmatic scrolls), and those are too
 * screen-specific to generalise. Callers that pass a non-null [reorder] must suspend the effects
 * that write a page change back to their view model while [CarouselReorderConfig.isActive],
 * otherwise the carousel following a moved card would be read as the user selecting a different one.
 *
 * @param state Pager state owned by the caller.
 * @param modifier Modifier for the carousel container.
 * @param cardHeight Height of the pager row, see [CarouselDefaults.CardHeight].
 * @param userScrollEnabled Set false to block swiping, e.g. while an editor holds unsaved changes.
 * @param showIndicator Whether to render [PageIndicatorDots] below the cards. Reorder mode puts its
 *        controls in that slot instead.
 * @param reorder Reorder mode configuration, or null for a plain carousel.
 * @param card Slot rendering a single card. It is stretched to the full page area.
 */
@Composable
fun ManagementCarousel(
    state: PagerState,
    modifier: Modifier = Modifier,
    cardHeight: Dp = CarouselDefaults.CardHeight,
    userScrollEnabled: Boolean = true,
    showIndicator: Boolean = true,
    reorder: CarouselReorderConfig? = null,
    card: @Composable (CarouselItemState) -> Unit
) {
    val isReordering = reorder != null && reorder.isActive

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight),
            contentPadding = PaddingValues(horizontal = CarouselDefaults.ContentPadding),
            pageSpacing = CarouselDefaults.PageSpacing,
            userScrollEnabled = userScrollEnabled
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue
                        val fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        val scale = lerp(CarouselDefaults.MIN_SCALE, 1f, fraction)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(CarouselDefaults.MIN_ALPHA, 1f, fraction)
                    }
            ) {
                card(
                    CarouselItemState(
                        page = page,
                        isSelected = state.currentPage == page,
                        isReordering = isReordering
                    )
                )
            }
        }

        // The controls take the indicator's slot rather than overlaying the card: they must be a
        // single node that survives a move (a per-page overlay is destroyed and recomposed on the
        // next card, losing accessibility focus every step), they must sit outside the pager's
        // scale/alpha layer, and the cards already use their bottom edge for a progress bar.
        if (reorder != null && isReordering) {
            ReorderControls(state = state, reorder = reorder)
        } else if (showIndicator) {
            PageIndicatorDots(
                pageCount = state.pageCount,
                currentPage = state.currentPage
            )
        }
    }
}

/**
 * Move-earlier / move-later buttons and a position readout, shown below the carousel in reorder
 * mode. Acts on whichever card is currently centred.
 *
 * The icons are auto-mirrored, so "earlier" points towards the start of the list in both
 * left-to-right and right-to-left layouts.
 */
@Composable
private fun ReorderControls(
    state: PagerState,
    reorder: CarouselReorderConfig
) {
    val scope = rememberCoroutineScope()

    // Latched synchronously in the click handler. `isScrollInProgress` alone is not enough: the
    // follow-scroll is dispatched, so for a frame or two after a tap the old handler is still live
    // with the old page — a second tap in that window would move whichever card had just slid into
    // that slot instead of the one the user is watching.
    var moveInFlight by remember { mutableStateOf(false) }

    val page = state.currentPage
    val ready = !moveInFlight && !state.isScrollInProgress
    // A move needs both ends free: a pinned card cannot be moved, and cannot be displaced either.
    val movable = reorder.canMove(page)
    val canMoveEarlier = ready && movable && page > 0 && reorder.canMove(page - 1)
    val canMoveLater = ready && movable && page < reorder.itemCount - 1 && reorder.canMove(page + 1)

    fun move(target: Int) {
        moveInFlight = true
        if (reorder.onMove(page, target)) {
            scope.launch {
                // Follow the card so the user keeps watching the profile they are moving, rather
                // than whichever one slid into the centre.
                try {
                    state.animateScrollToPage(target)
                } finally {
                    moveInFlight = false
                }
            }
        } else {
            moveInFlight = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AapsSpacing.small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = { move(page - 1) },
            enabled = canMoveEarlier
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = reorder.moveEarlierLabel
            )
        }

        Text(
            text = reorder.positionLabel(page),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = AapsSpacing.extraLarge)
                // Spoken instead of the terse visible form, and announced on change — without this
                // a screen-reader user gets no confirmation that a press did anything.
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = reorder.positionDescription(page)
                }
        )

        FilledTonalIconButton(
            onClick = { move(page + 1) },
            enabled = canMoveLater
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = reorder.moveLaterLabel
            )
        }
    }
}
