package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.compose.treatments.viewmodels.TreatmentConstants.TREATMENT_HISTORY_DAYS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * ViewModel for ProfileSwitchScreen managing profile switch state and business logic.
 */
@Stable
class ProfileSwitchViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<ProfileSwitchUiState>
        field = MutableStateFlow(ProfileSwitchUiState())

    init {
        loadData()
        observeProfileSwitchChanges()
    }

    /**
     * Load profile switches (both PS and EPS)
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.profileSwitches.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.days(TREATMENT_HISTORY_DAYS).msecs()

                val ps = if (currentState.showInvalidated) {
                    persistenceLayer.getProfileSwitchesIncludingInvalidFromTime(now - millsToThePast, false)
                } else {
                    persistenceLayer.getProfileSwitchesFromTime(now - millsToThePast, false)
                }

                val eps = if (currentState.showInvalidated) {
                    persistenceLayer.getEffectiveProfileSwitchesIncludingInvalidFromTime(now - millsToThePast, false)
                } else {
                    persistenceLayer.getEffectiveProfileSwitchesFromTime(now - millsToThePast, false)
                }

                val profileSwitches = (ps.map { ProfileSealed.PS(value = it, activePlugin = null) } +
                    eps.map { ProfileSealed.EPS(value = it, activePlugin = null) })
                    .sortedByDescending { it.timestamp }

                uiState.update {
                    it.copy(
                        profileSwitches = profileSwitches,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load profile switches", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading profile switches")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to profile switch change events using Flow
     * Observes both ProfileSwitch and EffectiveProfileSwitch changes
     */
    @OptIn(FlowPreview::class)
    private fun observeProfileSwitchChanges() {
        merge(
            persistenceLayer.observeChanges<PS>(),
            persistenceLayer.observeChanges<EPS>()
        )
            .debounce(1000L) // 1 second debounce
            .onEach { loadData() }
            .launchIn(viewModelScope)
    }

    /**
     * Clear error state
     */
    fun clearSnackbar() {
        uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Toggle show/hide invalidated items
     */
    fun toggleInvalidated() {
        uiState.update { it.copy(showInvalidated = !it.showInvalidated) }
        loadData()
    }

    /**
     * Enter selection mode with initial item selected
     */
    fun enterSelectionMode(item: ProfileSealed) {
        uiState.update {
            it.copy(
                isRemovingMode = true,
                selectedItems = setOf(item)
            )
        }
    }

    /**
     * Exit selection mode and clear selection
     */
    fun exitSelectionMode() {
        uiState.update {
            it.copy(
                isRemovingMode = false,
                selectedItems = emptySet()
            )
        }
    }

    /**
     * Toggle selection of an item
     */
    fun toggleSelection(item: ProfileSealed) {
        uiState.update { state ->
            val newSelection = if (item in state.selectedItems) {
                state.selectedItems - item
            } else {
                state.selectedItems + item
            }
            state.copy(selectedItems = newSelection)
        }
    }

    /**
     * Get currently active effective profile switch
     */
    fun getActiveProfile(): ProfileSealed? {
        val eps = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()) }
        return eps?.let { ProfileSealed.EPS(value = it, activePlugin = null) }
    }

    /**
     * Prepare delete confirmation message
     */
    fun getDeleteConfirmationMessage(): String {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return ""

        return if (selected.size == 1) {
            val ps = selected.first()
            "${rh.gs(R.string.careportal_profileswitch)}: ${ps.profileName}\n${dateUtil.dateAndTimeString(ps.timestamp)}"
        } else {
            rh.gs(R.string.confirm_remove_multiple_items, selected.size)
        }
    }

    /**
     * Delete selected items (only PS can be deleted, not EPS)
     */
    fun deleteSelected() {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                selected.forEach { profileSwitch ->
                    if (profileSwitch is ProfileSealed.PS) {
                        persistenceLayer.invalidateProfileSwitch(
                            id = profileSwitch.id,
                            action = Action.PROFILE_SWITCH_REMOVED,
                            source = Sources.Treatments,
                            note = profileSwitch.profileName,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(profileSwitch.timestamp)
                            )
                        )
                    }
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete profile switches", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting profile switches")) }
            }
        }
    }

    /**
     * Get toolbar configuration for current state
     */
    fun getToolbarConfig(onNavigateBack: () -> Unit, onDeleteClick: () -> Unit): ToolbarConfig {
        val state = uiState.value
        return SelectableListToolbar(
            isRemovingMode = state.isRemovingMode,
            selectedCount = state.selectedItems.size,
            onExitRemovingMode = { exitSelectionMode() },
            onNavigateBack = onNavigateBack,
            onDelete = {
                if (state.selectedItems.isNotEmpty()) {
                    onDeleteClick()
                }
            },
            rh = rh,
            showInvalidated = state.showInvalidated,
            onToggleInvalidated = { toggleInvalidated() }
        )
    }
}

/**
 * UI state for ProfileSwitchScreen
 */
@Immutable
data class ProfileSwitchUiState(
    val profileSwitches: List<ProfileSealed> = emptyList(),
    val isLoading: Boolean = true,
    val showInvalidated: Boolean = false,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<ProfileSealed> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null
)
