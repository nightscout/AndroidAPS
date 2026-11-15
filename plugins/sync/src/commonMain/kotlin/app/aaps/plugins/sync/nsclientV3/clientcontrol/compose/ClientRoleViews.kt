package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.clientcontrol.label
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource

/**
 * One choice of the four client roles, in ladder order. [selected] null shows none chosen (the Add client dialog
 * asks for a deliberate choice). Short labels, no check mark, so the four fit on a narrow phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientRoleSelector(
    selected: ClientRole?,
    onSelect: (ClientRole) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ClientRole.entries.forEach { role ->
            SegmentedButton(
                selected = selected == role,
                onClick = { onSelect(role) },
                shape = SegmentedButtonDefaults.itemShape(index = role.ordinal, count = ClientRole.entries.size),
                icon = {},
                // The default 12 dp at each side leaves too little for a label in the Add client dialog.
                contentPadding = PaddingValues(horizontal = AapsSpacing.small, vertical = AapsSpacing.medium)
            ) {
                // Shrinks the label only where it does not fit: the narrower Add client dialog, a long translation.
                Text(
                    text = stringResource(role.label()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = MaterialTheme.typography.labelSmall.fontSize, maxFontSize = LocalTextStyle.current.fontSize)
                )
            }
        }
    }
}

/** (i) that explains the four roles. */
@Composable
internal fun ClientRolesInfoButton() {
    var showInfo by remember { mutableStateOf(false) }
    IconButton(onClick = { showInfo = true }) {
        Icon(imageVector = Icons.Outlined.Info, contentDescription = stringResource(CoreUiStrings.client_roles_info_open))
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(CoreUiStrings.client_role_title)) },
            // The text is long; scroll it on a small screen or with a large font.
            text = { Text(text = stringResource(CoreUiStrings.client_roles_info), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(CoreUiStrings.ok)) }
            }
        )
    }
}
