package app.aaps.ui.compose.permissionsSheet

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activePlugin: ActivePlugin,
) : ViewModel() {

    val uiState: StateFlow<PermissionsUiState> field = MutableStateFlow(PermissionsUiState())
    private val _sideEffect = MutableSharedFlow<PermissionsSideEffect>()
    val sideEffect: SharedFlow<PermissionsSideEffect> = _sideEffect

    fun refresh() {
        val allGroups = activePlugin.collectAllPermissions(context)
        val missingGroups = activePlugin.collectMissingPermissions(context)
        val missingPermSets = missingGroups.map { it.permissions.toSet() }.toSet()

        val items = allGroups.map { group ->
            PermissionItem(
                group = group,
                granted = group.permissions.toSet() !in missingPermSets
            )
        }
        uiState.value = PermissionsUiState(
            items = items,
            hasAnyMissing = items.any { !it.granted },
            showSheet = items.any { !it.granted }
        )
    }

    fun showSheet() {
        uiState.value = uiState.value.copy(showSheet = true)
    }

    fun dismissSheet() {
        uiState.value = uiState.value.copy(showSheet = false)
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
