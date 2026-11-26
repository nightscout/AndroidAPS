package app.aaps.ui.compose.permissionsSheet

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class PermissionsViewModel(
    private val pluginPermissions: PluginPermissions,
    private val preferences: Preferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()
    private val _sideEffect = MutableSharedFlow<PermissionsSideEffect>()
    val sideEffect: SharedFlow<PermissionsSideEffect> = _sideEffect

    /**
     * Reads what is granted. The sheet opens by itself, and the bottom bar shows its button, while a permission is
     * missing that the user should be shown: a needed one, or an optional one they have not declined ([decline]).
     */
    fun refresh() {
        val allGroups = pluginPermissions.collectAllPermissions()
        val missingGroups = pluginPermissions.collectMissingPermissions()
        val missingPermSets = missingGroups.map { it.permissions.toSet() }.toSet()
        val declined = declinedPermissions()

        val items = allGroups.map { group ->
            PermissionItem(
                group = group,
                granted = group.permissions.toSet() !in missingPermSets,
                declined = group.optional && group.permissions.all { it in declined }
            )
        }
        _uiState.value = PermissionsUiState(
            items = items,
            hasAnyMissing = items.any { it.wanted },
            showSheet = items.any { it.wanted }
        )
    }

    /** "Not now" on an optional permission: kept on this phone, so it no longer opens the sheet by itself. */
    fun decline(group: PermissionGroup) {
        if (!group.optional) return
        preferences.put(StringNonKey.PermissionsDeclined, (declinedPermissions() + group.permissions).joinToString("\n"))
        val state = uiState.value
        val items = state.items.map { if (it.group == group) it.copy(declined = true) else it }
        // The sheet stays open, with the item still listed: the user sees the answer took
        _uiState.value = state.copy(items = items, hasAnyMissing = items.any { it.wanted })
    }

    private fun declinedPermissions(): Set<String> =
        preferences.get(StringNonKey.PermissionsDeclined).split("\n").filter { it.isNotEmpty() }.toSet()

    fun showSheet() {
        _uiState.value = uiState.value.copy(showSheet = true)
    }

    fun dismissSheet() {
        _uiState.value = uiState.value.copy(showSheet = false)
    }

    fun requestPermission(group: PermissionGroup) {
        viewModelScope.launch {
            if (group.special) {
                _sideEffect.emit(PermissionsSideEffect.LaunchSpecialPermission(group))
            } else {
                _sideEffect.emit(PermissionsSideEffect.RequestPermissions(group.permissions))
            }
        }
    }

    fun onPermissionsDenied(deniedPermissions: List<String>, canShowRationale: (String) -> Boolean) {
        // If rationale can't be shown, the permission is permanently denied
        val permanentlyDenied = deniedPermissions.any { !canShowRationale(it) }
        if (permanentlyDenied) {
            viewModelScope.launch {
                _sideEffect.emit(PermissionsSideEffect.PermanentlyDenied)
            }
        }
    }
}
