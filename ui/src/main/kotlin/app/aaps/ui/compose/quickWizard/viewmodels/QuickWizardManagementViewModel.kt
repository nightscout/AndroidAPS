package app.aaps.ui.compose.quickWizard.viewmodels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.ui.events.EventQuickWizardChange
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * ViewModel for QuickWizardManagementScreen managing QuickWizard entries and editing.
 */
@HiltViewModel
@Stable
class QuickWizardManagementViewModel @Inject constructor(
    private val quickWizard: QuickWizard,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val constraintChecker: ConstraintsChecker,
    private val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    private val disposable = CompositeDisposable()

    private val _uiState = MutableStateFlow(QuickWizardManagementUiState())
    val uiState: StateFlow<QuickWizardManagementUiState> = _uiState.asStateFlow()

    /**
     * Get max carbs allowed from constraints
     */
    fun getMaxCarbs(): Double = constraintChecker.getMaxCarbsAllowed().value().toDouble()
    fun getMaxInsulin(): Double = constraintChecker.getMaxBolusAllowed().value().coerceAtLeast(25.0)

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect

    sealed class SideEffect {
        data class ScrollToEntry(val index: Int) : SideEffect()
    }

    fun setScreenMode(mode: ScreenMode) {
        _uiState.update { it.copy(screenMode = mode) }
    }

    init {
        loadData()
        observeQuickWizardChanges()
        observeQuickWizardPrefChanges()
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    /**
     * Load QuickWizard entries and preferences
     */
    fun loadData() {
        viewModelScope.launch {
            try {
                val entries = quickWizard.list()
                val showSuperBolus = preferences.get(BooleanKey.OverviewUseSuperBolus)
                val showWear = preferences.get(BooleanKey.WearControl)

                _uiState.update { current ->
                    // Preserve the current selection if valid (e.g. after a save on a non-first card);
                    // fall back to 0 on initial load (current.selectedIndex == -1) or if the list shrank.
                    val targetIndex = if (current.selectedIndex in entries.indices) current.selectedIndex else 0
                    val targetEntry = entries.getOrNull(targetIndex)
                    current.copy(
                        entries = entries,
                        selectedIndex = if (entries.isNotEmpty()) targetIndex else -1,
                        selectedGuid = targetEntry?.guid() ?: "",
                        currentCardIndex = if (current.currentCardIndex in entries.indices) current.currentCardIndex else 0,
                        showSuperBolusOption = showSuperBolus,
                        showWearOptions = showWear,
                        isLoading = false
                    ).let { state ->
                        if (targetEntry != null) loadEntryIntoEditor(state, targetEntry) else state
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load QuickWizard entries", e)
                _uiState.update { it.copy(isLoading = false) }
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to load entries", EventShowSnackbar.Type.Error))
            }
        }
    }

    /**
     * Observe QuickWizard changes from RxBus
     */
    private fun observeQuickWizardChanges() {
        disposable += rxBus
            .toObservable(EventQuickWizardChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           loadData()
                       }, { throwable ->
                           aapsLogger.error(LTag.UI, "Error observing QuickWizard changes", throwable)
                       })
    }

    /**
     * Observe QuickWizard changes that don't originate from this screen — edits on another screen or
     * a value synced from the main phone. [QuickWizard.changes] bumps after the domain cache reloads,
     * so [loadData] reads fresh entries (no race with the cache update). The local-edit path is still
     * covered by [EventQuickWizardChange] above.
     */
    private fun observeQuickWizardPrefChanges() {
        quickWizard.changes
            .drop(1) // skip initial value; loadData() in init already read the current entries
            .onEach { loadData() }
            .launchIn(viewModelScope)
    }

    /**
     * Load entry data into editor fields
     */
    private fun loadEntryIntoEditor(state: QuickWizardManagementUiState, entry: QuickWizardEntry): QuickWizardManagementUiState {
        return state.copy(
            editorMode = entry.mode(),
            editorButtonText = entry.buttonText(),
            editorInsulin = entry.insulin(),
            editorCarbs = entry.carbs(),
            editorCarbTime = entry.carbTime(),
            editorValidFrom = entry.validFrom(),
            editorValidTo = entry.validTo(),
            editorUseBG = entry.useBG() == QuickWizardEntry.YES,
            editorUseCOB = entry.useCOB() == QuickWizardEntry.YES,
            editorUseIOB = entry.useIOB() == QuickWizardEntry.YES,
            editorUsePositiveIOBOnly = entry.usePositiveIOBOnly() == QuickWizardEntry.YES,
            editorUseTrend = entry.useTrend().toTrendOption(),
            editorUseSuperBolus = entry.useSuperBolus() == QuickWizardEntry.YES,
            editorUseTempTarget = entry.useTempTarget() == QuickWizardEntry.YES,
            editorUseAlarm = entry.useAlarm() == QuickWizardEntry.YES,
            editorPercentage = entry.percentage(),
            editorDevicePhone = entry.device() == QuickWizardEntry.DEVICE_PHONE || entry.device() == QuickWizardEntry.DEVICE_ALL,
            editorDeviceWatch = entry.device() == QuickWizardEntry.DEVICE_WATCH || entry.device() == QuickWizardEntry.DEVICE_ALL,
            editorUseEcarbs = entry.useEcarbs() == QuickWizardEntry.YES,
            editorTime = entry.time(),
            editorDuration = entry.duration(),
            editorCarbs2 = entry.carbs2(),
            hasUnsavedChanges = false
        )
    }

    /**
     * Select entry at given index and load into editor
     */
    fun selectEntry(index: Int) {
        val currentState = uiState.value
        if (index < 0 || index >= currentState.entries.size) return

        val entry = currentState.entries[index]
        _uiState.update {
            loadEntryIntoEditor(it.copy(selectedIndex = index, selectedGuid = entry.guid()), entry)
        }
    }

    /**
     * Update current card index (for carousel state persistence)
     */
    fun updateCurrentCardIndex(index: Int) {
        _uiState.update { it.copy(currentCardIndex = index) }
    }

    /**
     * Check if current editor has unsaved changes
     */
    fun hasUnsavedChanges(): Boolean = uiState.value.hasUnsavedChanges

    // ---------------------------------------------------------------------------------------------
    // Reorder ("sort") mode
    // ---------------------------------------------------------------------------------------------

    /**
     * Working order while reorder mode is on, as `value[position] == originalIndex`; null when the
     * mode is off — which is also the flag the screen uses to decide it is sorting.
     */
    private val _reorderOrder = MutableStateFlow<List<Int>?>(null)
    val reorderOrder: StateFlow<List<Int>?> = _reorderOrder.asStateFlow()

    /** Entry guids as they were when the mode was entered. See [commitReorder]. */
    private var reorderGuids: List<String> = emptyList()

    /** Original index of the entry the carousel re-centres on: the last one moved, else the selected one. */
    private var reorderAnchor: Int = 0

    /** Nothing to rearrange with a single entry. */
    fun canReorder(): Boolean = uiState.value.entries.size > 1

    fun enterReorderMode() {
        val entries = uiState.value.entries
        if (entries.size < 2) return
        reorderGuids = entries.map { it.guid() }
        reorderAnchor = uiState.value.selectedIndex.coerceIn(0, entries.size - 1)
        _reorderOrder.value = List(entries.size) { it }
    }

    fun cancelReorder() {
        _reorderOrder.value = null
    }

    /**
     * Apply one move as remove-then-insert, not a swap, so a non-adjacent move shifts the items in
     * between rather than scrambling them.
     *
     * @return true if the order changed. The carousel follows the moved card only on true.
     */
    fun moveReorderItem(from: Int, to: Int): Boolean {
        val current = _reorderOrder.value ?: return false
        if (from == to || from !in current.indices || to !in current.indices) return false
        reorderAnchor = current[from]
        _reorderOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    /**
     * Persist the working order and leave reorder mode.
     *
     * Commits once, here — not per move — because the entry list is a single JSON blob on a
     * bidirectionally synced preference.
     *
     * Note the write makes [QuickWizard] reload, which repopulates the editor from whichever entry
     * ends up selected — so unsaved editor changes do not survive a commit, exactly as they do not
     * survive an entry list synced in from the main phone.
     *
     * @return the card index to settle on, or null if the commit was abandoned because the entry
     *         list changed underneath us.
     */
    suspend fun commitReorder(): Int? {
        val order = _reorderOrder.value ?: return null
        // Resolved for both branches, so an undone move lands on the same card a committed one would.
        val anchorPosition = order.indexOf(reorderAnchor).takeIf { it >= 0 } ?: 0
        if (order.withIndex().all { (position, original) -> position == original }) {
            _reorderOrder.value = null
            return anchorPosition
        }
        // The order is a list of indices, so it only means anything against the list it came from.
        if (uiState.value.entries.map { it.guid() } != reorderGuids) return failReorder()
        val expectedGuids = order.map { reorderGuids[it] }
        return try {
            val applied = withContext(Dispatchers.IO) { quickWizard.reorder(order) }
            if (!applied) return failReorder()
            rxBus.send(EventQuickWizardChange())
            // Hold the working order until the reloaded list carries the new arrangement, otherwise
            // the cards would render the pre-sort order at post-sort positions for a frame.
            withTimeoutOrNull(ENTRY_RELOAD_TIMEOUT_MS) {
                uiState.first { state -> state.entries.map { it.guid() } == expectedGuids }
            }
            _reorderOrder.value = null
            // The reload preserves the selection by position, which now points at a different entry.
            selectEntry(anchorPosition)
            updateCurrentCardIndex(anchorPosition)
            anchorPosition
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to persist reordered QuickWizard entries", e)
            failReorder()
        }
    }

    private fun failReorder(): Int? {
        _reorderOrder.value = null
        rxBus.send(
            EventShowSnackbar(
                rh.gs(app.aaps.core.ui.R.string.presets_changed_reorder_aborted),
                EventShowSnackbar.Type.Error
            )
        )
        return null
    }

    /**
     * Save current entry
     */
    fun saveCurrentEntry() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val index = currentState.selectedIndex
                if (index < 0 || index >= currentState.entries.size) return@launch

                val entry = currentState.entries[index]

                // Validate inputs based on mode
                val mode = currentState.editorMode
                when (mode) {
                    QuickWizardMode.WIZARD, QuickWizardMode.CARBS -> {
                        if (currentState.editorCarbs <= 0 && (!currentState.editorUseEcarbs || currentState.editorCarbs2 <= 0)) {
                            rxBus.send(EventShowSnackbar("Carbs must be greater than 0", EventShowSnackbar.Type.Error))
                            return@launch
                        }
                    }

                    QuickWizardMode.INSULIN                       -> {
                        if (currentState.editorInsulin <= 0.0) {
                            rxBus.send(EventShowSnackbar("Insulin must be greater than 0", EventShowSnackbar.Type.Error))
                            return@launch
                        }
                    }
                }

                // Update entry storage
                entry.storage.put("mode", mode.value)
                entry.storage.put("buttonText", currentState.editorButtonText)
                entry.storage.put("insulin", currentState.editorInsulin)
                entry.storage.put("carbs", currentState.editorCarbs)
                entry.storage.put("carbTime", currentState.editorCarbTime)
                entry.storage.put("useAlarm", booleanToRadioNumber(currentState.editorUseAlarm))
                entry.storage.put("validFrom", currentState.editorValidFrom)
                entry.storage.put("validTo", currentState.editorValidTo)
                entry.storage.put("useBG", booleanToRadioNumber(currentState.editorUseBG))
                entry.storage.put("useCOB", booleanToRadioNumber(currentState.editorUseCOB))
                entry.storage.put("useIOB", booleanToRadioNumber(currentState.editorUseIOB))
                entry.storage.put("usePositiveIOBOnly", booleanToRadioNumber(currentState.editorUsePositiveIOBOnly))
                entry.storage.put("useTrend", currentState.editorUseTrend.toInt())
                entry.storage.put("useSuperBolus", booleanToRadioNumber(currentState.editorUseSuperBolus))
                entry.storage.put("useTempTarget", booleanToRadioNumber(currentState.editorUseTempTarget))
                entry.storage.put("percentage", currentState.editorPercentage)

                // Device selection
                val device = when {
                    currentState.editorDevicePhone && currentState.editorDeviceWatch -> QuickWizardEntry.DEVICE_ALL
                    currentState.editorDevicePhone                                   -> QuickWizardEntry.DEVICE_PHONE
                    currentState.editorDeviceWatch                                   -> QuickWizardEntry.DEVICE_WATCH
                    else                                                             -> QuickWizardEntry.DEVICE_ALL
                }
                entry.storage.put("device", device)

                // eCarbs
                entry.storage.put("useEcarbs", booleanToRadioNumber(currentState.editorUseEcarbs))
                entry.storage.put("time", currentState.editorTime)
                entry.storage.put("duration", currentState.editorDuration)
                entry.storage.put("carbs2", currentState.editorCarbs2)

                quickWizard.addOrUpdate(entry)
                rxBus.send(EventQuickWizardChange())

                // Synchronously refresh the entries list so the carousel shows the saved mode
                // immediately, without waiting for the async RxBus → loadData() path.
                val updatedEntries = quickWizard.list()
                _uiState.update { state ->
                    val savedEntry = updatedEntries.getOrNull(index)
                    val base = state.copy(entries = updatedEntries, hasUnsavedChanges = false)
                    if (savedEntry != null) loadEntryIntoEditor(base, savedEntry) else base
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save QuickWizard entry", e)
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to save entry", EventShowSnackbar.Type.Error))
            }
        }
    }

    /**
     * Add new empty entry
     */
    fun addNewEntry() {
        viewModelScope.launch {
            try {
                val newEntry = quickWizard.newEmptyItem()
                newEntry.storage.put("buttonText", "")
                newEntry.storage.put("carbs", 0)
                newEntry.storage.put("validFrom", 0)
                newEntry.storage.put("validTo", 86340)

                quickWizard.addOrUpdate(newEntry)
                rxBus.send(EventQuickWizardChange())

                // Scroll to new entry (will be last)
                val newIndex = quickWizard.size() - 1
                _sideEffect.emit(SideEffect.ScrollToEntry(newIndex))
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to add QuickWizard entry", e)
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to add entry", EventShowSnackbar.Type.Error))
            }
        }
    }

    /**
     * Clone/duplicate current entry
     */
    fun cloneCurrentEntry() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val index = currentState.selectedIndex
                if (index < 0 || index >= currentState.entries.size) return@launch

                val sourceEntry = currentState.entries[index]
                val newEntry = quickWizard.newEmptyItem()

                // Copy all fields from source entry
                newEntry.storage.put("mode", sourceEntry.mode().value)
                newEntry.storage.put("buttonText", sourceEntry.buttonText() + " (Copy)")
                newEntry.storage.put("insulin", sourceEntry.insulin())
                newEntry.storage.put("carbs", sourceEntry.carbs())
                newEntry.storage.put("carbTime", sourceEntry.carbTime())
                newEntry.storage.put("useAlarm", sourceEntry.useAlarm())
                newEntry.storage.put("validFrom", sourceEntry.validFrom())
                newEntry.storage.put("validTo", sourceEntry.validTo())
                newEntry.storage.put("useBG", sourceEntry.useBG())
                newEntry.storage.put("useCOB", sourceEntry.useCOB())
                newEntry.storage.put("useIOB", sourceEntry.useIOB())
                newEntry.storage.put("usePositiveIOBOnly", sourceEntry.usePositiveIOBOnly())
                newEntry.storage.put("useTrend", sourceEntry.useTrend())
                newEntry.storage.put("useSuperBolus", sourceEntry.useSuperBolus())
                newEntry.storage.put("useTempTarget", sourceEntry.useTempTarget())
                newEntry.storage.put("percentage", sourceEntry.percentage())
                newEntry.storage.put("device", sourceEntry.device())
                newEntry.storage.put("useEcarbs", sourceEntry.useEcarbs())
                newEntry.storage.put("time", sourceEntry.time())
                newEntry.storage.put("duration", sourceEntry.duration())
                newEntry.storage.put("carbs2", sourceEntry.carbs2())

                quickWizard.addOrUpdate(newEntry)
                rxBus.send(EventQuickWizardChange())

                // Scroll to new entry (will be last)
                val newIndex = quickWizard.size() - 1
                _sideEffect.emit(SideEffect.ScrollToEntry(newIndex))
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to clone QuickWizard entry", e)
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to clone entry", EventShowSnackbar.Type.Error))
            }
        }
    }

    /**
     * Delete current entry
     */
    fun deleteCurrentEntry() {
        viewModelScope.launch {
            try {
                val currentState = uiState.value
                val index = currentState.selectedIndex
                if (index < 0 || index >= currentState.entries.size) return@launch

                quickWizard.remove(index)
                rxBus.send(EventQuickWizardChange())
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete QuickWizard entry", e)
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to delete entry", EventShowSnackbar.Type.Error))
            }
        }
    }

    // Editor field update functions
    fun updateMode(value: QuickWizardMode) {
        _uiState.update { it.copy(editorMode = value, hasUnsavedChanges = true) }
    }

    fun updateButtonText(value: String) {
        _uiState.update { it.copy(editorButtonText = value, hasUnsavedChanges = true) }
    }

    fun updateInsulin(value: Double) {
        _uiState.update { it.copy(editorInsulin = value, hasUnsavedChanges = true) }
    }

    fun updateCarbs(value: Int) {
        _uiState.update { it.copy(editorCarbs = value, hasUnsavedChanges = true) }
    }

    fun updateCarbTime(value: Int) {
        _uiState.update {
            it.copy(
                editorCarbTime = value,
                editorUseAlarm = value > 0,  // Auto-enable alarm if carb time > 0
                hasUnsavedChanges = true
            )
        }
    }

    fun updateValidFrom(value: Int) {
        _uiState.update { it.copy(editorValidFrom = value, hasUnsavedChanges = true) }
    }

    fun updateValidTo(value: Int) {
        _uiState.update { it.copy(editorValidTo = value, hasUnsavedChanges = true) }
    }

    fun updateUseBG(value: Boolean) {
        _uiState.update { it.copy(editorUseBG = value, hasUnsavedChanges = true) }
    }

    fun updateUseCOB(value: Boolean) {
        _uiState.update {
            it.copy(
                editorUseCOB = value,
                editorUseIOB = if (value) true else it.editorUseIOB,  // Auto-enable IOB if COB enabled
                hasUnsavedChanges = true
            )
        }
    }

    fun updateUseIOB(value: Boolean) {
        _uiState.update {
            it.copy(
                editorUseIOB = value,
                editorUseCOB = if (!value) false else it.editorUseCOB,  // Auto-disable COB if IOB disabled
                editorUsePositiveIOBOnly = if (!value) false else it.editorUsePositiveIOBOnly,  // Auto-disable positive IOB only
                hasUnsavedChanges = true
            )
        }
    }

    fun updateUsePositiveIOBOnly(value: Boolean) {
        _uiState.update { it.copy(editorUsePositiveIOBOnly = value, hasUnsavedChanges = true) }
    }

    fun updateUseTrend(value: TrendOption) {
        _uiState.update { it.copy(editorUseTrend = value, hasUnsavedChanges = true) }
    }

    fun updateUseSuperBolus(value: Boolean) {
        _uiState.update { it.copy(editorUseSuperBolus = value, hasUnsavedChanges = true) }
    }

    fun updateUseTempTarget(value: Boolean) {
        _uiState.update { it.copy(editorUseTempTarget = value, hasUnsavedChanges = true) }
    }

    fun updateUseAlarm(value: Boolean) {
        _uiState.update { it.copy(editorUseAlarm = value, hasUnsavedChanges = true) }
    }

    fun updatePercentage(value: Int) {
        _uiState.update { it.copy(editorPercentage = value, hasUnsavedChanges = true) }
    }

    fun updateDevicePhone(value: Boolean) {
        _uiState.update { it.copy(editorDevicePhone = value, hasUnsavedChanges = true) }
    }

    fun updateDeviceWatch(value: Boolean) {
        _uiState.update { it.copy(editorDeviceWatch = value, hasUnsavedChanges = true) }
    }

    fun updateUseEcarbs(value: Boolean) {
        _uiState.update { it.copy(editorUseEcarbs = value, hasUnsavedChanges = true) }
    }

    fun updateTime(value: Int) {
        _uiState.update { it.copy(editorTime = value, hasUnsavedChanges = true) }
    }

    fun updateDuration(value: Int) {
        _uiState.update { it.copy(editorDuration = value, hasUnsavedChanges = true) }
    }

    fun updateCarbs2(value: Int) {
        _uiState.update { it.copy(editorCarbs2 = value, hasUnsavedChanges = true) }
    }

    /**
     * Convert boolean to QuickWizardEntry radio number (0 = true/YES, 1 = false/NO)
     */
    private fun booleanToRadioNumber(value: Boolean): Int = if (value) 0 else 1

    companion object {

        /**
         * How long [commitReorder] waits for the reloaded entry list to carry the new order before
         * leaving reorder mode anyway. A safety net only — the reload normally lands in a frame or
         * two, and this exists so the mode cannot get stuck.
         */
        private const val ENTRY_RELOAD_TIMEOUT_MS = 1_000L
    }
}
