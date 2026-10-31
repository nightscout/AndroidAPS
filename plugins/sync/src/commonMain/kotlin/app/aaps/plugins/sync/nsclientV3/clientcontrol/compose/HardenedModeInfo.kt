package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource

/**
 * "Hardened mode on", with an (i) button that explains what hardened mode does.
 *
 * Shown while hardened mode is on: on the master's Authorized clients screen, and on a paired
 * client's pairing screen. The text is the same on both sides, because both phones follow the same
 * rule (see `Preferences.hardenedMode`).
 */
@Composable
internal fun HardenedModeInfoRow(modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(CoreUiStrings.hardened_mode_on),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { showInfo = true }) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = stringResource(CoreUiStrings.hardened_mode_info_open))
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(CoreUiStrings.hardened_mode_title)) },
            // The text is long; scroll it on a small screen or with a large font.
            text = { Text(text = stringResource(CoreUiStrings.hardened_mode_info), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(CoreUiStrings.ok)) }
            }
        )
    }
}
