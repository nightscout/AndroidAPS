package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.stringResource

/**
 * Small badge marking something that two-way syncs with the master ("main phone").
 *
 * The raw marker — renders nothing when [visible] is false, so it's safe to drop anywhere. Use
 * [TextWithSyncBadge]/[PreferenceTitleWithSyncBadge] for preference titles (they compute visibility
 * from the key and keep the badge inline with wrapped text); pass an explicit flag here for callers
 * that already know (e.g. a synced Configuration category).
 */
@Composable
fun SyncBadge(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    // PhonelinkRing ("linked to another phone"), muted/small so it reads as a passive status marker,
    // NOT a tappable refresh/sync control.
    Icon(
        imageVector = Icons.Default.PhonelinkRing,
        contentDescription = stringResource(CoreUiStrings.pref_syncs_with_main_phone),
        modifier = modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

/** Badge visibility for [key]: shown ONLY on a client (AAPSCLIENT) and ONLY for keys declared
 *  [SyncDirection.Bidirectional] (read from the key's [NonPreferenceKey.sync] — the single source of truth). */
@Composable
private fun syncBadgeVisible(key: NonPreferenceKey?): Boolean =
    LocalConfig.current.AAPSCLIENT && key?.sync?.direction == SyncDirection.Bidirectional

/**
 * Text with a trailing [SyncBadge] rendered as inline text content: on a wrapped title the badge
 * flows right after the last word (on the last line) instead of being pushed to the far end of the
 * row. Renders a plain [Text] when the badge is not visible.
 */
@Composable
fun TextWithSyncBadge(
    text: String,
    key: NonPreferenceKey?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    if (!syncBadgeVisible(key)) {
        Text(text = text, modifier = modifier, style = style, color = color)
        return
    }
    val badgeId = "syncBadge"
    val annotated = buildAnnotatedString {
        append(text)
        append(' ')
        appendInlineContent(badgeId, alternateText = " ")
    }
    val inlineContent = mapOf(
        badgeId to InlineTextContent(
            // Sized relative to the font so the badge scales with the title (~14dp at default title size)
            Placeholder(width = 0.9.em, height = 0.9.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(
                imageVector = Icons.Default.PhonelinkRing,
                contentDescription = stringResource(CoreUiStrings.pref_syncs_with_main_phone),
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    )
    Text(text = annotated, inlineContent = inlineContent, modifier = modifier, style = style, color = color)
}

/**
 * Preference title text with a trailing inline [SyncBadge]. Drop-in replacement for
 * `Text(stringResource(id))` in an `Adaptive*PreferenceItem` title slot; the badge only appears
 * on a client for Bidirectional keys.
 */
@Composable
fun PreferenceTitleWithSyncBadge(title: TextRef, key: NonPreferenceKey?) =
    TextWithSyncBadge(stringResource(title), key)
