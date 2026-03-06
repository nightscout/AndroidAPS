package app.aaps.ui.compose.insulinManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

@HiltViewModel
class InsulinManagementViewModel @Inject constructor(
    private val insulin: Insulin,
    private val insulinManager: InsulinManager,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val localProfileManager: LocalProfileManager,
    private val dateUtil: DateUtil,
    private val hardLimits: HardLimits,
    private val uel: UserEntryLogger,
    val rh: ResourceHelper
) : ViewModel() {

    val uiState: StateFlow<InsulinManagementUiState>
        field = MutableStateFlow(InsulinManagementUiState())

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    sealed class SideEffect {
        data class ScrollToInsulin(val index: Int) : SideEffect()
    }

    init {
        loadData()
    }

    fun setScreenMode(mode: ScreenMode) {
        uiState.update { it.copy(screenMode = mode) }
    }

    fun loadData(targetIndex: Int? = null, reload: Boolean = true, autoName: Boolean = false) {
        viewModelScope.launch {
            if (reload) insulinManager.loadSettings()
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel

            val currentIndex = (targetIndex ?: uiState.value.currentCardIndex).coerceIn(0, (insulins.size - 1).coerceAtLeast(0))
            val currentICfg = insulins.getOrNull(currentIndex)

            val template = currentICfg?.let { cfg -> InsulinType.fromPeak(cfg.insulinPeakTime) }

            uiState.update {
                it.copy(
                    insulins = insulins,
                    currentCardIndex = currentIndex,
                    activeInsulinLabel = activeLabel,
                    editorName = currentICfg?.insulinLabel ?: "",
                    editorTemplate = template,
                    editorConcentration = currentICfg?.let { cfg -> ConcentrationType.fromDouble(cfg.concentration) } ?: ConcentrationType.U100,
                    editorPeakMinutes = currentICfg?.peak ?: 75,
                    editorDiaHours = currentICfg?.dia ?: 5.0,
                    isLoading = false
                )
            }

            if (autoName) autoGenerateName()
            targetIndex?.let { sideEffect.emit(SideEffect.ScrollToInsulin(it)) }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            insulinManager.loadSettings()
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            uiState.update {
                it.copy(
                    insulins = insulins,
                    activeInsulinLabel = activeLabel
                )
            }
        }
    }

    fun updateCurrentCardIndex(index: Int) {
        if (index == uiState.value.currentCardIndex) return
        val insulins = uiState.value.insulins
        val iCfg = insulins.getOrNull(index) ?: return

        uiState.update {
            it.copy(
                currentCardIndex = index,
                editorName = iCfg.insulinLabel,
                editorTemplate = InsulinType.fromPeak(iCfg.insulinPeakTime),
                editorConcentration = ConcentrationType.fromDouble(iCfg.concentration),
                editorPeakMinutes = iCfg.peak,
                editorDiaHours = iCfg.dia
            )
        }
    }

    // Editor field updates

    fun updateEditorName(name: String) {
        uiState.update { it.copy(editorName = name) }
    }

    fun updateEditorConcentration(concentration: ConcentrationType) {
        uiState.update { it.copy(editorConcentration = concentration) }
        autoGenerateName()
    }

    fun updateEditorPeak(peakMinutes: Int) {
        uiState.update { it.copy(editorPeakMinutes = peakMinutes) }
        autoGenerateName()
    }

    fun updateEditorDia(diaHours: Double) {
        uiState.update { it.copy(editorDiaHours = diaHours) }
        autoGenerateName()
    }

    /** Load peak from a preset template (chips UI). Only sets peak, not DIA. */
    fun loadPeakFromPreset(preset: InsulinType) {
        uiState.update { it.copy(editorTemplate = preset, editorPeakMinutes = preset.iCfg.peak) }
        autoGenerateName()
    }

    private fun autoGenerateName() {
        val state = uiState.value
        val template = state.editorTemplate ?: InsulinType.fromPeak(state.editorPeakMinutes.toLong() * 60_000L)
        val templateName = rh.gs(template.label)
        val concLabel = rh.gs(state.editorConcentration.label)
        val diaLabel = if (state.editorDiaHours % 1.0 == 0.0) "${state.editorDiaHours.toInt()}h" else "${state.editorDiaHours}h"
        val baseName = "$templateName ${state.editorPeakMinutes}m ${diaLabel} $concLabel"
        var name = baseName
        val existingNames = state.insulins.mapIndexed { i, it -> if (i == state.currentCardIndex) null else it.insulinLabel }.filterNotNull()
        if (name in existingNames) {
            for (i in 1..100) {
                if ("$baseName ($i)" !in existingNames) {
                    name = "$baseName ($i)"
                    break
                }
            }
        }
        uiState.update { it.copy(editorName = name) }
    }

    // CRUD operations

    fun saveCurrentInsulin(): Boolean {
        val state = uiState.value
        val editedICfg = buildEditorICfg()

        // Validation
        if (editedICfg.insulinLabel.isEmpty()) {
            showSnackbar(rh.gs(R.string.missing_insulin_name))
            return false
        }
        if (editedICfg.dia < hardLimits.minDia() || editedICfg.dia > hardLimits.maxDia()) {
            showSnackbar(rh.gs(CoreUiR.string.value_out_of_hard_limits, rh.gs(CoreUiR.string.insulin_dia), editedICfg.dia))
            return false
        }
        if (editedICfg.peak < hardLimits.minPeak() || editedICfg.peak > hardLimits.maxPeak()) {
            showSnackbar(rh.gs(CoreUiR.string.value_out_of_hard_limits, rh.gs(CoreUiR.string.insulin_peak), editedICfg.peak))
            return false
        }

        // Check name uniqueness
        val existingIndex = state.insulins.indexOfFirst { it.insulinLabel == editedICfg.insulinLabel }
        if (existingIndex >= 0 && existingIndex != state.currentCardIndex) {
            showSnackbar(rh.gs(R.string.insulin_name_exists, editedICfg.insulinLabel))
            return false
        }

        // Apply to plugin
        val stored = insulinManager.insulins.getOrNull(state.currentCardIndex)
        if (stored != null) {
            stored.insulinLabel = editedICfg.insulinLabel
            stored.insulinEndTime = editedICfg.insulinEndTime
            stored.insulinPeakTime = editedICfg.insulinPeakTime
            stored.concentration = editedICfg.concentration
            uel.log(Action.STORE_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(editedICfg.insulinLabel))
        }
        insulinManager.storeSettings()
        loadData(reload = false)
        return true
    }

    fun addNewInsulin() {
        val state = uiState.value
        val source = state.insulins.getOrNull(state.currentCardIndex)
        val newICfg = source?.deepClone() ?: InsulinType.OREF_RAPID_ACTING.iCfg
        newICfg.insulinLabel = ""
        insulinManager.addNewInsulin(newICfg)
        loadData(targetIndex = insulinManager.currentInsulinIndex, reload = false, autoName = true)
    }

    fun deleteCurrentInsulin(): Boolean {
        val state = uiState.value
        if (state.insulins.size <= 1) {
            showSnackbar(rh.gs(R.string.cannot_delete_last_insulin))
            return false
        }
        val currentICfg = state.insulins.getOrNull(state.currentCardIndex) ?: return false
        if (currentICfg.insulinLabel == state.activeInsulinLabel) {
            showSnackbar(rh.gs(R.string.cannot_delete_active_insulin))
            return false
        }

        insulinManager.currentInsulinIndex = state.currentCardIndex
        insulinManager.removeCurrentInsulin()
        loadData(reload = false)
        return true
    }

    fun prepareActivation() {
        val state = uiState.value
        val iCfg = state.insulins.getOrNull(state.currentCardIndex) ?: return
        val profile = profileFunction.getProfile()
        if (profile == null) {
            showSnackbar(rh.gs(R.string.activate_insulin_no_profile))
            return
        }

        val eps = (profile as? ProfileSealed.EPS)?.value
        val profileName = eps?.originalProfileName ?: return
        val percentage = eps.originalPercentage
        val timeshiftHours = T.msecs(eps.originalTimeshift).hours().toInt()
        val durationMs = eps.originalDuration

        val details = mutableListOf<String>()
        details.add(profileName)
        if (percentage != 100) details.add(rh.gs(CoreUiR.string.format_percent, percentage))
        if (timeshiftHours != 0) details.add(rh.gs(CoreUiR.string.format_hours, timeshiftHours.toDouble()))
        if (durationMs > 0) {
            val remaining = ((durationMs - (dateUtil.now() - eps.timestamp)) / 60_000L).coerceAtLeast(0)
            if (remaining > 0)
                details.add(rh.gs(R.string.activate_insulin_remaining, remaining.toInt()))
        }

        val message = rh.gs(R.string.activate_insulin_new_insulin, iCfg.insulinLabel) +
            "\n\n" + rh.gs(R.string.activate_insulin_profile_switch, details.joinToString(", "))

        uiState.update { it.copy(activationMessage = message) }
    }

    fun dismissActivation() {
        uiState.update { it.copy(activationMessage = null) }
    }

    fun executeActivation() {
        uiState.update { it.copy(activationMessage = null) }
        val state = uiState.value
        val iCfg = state.insulins.getOrNull(state.currentCardIndex) ?: return
        profileFunction.createProfileSwitchWithNewInsulin(iCfg, Sources.Insulin)
        refreshData()
    }

    // Helpers

    fun hasUnsavedChanges(): Boolean {
        val state = uiState.value
        val stored = state.insulins.getOrNull(state.currentCardIndex) ?: return state.editorName.isNotEmpty()
        return state.editorName != stored.insulinLabel ||
            state.editorPeakMinutes != stored.peak ||
            state.editorDiaHours != stored.dia ||
            state.editorConcentration.value != stored.concentration
    }

    fun isActiveInsulin(): Boolean {
        val state = uiState.value
        val current = state.insulins.getOrNull(state.currentCardIndex) ?: return false
        return current.insulinLabel == state.activeInsulinLabel
    }

    fun canDelete(): Boolean {
        val state = uiState.value
        if (state.insulins.size <= 1) return false
        val current = state.insulins.getOrNull(state.currentCardIndex) ?: return false
        return current.insulinLabel != state.activeInsulinLabel
    }

    /** Build an ICfg from current editor state */
    fun buildEditorICfg(): ICfg {
        val state = uiState.value
        val iCfg = ICfg(
            insulinLabel = state.editorName,
            insulinEndTime = 0,
            insulinPeakTime = 0,
            concentration = state.editorConcentration.value
        )
        iCfg.setDia(state.editorDiaHours)
        iCfg.setPeak(state.editorPeakMinutes)
        return iCfg
    }

    fun clearSnackbar() {
        uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun showSnackbar(message: String) {
        uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(message)) }
    }

    /** Preset list for "Load peak from" chips — excludes FreePeak (not a real preset) */
    fun presetList(): List<InsulinType> = insulinManager.insulinTemplateList().filter { it != InsulinType.OREF_FREE_PEAK }
    fun concentrationList(): List<ConcentrationType> = insulinManager.concentrationList()
    fun diaRange(): ClosedFloatingPointRange<Double> = hardLimits.minDia()..hardLimits.maxDia()
    fun peakRange(): ClosedFloatingPointRange<Double> = hardLimits.minPeak().toDouble()..hardLimits.maxPeak().toDouble()
}
