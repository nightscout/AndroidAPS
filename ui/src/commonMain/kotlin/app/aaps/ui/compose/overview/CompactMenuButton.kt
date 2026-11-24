package app.aaps.ui.compose.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource

/**
 * The menu button while the top bar is hidden: in the top left corner of the overview, over the empty
 * corner of the round BG circle, where it is in the full bar too. The one-column layout puts it on the
 * version line, the two-column layout in the corner of its left column.
 *
 * @param iconLift How far the icon is moved up inside its box, so its lines can be level with something
 *   above the box's top, see `OverviewScreenStacked`.
 */
@Composable
internal fun CompactMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier, iconLift: Dp = 0.dp) {
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .size(COMPACT_MENU_BUTTON_SIZE)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(CoreUiStrings.open_navigation),
            modifier = Modifier
                .padding(start = COMPACT_MENU_ICON_START)
                .offset(y = -iconLift)
        )
    }
}

/**
 * Touch area of the menu button over the corner of the BG circle, the usual size. The icon has its usual
 * size too, the same as in the full bar: its lines fill only the middle of its box, so they stay clear of
 * the ring when the icon sits in the top left of this area.
 */
private val COMPACT_MENU_BUTTON_SIZE = 48.dp

/** The icon's lines start 3 dp into its box: this puts them 8 dp from the screen edge, in line with the rest. */
private val COMPACT_MENU_ICON_START = 5.dp
