package app.aaps.ui.compose.permissionsSheet

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.plugin.PermissionGroup

@Immutable
data class PermissionsUiState(
    val items: List<PermissionItem> = emptyList(),
    val hasAnyMissing: Boolean = false,
    val showSheet: Boolean = false,
)

@Immutable
data class PermissionItem(
    val group: PermissionGroup,
    val granted: Boolean,
)

sealed interface PermissionsSideEffect {
    data class RequestPermissions(val permissions: List<String>) : PermissionsSideEffect
    data class LaunchSpecialPermission(val group: PermissionGroup) : PermissionsSideEffect
    data class ShowError(val message: String) : PermissionsSideEffect
    data object PermanentlyDenied : PermissionsSideEffect
}
