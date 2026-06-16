package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalConfig

/**
 * Small badge marking something that two-way syncs with the master ("main phone").
 *
 * The raw marker — renders nothing when [visible] is false, so it's safe to drop anywhere. Use the
 * [SyncBadge] key overload for preference titles (it computes visibility from the key); pass an explicit
 * flag here for callers that already know (e.g. a synced Configuration category).
 */
@Composable
fun SyncBadge(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    // PhonelinkRing ("linked to another phone"), muted/small so it reads as a passive status marker,
    // NOT a tappable refresh/sync control.
    Icon(
        imageVector = Icons.Default.PhonelinkRing,
        contentDescription = stringResource(R.string.pref_syncs_with_main_phone),
        modifier = modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

/**
 * Preference-title overload: shown ONLY on a client (AAPSCLIENT) and ONLY for keys declared
 * [SyncDirection.Bidirectional] (read from the key's [NonPreferenceKey.sync] — the single source of truth).
 */
@Composable
fun SyncBadge(key: NonPreferenceKey?, modifier: Modifier = Modifier) =
    SyncBadge(
        visible = LocalConfig.current.AAPSCLIENT && key?.sync?.direction == SyncDirection.Bidirectional,
        modifier = modifier
    )

/**
 * Preference title text with a trailing [SyncBadge]. Drop-in replacement for `Text(stringResource(id))`
 * in an `Adaptive*PreferenceItem` title slot; the badge only appears on a client for Bidirectional keys.
 */
@Composable
fun PreferenceTitleWithSyncBadge(titleResId: Int, key: NonPreferenceKey?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(titleResId))
        SyncBadge(key, Modifier.padding(start = 6.dp))
    }
}
