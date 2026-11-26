package app.aaps.ui.compose.permissionsSheet

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.plugin.PermissionGroup

@Immutable
data class PermissionsUiState(
    val items: List<PermissionItem> = emptyList(),
    val hasAnyMissing: Boolean = false,
    val showSheet: Boolean = false,
)

/** [declined]: an optional one the user answered "Not now" on this phone; it is still listed and can be granted. */
@Immutable
data class PermissionItem(
    val group: PermissionGroup,
    val granted: Boolean,
    val declined: Boolean = false,
) {

    /** Missing in a way the user should be shown by itself: a needed one, or an optional one not declined. */
    val wanted: Boolean get() = !granted && !declined
}

sealed interface PermissionsSideEffect {
    data class RequestPermissions(val permissions: List<String>) : PermissionsSideEffect
    data class LaunchSpecialPermission(val group: PermissionGroup) : PermissionsSideEffect
    data class ShowError(val message: String) : PermissionsSideEffect
    data object PermanentlyDenied : PermissionsSideEffect
}
