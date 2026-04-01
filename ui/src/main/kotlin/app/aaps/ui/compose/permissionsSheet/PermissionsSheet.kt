package app.aaps.ui.compose.permissionsSheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(
    items: List<PermissionItem>,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: (PermissionGroup) -> Unit,
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
        )
    }
}

@Composable
private fun PermissionsSheetContent(
    items: List<PermissionItem>,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: (PermissionGroup) -> Unit,
) {
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.permission_sheet_subtitle),
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
                        onGrant = { onRequestPermission(item.group) }
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
) {
    ListItem(
        leadingContent = {
            if (item.granted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        headlineContent = {
            Text(stringResource(item.group.rationaleTitle))
        },
        supportingContent = {
            Text(
                text = stringResource(item.group.rationaleDescription),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            if (!item.granted || item.group.alwaysShowAction) {
                TextButton(onClick = onGrant) {
                    Text(
                        stringResource(
                            if (item.granted) R.string.permission_change
                            else R.string.permission_grant
                        )
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PermissionsSheetContentPreview() {
    val items = listOf(
        PermissionItem(
            group = PermissionGroup(
                permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
                rationaleTitle = R.string.permission_sheet_title,
                rationaleDescription = R.string.permission_sheet_subtitle,
            ),
            granted = true
        ),
        PermissionItem(
            group = PermissionGroup(
                permissions = listOf("android.permission.POST_NOTIFICATIONS"),
                rationaleTitle = R.string.permission_grant,
                rationaleDescription = R.string.permission_sheet_subtitle,
            ),
            granted = false
        ),
        PermissionItem(
            group = PermissionGroup(
                permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                rationaleTitle = R.string.permission_change,
                rationaleDescription = R.string.permission_sheet_subtitle,
                alwaysShowAction = true,
            ),
            granted = true
        ),
    )
    MaterialTheme {
        PermissionsSheetContent(
            items = items,
            snackbarHostState = remember { SnackbarHostState() },
            onRequestPermission = {},
        )
    }
}
