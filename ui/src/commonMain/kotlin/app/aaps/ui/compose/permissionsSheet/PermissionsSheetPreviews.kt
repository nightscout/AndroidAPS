package app.aaps.ui.compose.permissionsSheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.ui.UiStrings

@Preview(showBackground = true)
@Composable
internal fun PermissionsSheetContentPreview() {
    val items = listOf(
        PermissionItem(
            group = PermissionGroup(
                permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
                rationaleTitle = UiStrings.permission_sheet_title,
                rationaleDescription = UiStrings.permission_sheet_subtitle,
            ),
            granted = true
        ),
        PermissionItem(
            group = PermissionGroup(
                permissions = listOf("android.permission.POST_NOTIFICATIONS"),
                rationaleTitle = UiStrings.permission_grant,
                rationaleDescription = UiStrings.permission_sheet_subtitle,
            ),
            granted = false
        ),
        PermissionItem(
            group = PermissionGroup(
                permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                rationaleTitle = UiStrings.permission_change,
                rationaleDescription = UiStrings.permission_sheet_subtitle,
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
