package app.aaps.ui.compose.permissionsSheet

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginPermissions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginPermissions: PluginPermissions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()
    private val _sideEffect = MutableSharedFlow<PermissionsSideEffect>()
    val sideEffect: SharedFlow<PermissionsSideEffect> = _sideEffect

    fun refresh() {
        val allGroups = pluginPermissions.collectAllPermissions(context)
        val missingGroups = pluginPermissions.collectMissingPermissions(context)
        val missingPermSets = missingGroups.map { it.permissions.toSet() }.toSet()

        val items = allGroups.map { group ->
            PermissionItem(
                group = group,
                granted = group.permissions.toSet() !in missingPermSets
            )
        }
        _uiState.value = PermissionsUiState(
            items = items,
            hasAnyMissing = items.any { !it.granted },
            showSheet = items.any { !it.granted }
        )
    }

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
