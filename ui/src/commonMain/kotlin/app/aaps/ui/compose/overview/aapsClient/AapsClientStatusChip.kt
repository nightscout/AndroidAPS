package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.stringResource

/**
 * Compact chip showing "Label: Value" with color-coded value.
 * Used in the collapsed state of [AapsClientStatusCard].
 *
 * @see AapsClientStatusChipInfoPreview
 * @see AapsClientStatusChipWarnPreview
 * @see AapsClientStatusChipUrgentPreview
 */
@Composable
fun AapsClientStatusChip(
    item: AapsClientStatusItem,
    modifier: Modifier = Modifier
) {
    val valueColor = when (item.level) {
        AapsClientLevel.INFO   -> MaterialTheme.colorScheme.onSurface
        AapsClientLevel.WARN   -> AapsTheme.generalColors.statusWarning
        AapsClientLevel.URGENT -> AapsTheme.generalColors.statusCritical
    }

    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor)) {
            append("${item.label}: ")
        }
        withStyle(SpanStyle(color = valueColor)) {
            append(item.value)
        }
    }

    // Warning / urgent was only the colour of the value; said as the state.
    val levelState = item.level.toSpokenLevel()
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = if (levelState != null) modifier.semantics { stateDescription = levelState } else modifier
    )
}

/** A follower status level in words, for a screen reader. INFO says nothing, like the notification levels. */
@Composable
internal fun AapsClientLevel.toSpokenLevel(): String? = when (this) {
    AapsClientLevel.INFO   -> null
    AapsClientLevel.WARN   -> stringResource(CoreUiStrings.warning)
    AapsClientLevel.URGENT -> stringResource(CoreUiStrings.critical)
}
