package app.aaps.ui.compose.tempTarget

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.clientcontrol.failText
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.navigation.icon
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ViewModel for TempTargetManagementScreen managing TT presets and activation.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class TempTargetManagementViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val batchExecutor: BatchExecutor,
    private val config: Config,
    @ApplicationScope private val appScope: CoroutineScope
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

    private val _uiState = MutableStateFlow(TempTargetManagementUiState())
    val uiState: StateFlow<TempTargetManagementUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ScrollToPreset(val index: Int) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,                          // Don't replay to new collectors
        extraBufferCapacity = 1,             // Buffer one event if no collector
        onBufferOverflow = BufferOverflow.DROP_OLDEST  // Drop old events
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    fun setScreenMode(mode: ScreenMode) {
        _uiState.update { it.copy(screenMode = mode) }
    }

    init {
        loadData()
        observeTempTargetChanges()
        observePresetChanges()
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

                // Standalone active TT (no matching preset) -> selectedPreset = null, editor mirrors active TT
                val hasStandaloneActiveTT = activeTT != null && activePresetIndex == null
                val initialPreset = when {
                    hasStandaloneActiveTT -> null
                    else                  -> activePresetIndex?.let { presets.getOrNull(it) } ?: presets.firstOrNull()
                }

                // If active TT shown (standalone or matched preset), mirror its values; else use preset values
                val initialTargetMgdl = activeTT?.lowTarget ?: initialPreset?.targetValue ?: 100.0
                val initialDurationMs = activeTT?.duration ?: initialPreset?.duration ?: (60L * 60L * 1000L)

                // Convert target from mg/dL (storage) to user units (display) with proper rounding
                val targetInUserUnits = roundForDisplay(profileUtil.fromMgdlToUnits(initialTargetMgdl, units), units)

                _uiState.update {
                    it.copy(
                        activeTT = activeTT,
                        activePresetIndex = activePresetIndex,
                        currentCardIndex = activePresetIndex ?: 0,
                        remainingTimeMs = remainingTime,
                        presets = presets,
                        selectedPreset = initialPreset,
                        editorName = initialPreset?.name ?: "",
                        editorTarget = targetInUserUnits,
                        editorDuration = initialDurationMs,
                        showNotesField = showNotes,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load temp target presets", e)
                _uiState.update { it.copy(isLoading = false) }
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to load presets", EventShowSnackbar.Type.Error))
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

                _uiState.update {
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
     * Subscribe to TempTargetPresets changes (edited elsewhere or synced from the master phone).
     * Soft refresh: updates the preset cards + active TT without resetting the editor, so an
     * incoming change doesn't clobber the user's in-progress edits. Reads only — no echo/loop.
     */
    private fun observePresetChanges() {
        preferences
            .observe(StringNonKey.TempTargetPresets)
            .drop(1) // skip initial replay; loadData() in init already read the current value
            .onEach { refreshData() }
            .launchIn(viewModelScope)
    }

    /**
     * Update the current card index (pager position) in UI state.
     * Used to preserve pager position across rotation.
     */
    fun updateCurrentCardIndex(index: Int) {
        _uiState.update { it.copy(currentCardIndex = index) }
    }

    // ---------------------------------------------------------------------------------------------
    // Reorder ("sort") mode
    // ---------------------------------------------------------------------------------------------

    /**
     * Working order while reorder mode is on, as `value[position] == originalPresetIndex`; null when
     * the mode is off — which is also the flag the screen uses to decide it is sorting.
     *
     * Indices are into the **preset** list, not carousel pages: page 0 is the standalone active-TT
     * card when there is one, and that card is not a preset at all.
     */
    private val _reorderOrder = MutableStateFlow<List<Int>?>(null)
    val reorderOrder: StateFlow<List<Int>?> = _reorderOrder.asStateFlow()

    /** Presets as they were when the mode was entered. See [commitReorder]. */
    private var reorderPresets: List<TTPreset> = emptyList()

    /**
     * Original preset index of the card the carousel re-centres on when the mode is left: the last
     * one moved, or the selected preset if nothing was moved.
     */
    private var reorderAnchor: Int = 0

    /**
     * True when reordering is possible at all: at least two presets, and at least two of them
     * movable. With only the three built-ins there is nothing a user could rearrange.
     */
    fun canReorder(): Boolean = uiState.value.presets.count { it.isDeletable } > 1

    fun enterReorderMode() {
        val presets = uiState.value.presets
        if (presets.size < 2) return
        reorderPresets = presets
        reorderAnchor = uiState.value.selectedPreset
            ?.let { selected -> presets.indexOfFirst { it.id == selected.id } }
            ?.takeIf { it >= 0 }
            ?: presets.indexOfFirst { it.isDeletable }.coerceAtLeast(0)
        _reorderOrder.value = List(presets.size) { it }
    }

    fun cancelReorder() {
        _reorderOrder.value = null
    }

    /**
     * Whether the preset currently sitting at [position] may change position.
     *
     * The built-in eating-soon / activity / hypo presets keep their places, so they are the ones
     * that cannot move. Keyed on [TTPreset.isDeletable] rather than "the first three" because that
     * is what actually distinguishes them — a fourth built-in would be covered automatically, and a
     * custom preset that happens to share a reason would not be wrongly pinned.
     *
     * Read against the entry-time snapshot, so a preset list refreshed mid-session cannot un-pin
     * anything half way through.
     */
    fun isReorderPositionMovable(position: Int): Boolean {
        val order = _reorderOrder.value ?: return false
        val original = order.getOrNull(position) ?: return false
        return reorderPresets.getOrNull(original)?.isDeletable == true
    }

    /**
     * Apply one move as remove-then-insert, not a swap, so a non-adjacent move shifts the items in
     * between rather than scrambling them.
     *
     * Both ends must be movable: a pinned preset can neither be moved nor displaced. The carousel
     * already hides those moves, but this is the source of truth.
     *
     * @return true if the order changed. The carousel follows the moved card only on true.
     */
    fun moveReorderItem(from: Int, to: Int): Boolean {
        val current = _reorderOrder.value ?: return false
        if (from == to || from !in current.indices || to !in current.indices) return false
        if (!isReorderPositionMovable(from) || !isReorderPositionMovable(to)) return false
        reorderAnchor = current[from]
        _reorderOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    /**
     * Persist the working order and leave reorder mode.
     *
     * Commits once, here — not per move — because the preset list is stored as a single JSON blob on
     * a bidirectionally synced preference, so every write is a full round trip to the other device.
     *
     * @return the **card index** to settle on (preset position plus the standalone active-TT card,
     *         if present), or null if the commit was abandoned because the preset list changed
     *         underneath us.
     */
    suspend fun commitReorder(): Int? {
        val order = _reorderOrder.value ?: return null
        // Resolved for both branches, so an undone move lands on the same card a committed one would.
        val anchorPosition = order.indexOf(reorderAnchor).takeIf { it >= 0 } ?: 0
        // Nothing moved — skip the write entirely so leaving the mode is free.
        if (order.withIndex().all { (position, original) -> position == original }) {
            _reorderOrder.value = null
            return cardIndexOf(anchorPosition)
        }
        // The order is a list of indices, so it only means anything against the list it came from.
        // A same-length replacement (presets synced in from the master) would otherwise be silently
        // rearranged into an order the user never chose.
        if (uiState.value.presets.map { it.id } != reorderPresets.map { it.id }) {
            _reorderOrder.value = null
            rxBus.send(
                EventShowSnackbar(
                    rh.gs(app.aaps.core.ui.R.string.presets_changed_reorder_aborted),
                    EventShowSnackbar.Type.Error
                )
            )
            return null
        }
        val reordered = order.map { reorderPresets[it] }
        val expectedIds = reordered.map { it.id }
        return try {
            withContext(Dispatchers.IO) {
                preferences.put(StringNonKey.TempTargetPresets, reordered.toJson())
            }
            // Hold the working order until the reloaded state carries the new arrangement — clearing
            // immediately would render the pre-sort order at post-sort positions for a frame.
            withTimeoutOrNull(PRESET_RELOAD_TIMEOUT_MS) {
                uiState.first { state -> state.presets.map { it.id } == expectedIds }
            }
            _reorderOrder.value = null
            val settleOn = cardIndexOf(anchorPosition)
            updateCurrentCardIndex(settleOn)
            settleOn
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to persist reordered temp target presets", e)
            _reorderOrder.value = null
            rxBus.send(EventShowSnackbar(e.message ?: "Failed to save presets", EventShowSnackbar.Type.Error))
            null
        }
    }

    /** Carousel page showing the preset at [presetPosition], accounting for the standalone active card. */
    private fun cardIndexOf(presetPosition: Int): Int {
        val state = uiState.value
        val hasStandaloneActiveTT = state.activeTT != null && state.activePresetIndex == null
        return if (hasStandaloneActiveTT) presetPosition + 1 else presetPosition
    }

    /**
     * Select a preset by index and populate editor fields.
     * Skips if the same preset is already selected (e.g., after rotation).
     */
    fun selectPreset(index: Int) {
        val preset = uiState.value.presets.getOrNull(index)
        // Skip if same preset is already selected (rotation re-fires LaunchedEffect)
        if (preset != null && preset.id == uiState.value.selectedPreset?.id) return

        // If this card is the active preset, mirror the running TT's values
        val activeTT = uiState.value.activeTT
        val isActive = activeTT != null && index == uiState.value.activePresetIndex
        val targetMgdl = if (isActive) activeTT.lowTarget else preset?.targetValue ?: 100.0
        val durationMs = if (isActive) activeTT.duration else preset?.duration ?: (60L * 60L * 1000L)
        val targetInUserUnits = roundForDisplay(profileUtil.fromMgdlToUnits(targetMgdl, units), units)

        _uiState.update {
            it.copy(
                selectedPreset = preset,
                editorName = preset?.name ?: "",
                editorTarget = targetInUserUnits,
                editorDuration = durationMs,
                // Reset activation fields when switching presets
                eventTime = dateUtil.now(),
                eventTimeChanged = false,
                notes = ""
            )
        }
    }

    /**
     * Populate editor fields from the currently active TT (for the standalone active card
     * that has no matching preset). No-op when no active TT or already showing it.
     */
    fun selectActiveTT() {
        val tt = uiState.value.activeTT ?: return
        if (uiState.value.selectedPreset == null) return // already on active card
        val targetInUserUnits = roundForDisplay(profileUtil.fromMgdlToUnits(tt.lowTarget, units), units)
        _uiState.update {
            it.copy(
                selectedPreset = null,
                editorName = "",
                editorTarget = targetInUserUnits,
                editorDuration = tt.duration,
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
        _uiState.update { it.copy(editorName = name) }
    }

    /**
     * Update editor target value (in mg/dL)
     */
    fun updateEditorTarget(target: Double) {
        _uiState.update { it.copy(editorTarget = target) }
    }

    /**
     * Update editor duration (in milliseconds)
     */
    fun updateEditorDuration(duration: Long) {
        _uiState.update { it.copy(editorDuration = duration) }
    }

    /**
     * Update event time for activation
     */
    fun updateEventTime(time: Long) {
        _uiState.update {
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
        _uiState.update { it.copy(notes = notes) }
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
                _uiState.update {
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
            TT.Reason.EATING_SOON -> Pair(
                Constants.DEFAULT_TT_EATING_SOON_TARGET,
                Constants.DEFAULT_TT_EATING_SOON_DURATION
            )

            TT.Reason.ACTIVITY -> Pair(
                Constants.DEFAULT_TT_ACTIVITY_TARGET,
                Constants.DEFAULT_TT_ACTIVITY_DURATION
            )

            TT.Reason.HYPOGLYCEMIA -> Pair(
                Constants.DEFAULT_TT_HYPO_TARGET,
                Constants.DEFAULT_TT_HYPO_DURATION
            )

            else -> return null
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

        val targetDiffers = editorTargetDiffers(defaultTargetMgdl, currentState.editorTarget)
        val durationDiffers = currentState.editorDuration != defaultDurationMs

        return targetDiffers || durationDiffers
    }

    /**
     * Whether the editor's target differs from [storedMgdl] — compared at the precision the user can
     * actually see.
     *
     * The editor holds a value that has already been rounded for display (0.1 mmol/L, or 1 mg/dL),
     * so converting it back to mg/dL and comparing against full-precision storage reports a change
     * nobody made: 90 mg/dL displays as 5.0 mmol/L, which converts back to 90.08 mg/dL. Round the
     * stored value the same way instead, and compare in display units.
     */
    private fun editorTargetDiffers(storedMgdl: Double, editorTarget: Double): Boolean {
        val storedForDisplay = roundForDisplay(profileUtil.fromMgdlToUnits(storedMgdl, units), units)
        return abs(editorTarget - storedForDisplay) > DISPLAY_TARGET_EPSILON
    }

    /**
     * Check if current EDITOR values differ from saved preset values.
     * Evaluated on every editor change to update save button visibility.
     */
    fun hasUnsavedChanges(): Boolean {
        val currentState = uiState.value
        val preset = currentState.selectedPreset ?: return false

        val targetDiffers = editorTargetDiffers(preset.targetValue, currentState.editorTarget)
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

                _uiState.update {
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
                _sideEffect.emit(SideEffect.ScrollToPreset(pageIndex))
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
     * Activate temp target with current editor values — the bolus-style two-step: contact the master (shows the
     * pending modal), render the MASTER's confirmation lines, and commit on the user's OK. The client never confirms
     * locally; a contact failure is visible (no confirmation dialog appears — the error shows instead). A back-dated
     * event time rides as startOffsetMinutes. The PREPARE rides viewModelScope (abandoned if you leave the screen); the
     * COMMIT rides appScope (survives a screen pop) and the back-navigation hops to Main (NavController is main-thread-only).
     */
    fun activateWithEditorValues(onSuccess: () -> Unit) {
        val currentState = uiState.value
        val timestamp = if (currentState.eventTimeChanged) currentState.eventTime else dateUtil.now()
        // Convert target from user units (display) to mg/dL (database storage)
        val targetMgdl = profileUtil.convertToMgdl(currentState.editorTarget, units)
        val durationMinutes = (currentState.editorDuration / 60000L).toInt()
        val reason = currentState.selectedPreset?.reason ?: TT.Reason.CUSTOM
        val notes = currentState.notes.takeIf { it.isNotBlank() }
        val label = rh.gs(app.aaps.core.ui.R.string.clientcontrol_action_set_temp_target)
        val startOffsetMinutes = ((timestamp - dateUtil.now()) / 60000L).toInt()
        val actions = listOf(BatchAction.TempTarget(reason.text, targetMgdl, targetMgdl, durationMinutes, startOffsetMinutes, notes))
        viewModelScope.launch {
            when (val prepared = batchExecutor.prepare(actions, Sources.TTDialog, label)) {
                is ActionProgress.Prepared ->
                    rxBus.send(
                        EventShowDialog.OkCancel(
                            title = rh.gs(app.aaps.core.ui.R.string.temporary_target), message = "", confirmationLines = prepared.lines,
                            icon = ElementType.TEMP_TARGET_MANAGEMENT.icon(),
                            onOk = {
                                appScope.launch {
                                    if (batchExecutor.commit(prepared.id, Sources.TTDialog, label) is ActionProgress.Applied) {
                                        if (durationMinutes == 10) preferences.put(BooleanNonKey.ObjectivesTempTargetUsed, true)
                                        withContext(Dispatchers.Main) { onSuccess() }
                                    }
                                }
                            }
                        )
                    )
                // Master-local failure (no modal) or a client offline pre-check; a client round-trip failure already showed on the app modal.
                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.temporary_target), message = prepared.detail ?: rh.gs(prepared.reason.failText())))

                else                       -> Unit
            }
        }
    }

    /**
     * Cancel the currently active temp target — a duration-0 [BatchAction.TempTarget]. Same bolus-style flow:
     * contact the master, show the master's "cancel" confirmation, commit on OK.
     * [onSuccess] is invoked on the main thread ONLY after the user confirms and the cancel actually commits
     * (ActionProgress.Applied) — so the caller can close the screen on a real cancel, not when the dialog appears.
     */
    fun cancelActive(onSuccess: () -> Unit) {
        val label = rh.gs(app.aaps.core.ui.R.string.clientcontrol_action_cancel_temp_target)
        val actions = listOf(BatchAction.TempTarget(TT.Reason.CUSTOM.text, 0.0, 0.0, 0, 0))
        viewModelScope.launch {
            when (val prepared = batchExecutor.prepare(actions, Sources.TTDialog, label)) {
                is ActionProgress.Prepared ->
                    rxBus.send(
                        EventShowDialog.OkCancel(
                            title = rh.gs(app.aaps.core.ui.R.string.temporary_target), message = "", confirmationLines = prepared.lines,
                            icon = ElementType.TEMP_TARGET_MANAGEMENT.icon(),
                            onOk = {
                                appScope.launch {
                                    if (batchExecutor.commit(prepared.id, Sources.TTDialog, label) is ActionProgress.Applied)
                                        withContext(Dispatchers.Main) { onSuccess() }
                                }
                            }
                        )
                    )

                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.temporary_target), message = prepared.detail ?: rh.gs(prepared.reason.failText())))

                else                       -> Unit
            }
        }
    }

    companion object {

        /**
         * How long [commitReorder] waits for the reloaded preset list to carry the new order before
         * leaving reorder mode anyway. A safety net only — the preference observer normally refreshes
         * within a frame or two, and this exists so the mode cannot get stuck.
         */
        private const val PRESET_RELOAD_TIMEOUT_MS = 1_000L

        /**
         * Float-noise tolerance for comparing two targets already expressed in display units. Well
         * below the smallest step the user can enter (0.1 mmol/L, 1 mg/dL), so it only absorbs
         * conversion error — never a real edit.
         */
        private const val DISPLAY_TARGET_EPSILON = 0.001
    }
}
