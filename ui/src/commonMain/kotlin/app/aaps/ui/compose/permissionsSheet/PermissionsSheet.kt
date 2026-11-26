package app.aaps.ui.compose.permissionsSheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(
    items: List<PermissionItem>,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: (PermissionGroup) -> Unit,
    onDecline: (PermissionGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        PermissionsSheetContent(
            items = items,
            snackbarHostState = snackbarHostState,
            onRequestPermission = onRequestPermission,
            onDecline = onDecline,
        )
    }
}

/**
 * The permissions, each with its reason. A needed one that is missing has a warning and Grant. An optional one
 * ([PermissionGroup.optional]) is "Recommended", with no warning, and can also be answered "Not now" ([onDecline]).
 *
 * @see PermissionsSheetContentPreview
 */
@Composable
internal fun PermissionsSheetContent(
    items: List<PermissionItem>,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: (PermissionGroup) -> Unit,
    onDecline: (PermissionGroup) -> Unit,
) {
    // "Required" only while a needed one is missing: with only a recommended one open, it would say what is not so
    val neededMissing = items.any { !it.granted && !it.group.optional }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(if (neededMissing) UiStrings.permission_sheet_title else UiStrings.permission_sheet_title_recommended),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (neededMissing) UiStrings.permission_sheet_subtitle else UiStrings.permission_sheet_subtitle_recommended),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn {
                items(items, key = { it.group.permissions.joinToString() }) { item ->
                    PermissionRow(
                        item = item,
                        onGrant = { onRequestPermission(item.group) },
                        onDecline = { onDecline(item.group) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    onGrant: () -> Unit,
    onDecline: () -> Unit,
) {
    val optional = item.group.optional
    ListItem(
        leadingContent = {
            when {
                item.granted -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                // Recommended, not needed: no warning colour, nothing is wrong without it
                optional     -> Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else         -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        overlineContent = if (optional && !item.granted) {
            { Text(stringResource(UiStrings.permission_recommended)) }
        } else null,
        headlineContent = {
            Text(stringResource(item.group.rationaleTitle))
        },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(item.group.rationaleDescription),
                    style = MaterialTheme.typography.bodySmall,
                )
                // Two answers do not fit beside the text: under it, at the end
                if (optional && item.wanted)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDecline) { Text(stringResource(UiStrings.permission_not_now)) }
                        TextButton(onClick = onGrant) { Text(stringResource(UiStrings.permission_grant)) }
                    }
            }
        },
        trailingContent = {
            if ((!item.granted && !(optional && item.wanted)) || item.group.alwaysShowAction) {
                TextButton(onClick = onGrant) {
                    Text(
                        stringResource(
                            if (item.granted) UiStrings.permission_change
                            else UiStrings.permission_grant
                        )
                    )
                }
            }
        }
    )
}
