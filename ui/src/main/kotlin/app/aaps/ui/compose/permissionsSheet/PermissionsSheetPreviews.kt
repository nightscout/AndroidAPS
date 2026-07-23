package app.aaps.ui.compose.permissionsSheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.ui.R

@Preview(showBackground = true)
@Composable
internal fun PermissionsSheetContentPreview() {
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
