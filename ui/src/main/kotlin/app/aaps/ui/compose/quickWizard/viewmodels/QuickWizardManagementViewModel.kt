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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.ui.events.EventQuickWizardChange
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    val uiState: StateFlow<QuickWizardManagementUiState>
        field = MutableStateFlow(QuickWizardManagementUiState())

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
        uiState.update { it.copy(screenMode = mode) }
    }

    init {
        loadData()
        observeQuickWizardChanges()
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

                // Select first entry if exists
                val firstEntry = entries.firstOrNull()

                uiState.update {
                    it.copy(
                        entries = entries,
                        selectedIndex = if (entries.isNotEmpty()) 0 else -1,
                        selectedGuid = firstEntry?.guid() ?: "",
                        currentCardIndex = 0,
                        showSuperBolusOption = showSuperBolus,
                        showWearOptions = showWear,
                        isLoading = false,
                        snackbarMessage = null
                    ).let { state ->
                        // Load first entry into editor if exists
                        if (firstEntry != null) {
                            loadEntryIntoEditor(state, firstEntry)
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load QuickWizard entries", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to load entries")
                    )
                }
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
        uiState.update {
            loadEntryIntoEditor(it.copy(selectedIndex = index, selectedGuid = entry.guid()), entry)
        }
    }

    /**
     * Update current card index (for carousel state persistence)
     */
    fun updateCurrentCardIndex(index: Int) {
        uiState.update { it.copy(currentCardIndex = index) }
    }

    /**
     * Check if current editor has unsaved changes
     */
    fun hasUnsavedChanges(): Boolean = uiState.value.hasUnsavedChanges

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
                            uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error("Carbs must be greater than 0")) }
                            return@launch
                        }
                    }

                    QuickWizardMode.INSULIN                       -> {
                        if (currentState.editorInsulin <= 0.0) {
                            uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error("Insulin must be greater than 0")) }
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

                uiState.update { it.copy(hasUnsavedChanges = false) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save QuickWizard entry", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to save entry")) }
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
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to add entry")) }
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
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to clone entry")) }
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
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to delete entry")) }
            }
        }
    }

    /**
     * Clear snackbar message
     */
    fun clearSnackbar() {
        uiState.update { it.copy(snackbarMessage = null) }
    }

    // Editor field update functions
    fun updateMode(value: QuickWizardMode) {
        uiState.update { it.copy(editorMode = value, hasUnsavedChanges = true) }
    }

    fun updateButtonText(value: String) {
        uiState.update { it.copy(editorButtonText = value, hasUnsavedChanges = true) }
    }

    fun updateInsulin(value: Double) {
        uiState.update { it.copy(editorInsulin = value, hasUnsavedChanges = true) }
    }

    fun updateCarbs(value: Int) {
        uiState.update { it.copy(editorCarbs = value, hasUnsavedChanges = true) }
    }

    fun updateCarbTime(value: Int) {
        uiState.update {
            it.copy(
                editorCarbTime = value,
                editorUseAlarm = value > 0,  // Auto-enable alarm if carb time > 0
                hasUnsavedChanges = true
            )
        }
    }

    fun updateValidFrom(value: Int) {
        uiState.update { it.copy(editorValidFrom = value, hasUnsavedChanges = true) }
    }

    fun updateValidTo(value: Int) {
        uiState.update { it.copy(editorValidTo = value, hasUnsavedChanges = true) }
    }

    fun updateUseBG(value: Boolean) {
        uiState.update { it.copy(editorUseBG = value, hasUnsavedChanges = true) }
    }

    fun updateUseCOB(value: Boolean) {
        uiState.update {
            it.copy(
                editorUseCOB = value,
                editorUseIOB = if (value) true else it.editorUseIOB,  // Auto-enable IOB if COB enabled
                hasUnsavedChanges = true
            )
        }
    }

    fun updateUseIOB(value: Boolean) {
        uiState.update {
            it.copy(
                editorUseIOB = value,
                editorUseCOB = if (!value) false else it.editorUseCOB,  // Auto-disable COB if IOB disabled
                editorUsePositiveIOBOnly = if (!value) false else it.editorUsePositiveIOBOnly,  // Auto-disable positive IOB only
                hasUnsavedChanges = true
            )
        }
    }

    fun updateUsePositiveIOBOnly(value: Boolean) {
        uiState.update { it.copy(editorUsePositiveIOBOnly = value, hasUnsavedChanges = true) }
    }

    fun updateUseTrend(value: TrendOption) {
        uiState.update { it.copy(editorUseTrend = value, hasUnsavedChanges = true) }
    }

    fun updateUseSuperBolus(value: Boolean) {
        uiState.update { it.copy(editorUseSuperBolus = value, hasUnsavedChanges = true) }
    }

    fun updateUseTempTarget(value: Boolean) {
        uiState.update { it.copy(editorUseTempTarget = value, hasUnsavedChanges = true) }
    }

    fun updateUseAlarm(value: Boolean) {
        uiState.update { it.copy(editorUseAlarm = value, hasUnsavedChanges = true) }
    }

    fun updatePercentage(value: Int) {
        uiState.update { it.copy(editorPercentage = value, hasUnsavedChanges = true) }
    }

    fun updateDevicePhone(value: Boolean) {
        uiState.update { it.copy(editorDevicePhone = value, hasUnsavedChanges = true) }
    }

    fun updateDeviceWatch(value: Boolean) {
        uiState.update { it.copy(editorDeviceWatch = value, hasUnsavedChanges = true) }
    }

    fun updateUseEcarbs(value: Boolean) {
        uiState.update { it.copy(editorUseEcarbs = value, hasUnsavedChanges = true) }
    }

    fun updateTime(value: Int) {
        uiState.update { it.copy(editorTime = value, hasUnsavedChanges = true) }
    }

    fun updateDuration(value: Int) {
        uiState.update { it.copy(editorDuration = value, hasUnsavedChanges = true) }
    }

    fun updateCarbs2(value: Int) {
        uiState.update { it.copy(editorCarbs2 = value, hasUnsavedChanges = true) }
    }

    /**
     * Convert boolean to QuickWizardEntry radio number (0 = true/YES, 1 = false/NO)
     */
    private fun booleanToRadioNumber(value: Boolean): Int = if (value) 0 else 1
}
