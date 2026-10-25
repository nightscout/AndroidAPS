package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * AAPS card component that stands out from the background in light and dark mode.
 *
 * Uses [ElevatedCard], but its shadow is not enough: on a dark background it is dark on dark and
 * almost invisible, and with the default `surfaceContainerLow` two cards next to each other looked
 * like one surface. In dark mode Material separates a card by a lighter tone, so the container is
 * `surfaceContainerHigh`. In light mode it is a light grey, and the shadow still helps.
 *
 * @param modifier Modifier to be applied to the card
 * @param selected Whether the card is in selected state (uses secondaryContainer color)
 * @param onClick Makes the whole card one button; null for a card that is not clickable
 * @param content The content of the card
 *
 * @see AapsCardPreview
 * @see AapsCardSelectedPreview
 */
@Composable
fun AapsCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Selected was only the container colour. Said only when on: "not selected" on every card
    // of every list, outside remove mode, would bury the content.
    val cardModifier = if (selected) modifier.semantics { this.selected = true } else modifier
    val colors = CardDefaults.elevatedCardColors(
        containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    )
    if (onClick != null) ElevatedCard(onClick = onClick, modifier = cardModifier, colors = colors, content = content)
    else ElevatedCard(modifier = cardModifier, colors = colors, content = content)
}
