package app.aaps.ui.compose.tempTarget

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ViewModel for TempTargetManagementScreen managing TT presets and activation.
 */
@HiltViewModel
@Stable
class TempTargetManagementViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val units: GlucoseUnit
        get() = profileFunction.getUnits()

    /**
     * Round value for user display - 0.1 for mmol/L, 1.0 for mg/dL
     */
    private fun roundForDisplay(value: Double, units: GlucoseUnit): Double {
        return when (units) {
            GlucoseUnit.MMOL -> (value * 10).roundToInt() / 10.0
            GlucoseUnit.MGDL -> value.roundToInt().toDouble()
        }
    }

    val uiState: StateFlow<TempTargetManagementUiState>
        field = MutableStateFlow(TempTargetManagementUiState())

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,                          // Don't replay to new collectors
            extraBufferCapacity = 1,             // Buffer one event if no collector
            onBufferOverflow = BufferOverflow.DROP_OLDEST  // Drop old events
        )

    sealed class SideEffect {
        data class ScrollToPreset(val index: Int) : SideEffect()
    }

    fun setScreenMode(mode: ScreenMode) {
        uiState.update { it.copy(screenMode = mode) }
    }

    init {
        loadData()
        observeTempTargetChanges()
    }

    /**
     * Load presets from preferences and active TT from database
     */
    fun loadData() {
        viewModelScope.launch {
            try {
                // Load presets from JSON (or create defaults if empty)
                val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresetsWithNameRes()

                // Load active TT
                val now = dateUtil.now()
                val activeTT = persistenceLayer.getTemporaryTargetActiveAt(now)

                // Calculate remaining time
                val remainingTime = activeTT?.let { tt ->
                    val endTime = tt.timestamp + tt.duration
                    if (endTime > now) endTime - now else 0L
                }

                // Check if notes field should be shown
                val showNotes = preferences.get(BooleanKey.OverviewShowNotesInDialogs)

                // Check if active TT matches a preset (same reason + target value)
                val activePresetIndex = activeTT?.let { tt ->
                    presets.indexOfFirst { preset ->
                        preset.reason == tt.reason &&
                            abs(preset.targetValue - tt.lowTarget) < 0.01
                    }.takeIf { it >= 0 }
                }

                // Default to first preset (or matched active preset)
                val initialPreset = activePresetIndex?.let { presets.getOrNull(it) }
                    ?: presets.firstOrNull()

                // Convert target from mg/dL (storage) to user units (display) with proper rounding
                val targetInUserUnits = initialPreset?.targetValue?.let {
                    roundForDisplay(profileUtil.fromMgdlToUnits(it, units), units)
                } ?: roundForDisplay(profileUtil.fromMgdlToUnits(100.0, units), units)

                uiState.update {
                    it.copy(
                        activeTT = activeTT,
                        activePresetIndex = activePresetIndex,
                        currentCardIndex = activePresetIndex ?: 0,
                        remainingTimeMs = remainingTime,
                        presets = presets,
                        selectedPreset = initialPreset,
                        editorName = initialPreset?.name ?: "",
                        editorTarget = targetInUserUnits,
                        editorDuration = initialPreset?.duration ?: (60L * 60L * 1000L),
                        showNotesField = showNotes,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load temp target presets", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to load presets")
                    )
                }
            }
        }
    }

    /**
     * Refresh runtime data (active TT, presets, preferences) without resetting editor fields.
     * Called from ON_RESUME to handle rotation and background return without losing user edits.
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresetsWithNameRes()
                val now = dateUtil.now()
                val activeTT = persistenceLayer.getTemporaryTargetActiveAt(now)

                val remainingTime = activeTT?.let { tt ->
                    val endTime = tt.timestamp + tt.duration
                    if (endTime > now) endTime - now else 0L
                }

                val showNotes = preferences.get(BooleanKey.OverviewShowNotesInDialogs)

                val activePresetIndex = activeTT?.let { tt ->
                    presets.indexOfFirst { preset ->
                        preset.reason == tt.reason &&
                            abs(preset.targetValue - tt.lowTarget) < 0.01
                    }.takeIf { it >= 0 }
                }

                uiState.update {
                    it.copy(
                        activeTT = activeTT,
                        activePresetIndex = activePresetIndex,
                        remainingTimeMs = remainingTime,
                        presets = presets,
                        showNotesField = showNotes
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to refresh temp target data", e)
            }
        }
    }

    /**
     * Subscribe to temp target change events
     */
    private fun observeTempTargetChanges() {
        persistenceLayer
            .observeChanges<TT>()
            .onEach {
                // Reload data when TT changes in database
                loadData()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Update the current card index (pager position) in UI state.
     * Used to preserve pager position across rotation.
     */
    fun updateCurrentCardIndex(index: Int) {
        uiState.update { it.copy(currentCardIndex = index) }
    }

    /**
     * Select a preset by index and populate editor fields.
     * Skips if the same preset is already selected (e.g., after rotation).
     */
    fun selectPreset(index: Int) {
        val preset = uiState.value.presets.getOrNull(index)
        // Skip if same preset is already selected (rotation re-fires LaunchedEffect)
        if (preset != null && preset.id == uiState.value.selectedPreset?.id) return

        // Convert target from mg/dL (storage) to user units (display) with proper rounding
        val targetInUserUnits = preset?.targetValue?.let {
            roundForDisplay(profileUtil.fromMgdlToUnits(it, units), units)
        } ?: roundForDisplay(profileUtil.fromMgdlToUnits(100.0, units), units)

        uiState.update {
            it.copy(
                selectedPreset = preset,
                editorName = preset?.name ?: "",
                editorTarget = targetInUserUnits,
                editorDuration = preset?.duration ?: (60L * 60L * 1000L),
                // Reset activation fields when switching presets
                eventTime = dateUtil.now(),
                eventTimeChanged = false,
                notes = ""
            )
        }
    }

    /**
     * Update editor name
     */
    fun updateEditorName(name: String) {
        uiState.update { it.copy(editorName = name) }
    }

    /**
     * Update editor target value (in mg/dL)
     */
    fun updateEditorTarget(target: Double) {
        uiState.update { it.copy(editorTarget = target) }
    }

    /**
     * Update editor duration (in milliseconds)
     */
    fun updateEditorDuration(duration: Long) {
        uiState.update { it.copy(editorDuration = duration) }
    }

    /**
     * Update event time for activation
     */
    fun updateEventTime(time: Long) {
        uiState.update {
            it.copy(
                eventTime = time,
                eventTimeChanged = true
            )
        }
    }

    /**
     * Update notes for activation
     */
    fun updateNotes(notes: String) {
        uiState.update { it.copy(notes = notes) }
    }

    /**
     * Save current editor values to the selected preset
     */
    fun saveCurrentPreset() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val selectedPreset = currentState.selectedPreset ?: return@launch
                val selectedPresetId = selectedPreset.id

                // Convert target from user units (display) to mg/dL (storage)
                val targetInMgdl = profileUtil.convertToMgdl(currentState.editorTarget, units)

                // Update the preset with current editor values
                val updatedPresets = currentState.presets.map { preset ->
                    if (preset.id == selectedPreset.id) {
                        preset.copy(
                            name = if (preset.nameRes == null) currentState.editorName else preset.name,
                            targetValue = targetInMgdl,
                            duration = currentState.editorDuration
                        )
                    } else {
                        preset
                    }
                }

                preferences.put(StringNonKey.TempTargetPresets, updatedPresets.toJson())

                // Update presets list and selected preset reference
                val reselectedPreset = updatedPresets.find { it.id == selectedPresetId }
                uiState.update {
                    it.copy(
                        presets = updatedPresets,
                        selectedPreset = reselectedPreset
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save preset", e)
            }
        }
    }

    /**
     * Get default values for a fixed preset from Constants.
     * Returns Pair(targetMgdl, durationMs) or null if not a fixed preset.
     */
    private fun getDefaultValuesForPreset(preset: TTPreset): Pair<Double, Long>? {
        if (preset.isDeletable) return null

        // Get defaults from Constants (target in mg/dL, duration in minutes)
        val (targetMgdl, durationMin) = when (preset.reason) {
            TT.Reason.EATING_SOON  -> Pair(
                Constants.DEFAULT_TT_EATING_SOON_TARGET,
                Constants.DEFAULT_TT_EATING_SOON_DURATION
            )

            TT.Reason.ACTIVITY     -> Pair(
                Constants.DEFAULT_TT_ACTIVITY_TARGET,
                Constants.DEFAULT_TT_ACTIVITY_DURATION
            )

            TT.Reason.HYPOGLYCEMIA -> Pair(
                Constants.DEFAULT_TT_HYPO_TARGET,
                Constants.DEFAULT_TT_HYPO_DURATION
            )

            else                   -> return null
        }

        val durationMs = durationMin.toLong() * 60L * 1000L
        return Pair(targetMgdl, durationMs)
    }

    /**
     * Check if current EDITOR values differ from default preference values.
     * Only applicable for fixed presets (non-deletable).
     * Evaluated on every editor change to update revert button visibility.
     */
    fun isEditorDifferentFromDefaults(): Boolean {
        val currentState = uiState.value
        val preset = currentState.selectedPreset ?: return false
        if (preset.isDeletable) return false

        val defaults = getDefaultValuesForPreset(preset) ?: return false
        val (defaultTargetMgdl, defaultDurationMs) = defaults

        // Convert current editor target (in user units) to mg/dL for comparison
        val editorTargetMgdl = profileUtil.convertToMgdl(currentState.editorTarget, units)

        // Compare with tolerance for floating point
        val targetDiffers = abs(editorTargetMgdl - defaultTargetMgdl) > 0.01
        val durationDiffers = currentState.editorDuration != defaultDurationMs

        return targetDiffers || durationDiffers
    }

    /**
     * Check if current EDITOR values differ from saved preset values.
     * Evaluated on every editor change to update save button visibility.
     */
    fun hasUnsavedChanges(): Boolean {
        val currentState = uiState.value
        val preset = currentState.selectedPreset ?: return false

        // Convert current editor target (in user units) to mg/dL for comparison
        val editorTargetMgdl = profileUtil.convertToMgdl(currentState.editorTarget, units)

        // Compare with tolerance for floating point
        val targetDiffers = abs(editorTargetMgdl - preset.targetValue) > 0.01
        val durationDiffers = currentState.editorDuration != preset.duration

        // For custom presets, also check name changes
        val nameDiffers = if (preset.nameRes == null) {
            currentState.editorName != (preset.name ?: "")
        } else false

        return targetDiffers || durationDiffers || nameDiffers
    }

    /**
     * Revert fixed preset (Eating Soon, Activity, Hypo) to default values from preferences
     */
    fun revertToDefaults() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val selectedPreset = currentState.selectedPreset ?: return@launch

                // Only allow revert for fixed presets (non-deletable)
                if (selectedPreset.isDeletable) return@launch

                val defaults = getDefaultValuesForPreset(selectedPreset) ?: return@launch
                val (defaultTargetMgdl, defaultDurationMs) = defaults

                // Update the preset with default values
                val updatedPresets = currentState.presets.map { preset ->
                    if (preset.id == selectedPreset.id) {
                        preset.copy(
                            targetValue = defaultTargetMgdl,
                            duration = defaultDurationMs
                        )
                    } else {
                        preset
                    }
                }

                preferences.put(StringNonKey.TempTargetPresets, updatedPresets.toJson())

                // Update presets list, selected preset, and editor fields with default values
                val reselectedPreset = updatedPresets.find { it.id == selectedPreset.id }
                val targetInUserUnits = roundForDisplay(profileUtil.fromMgdlToUnits(defaultTargetMgdl, units), units)

                uiState.update {
                    it.copy(
                        presets = updatedPresets,
                        selectedPreset = reselectedPreset,
                        editorTarget = targetInUserUnits,
                        editorDuration = defaultDurationMs
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to revert preset to defaults", e)
            }
        }
    }

    /**
     * Add a new custom preset
     */
    fun addNewPreset() {
        viewModelScope.launch {
            try {
                // Default target value: 100 mg/dL or equivalent in mmol/L
                val defaultTargetMgdl = 100.0

                val newPreset = TTPreset(
                    id = UUID.randomUUID().toString(),
                    name = "New Preset",
                    reason = TT.Reason.CUSTOM,
                    targetValue = defaultTargetMgdl, // Always store in mg/dL
                    duration = 60L * 60L * 1000L, // 60 minutes
                    isDeletable = true
                )

                val updatedPresets = uiState.value.presets + newPreset
                preferences.put(StringNonKey.TempTargetPresets, updatedPresets.toJson())
                loadData()

                // Select the new preset (will be last in list)
                val presetIndex = updatedPresets.size - 1
                selectPreset(presetIndex)

                // Scroll to new preset (account for standalone active TT card at position 0)
                val hasStandaloneActiveTT = uiState.value.activeTT != null && uiState.value.activePresetIndex == null
                val pageIndex = if (hasStandaloneActiveTT) presetIndex + 1 else presetIndex
                sideEffect.emit(SideEffect.ScrollToPreset(pageIndex))
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to add preset", e)
            }
        }
    }

    /**
     * Delete the currently selected preset (only if deletable)
     */
    fun deleteCurrentPreset() {
        viewModelScope.launch {
            try {
                val presetId = uiState.value.selectedPreset?.id ?: return@launch
                val updatedPresets = uiState.value.presets.filter { it.id != presetId }
                preferences.put(StringNonKey.TempTargetPresets, updatedPresets.toJson())
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete preset", e)
            }
        }
    }

    /**
     * Activate temp target with current editor values
     */
    fun activateWithEditorValues(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val timestamp = if (currentState.eventTimeChanged) {
                    currentState.eventTime
                } else {
                    dateUtil.now()
                }

                // Convert target from user units (display) to mg/dL (database storage)
                val targetMgdl = profileUtil.convertToMgdl(currentState.editorTarget, units)
                val durationMs = currentState.editorDuration
                val reason = currentState.selectedPreset?.reason ?: TT.Reason.CUSTOM
                val notes = currentState.notes.takeIf { it.isNotBlank() }

                // Create TT object
                val tempTarget = TT(
                    timestamp = timestamp,
                    duration = durationMs,
                    reason = reason,
                    lowTarget = targetMgdl,
                    highTarget = targetMgdl
                )

                persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                    temporaryTarget = tempTarget,
                    action = Action.TT,
                    source = Sources.TTDialog,
                    note = notes,
                    listValues = listOf(
                        ValueWithUnit.Mgdl(targetMgdl),
                        ValueWithUnit.Minute((durationMs / 60000L).toInt())
                    )
                )

                onSuccess()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to activate temp target", e)
            }
        }
    }

    /**
     * Cancel the currently active temp target
     */
    fun cancelActive() {
        viewModelScope.launch {
            try {
                persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                    timestamp = dateUtil.now(),
                    action = Action.CANCEL_TT,
                    source = Sources.TTDialog,
                    note = null,
                    listValues = emptyList()
                )

                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to cancel temp target", e)
            }
        }
    }
}
