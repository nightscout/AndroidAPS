package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R

/**
 * Full-width error banner that explains why a screen's edit controls are disabled: the client's master
 * is unreachable. Renders nothing when [editingEnabled] is true (master, or client with a reachable
 * master), so callers drop it at the top of any screen that gates editing on master-reachability
 * (preferences, automation, scenes, the TempTarget / Insulin / QuickWizard management screens) and pass
 * the same `editingEnabled` they already compute via [rememberMasterEditingEnabled]. Takes the flag as a
 * param (rather than reading the CompositionLocals itself) so it stays usable in previews/tests where
 * `LocalConfig` isn't provided.
 *
 * @param editingEnabled when true the banner is hidden; pass the screen's `rememberMasterEditingEnabled()`.
 * @param text override message (defaults to the generic editing-disabled wording; scenes passes its own).
 */
@Composable
fun MasterOfflineBanner(
    editingEnabled: Boolean,
    modifier: Modifier = Modifier,
    text: String = if (!LocalMasterControlAllowed.current) stringResource(R.string.master_control_disabled_banner)
    else stringResource(R.string.master_offline_banner)
) {
    if (editingEnabled) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
