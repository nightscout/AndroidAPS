package app.aaps.core.ui.compose.banner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource

/**
 * High-visibility banner for failure / error conditions (Material3 errorContainer).
 * Use for: pump pairing errors, deactivation failures, communication errors, password errors, etc.
 *
 * @see ErrorBannerPreview
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) = BannerCore(
    message = message,
    icon = Icons.Default.Error,
    iconDescription = stringResource(CoreUiStrings.error),
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier = modifier
)

/**
 * Notice banner for state warnings that aren't failures (Material3 tertiaryContainer).
 * Use for: "this entry will be recorded only", reduced-functionality notices, advisory states.
 *
 * @see WarningBannerPreview
 */
@Composable
fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier
) = BannerCore(
    message = message,
    icon = Icons.Default.Warning,
    iconDescription = stringResource(CoreUiStrings.warning),
    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    modifier = modifier
)

/**
 * Shared body of [ErrorBanner] and [WarningBanner].
 *
 * Accessibility: a banner is composed only while there is something to say, so the whole banner
 * **appears** rather than changing. A live region would not help here, because Compose only sends a
 * live region event for a property change on a node that was already in the tree on the previous
 * pass; a node that has just appeared is skipped. The supported way to announce something that just
 * appeared is a pane title, so the banner container carries `paneTitle`. Compose then sends "pane
 * appeared" with that title when the banner shows up, and "pane title changed" when the banner stays
 * but its message changes, which covers both ways a banner can start saying something new.
 *
 * The title is the message itself. A short title like "Error" would tell the user that something
 * happened but not what, and the message is the only text the banner has.
 *
 * The container does not merge its children, so the icon description and the message stay separate
 * nodes: the icon says which severity this is, the text says what happened.
 *
 * **Never put a banner inside a container that merges its descendants** - `Modifier.clickable`,
 * `selectable`, `toggleable`, `Card(onClick = ...)`, `ListItem`, or an explicit
 * `semantics(mergeDescendants = true)`. `SemanticsProperties.PaneTitle` is declared with a merge
 * policy that **throws**, and a merging ancestor merges every key of every non-merging descendant,
 * so "tap the banner to retry" would crash with `IllegalStateException` as soon as the merged tree
 * is built - which is whenever a screen reader is on, and in any Compose test using the merged tree.
 * Every call site today wraps the banner in a plain `Column` or `Box`; keep it that way.
 */
@Composable
private fun BannerCore(
    message: String,
    icon: ImageVector,
    iconDescription: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = message },
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}
