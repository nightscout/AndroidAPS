package app.aaps.ui.compose.fillDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.data.ui.confirmationLines
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.observeChange
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.abs
import app.aaps.core.ui.R as CoreUiR

@HiltViewModel
@Stable
class FillDialogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val constraintChecker: ConstraintsChecker,
    activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    val preferences: Preferences,
    val config: Config,
    private val decimalFormatter: DecimalFormatter,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val translator: Translator,
    private val aapsLogger: AAPSLogger,
    private val ch: ConcentrationHelper,
    insulinManager: InsulinManager,
    private val profileFunction: ProfileFunction,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val batchExecutor: BatchExecutor,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(FillDialogUiState())
    val uiState: StateFlow<FillDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data object ShowNoActionDialog : SideEffect()
        data class ShowDeliveryError(val comment: String) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val preselect = FillPreselect.entries[savedStateHandle.get<Int>("preselect") ?: 0]
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep

        val availableInsulins = insulinManager.insulins.map { it.deepClone() }

        _uiState.update {
            FillDialogUiState(
                insulin = 0.0,
                siteChange = preselect == FillPreselect.SITE_CHANGE,
                insulinCartridgeChange = preselect == FillPreselect.CARTRIDGE_CHANGE,
                notes = "",
                eventTime = dateUtil.now(),
                eventTimeChanged = false,
                maxInsulin = maxInsulin,
                bolusStep = bolusStep,
                presetButton1 = preferences.get(DoubleKey.ActionsFillButton1),
                presetButton2 = preferences.get(DoubleKey.ActionsFillButton2),
                presetButton3 = preferences.get(DoubleKey.ActionsFillButton3),
                insulinAfterConstraints = 0.0,
                constraintApplied = false,
                availableInsulins = availableInsulins,
                selectedInsulin = availableInsulins.firstOrNull(),
                activeInsulinLabel = null,
                pumpUnitsWarning = pumpUnitsWarningFor(availableInsulins.firstOrNull()),
                showBolus = !config.AAPSCLIENT,
                siteRotationEnabled = preferences.get(BooleanKey.SiteRotationManagePump),
                showNotesFromPreferences = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                simpleMode = preferences.get(BooleanKey.GeneralSimpleMode),
                concentrationEnabled = preferences.get(BooleanKey.GeneralInsulinConcentration)
            )
        }
        viewModelScope.launch {
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            val currentInsulin = availableInsulins.find { it.insulinLabel == activeLabel } ?: availableInsulins.firstOrNull()
            _uiState.update {
                it.copy(
                    selectedInsulin = currentInsulin,
                    activeInsulinLabel = activeLabel,
                    pumpUnitsWarning = pumpUnitsWarningFor(currentInsulin)
                )
            }
        }
        loadLastSiteLocation()
        observeConcentrationEnabled()
    }

    // The concentration-support flag is a synced preference (it can change from another device while
    // this dialog is open). This dialog is not a preference screen, so it isn't covered by the
    // shared-state observer in ProvidePreferenceTheme — observe the key here instead of only
    // snapshotting it at construction.
    private fun observeConcentrationEnabled() {
        preferences.observeChange(BooleanKey.GeneralInsulinConcentration)
            .onEach { _uiState.update { it.copy(concentrationEnabled = preferences.get(BooleanKey.GeneralInsulinConcentration)) } }
            .launchIn(viewModelScope)
    }

    private fun loadLastSiteLocation() {
        viewModelScope.launch {
            try {
                val allEntries = persistenceLayer.getTherapyEventDataFromTime(
                    dateUtil.now() - app.aaps.core.data.time.T.days(45).msecs(), false
                ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
                val lastEntry = allEntries
                    .filter { it.type == TE.Type.CANNULA_CHANGE && it.location != null && it.location != TE.Location.NONE }
                    .maxByOrNull { it.timestamp }
                if (lastEntry != null) {
                    _uiState.update {
                        it.copy(lastSiteLocationString = translator.translate(lastEntry.location))
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun refreshPresetButtons() {
        _uiState.update {
            it.copy(
                presetButton1 = preferences.get(DoubleKey.ActionsFillButton1),
                presetButton2 = preferences.get(DoubleKey.ActionsFillButton2),
                presetButton3 = preferences.get(DoubleKey.ActionsFillButton3)
            )
        }
    }

    fun updateInsulin(value: Double) {
        val constrained = constraintChecker.applyBolusConstraints(
            ConstraintObject(value, aapsLogger)
        ).value()
        _uiState.update {
            it.copy(
                insulin = value,
                insulinAfterConstraints = constrained,
                constraintApplied = abs(constrained - value) > 0.01
            )
        }
    }

    fun updateSiteChange(checked: Boolean) {
        _uiState.update { it.copy(siteChange = checked) }
    }

    fun updateCartridgeChange(checked: Boolean) {
        _uiState.update { it.copy(insulinCartridgeChange = checked) }
    }

    fun selectInsulin(iCfg: ICfg) {
        _uiState.update { it.copy(selectedInsulin = iCfg, pumpUnitsWarning = pumpUnitsWarningFor(iCfg)) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateEventTime(timeMillis: Long) {
        _uiState.update { it.copy(eventTime = timeMillis, eventTimeChanged = true) }
    }

    private fun pumpUnitsWarningFor(iCfg: ICfg?): String? {
        val concentration = iCfg?.concentration ?: return null
        if (concentration == 1.0) return null // U100 — no warning needed
        return rh.gs(R.string.fill_pump_units_note)
    }

    fun updateSiteLocation(location: TE.Location) {
        _uiState.update {
            it.copy(
                siteLocation = location,
                selectedSiteLocationString = if (location != TE.Location.NONE) translator.translate(location) else null
            )
        }
    }

    fun updateSiteArrow(arrow: TE.Arrow) {
        _uiState.update { it.copy(siteArrow = arrow) }
    }

    private var confirmedState: FillDialogUiState? = null

    fun buildConfirmationSummary(): List<ConfirmationLine> {
        val state = uiState.value
        confirmedState = state
        val bolusStep = state.bolusStep

        return confirmationLines {
            if (state.insulinAfterConstraints > 0) {
                line(ConfirmationRole.WARNING, rh.gs(R.string.fill_warning))
                line(ConfirmationRole.NORMAL, "")
                val bolusValue =
                    if (state.siteChange || state.insulinCartridgeChange)
                        ch.bolusWithVolume(state.insulinAfterConstraints)
                    else
                        decimalFormatter.toPumpSupportedBolusWithUnits(state.insulinAfterConstraints, bolusStep)
                line(ConfirmationRole.BOLUS, rh.gs(CoreUiR.string.confirmation_line, rh.gs(R.string.fill_prime_amount), bolusValue))
                if (state.constraintApplied) {
                    line(
                        ConfirmationRole.WARNING,
                        rh.gs(
                            CoreUiR.string.bolus_constraint_applied_warn,
                            state.insulin,
                            state.insulinAfterConstraints
                        )
                    )
                }
                pumpUnitsWarningFor(state.selectedInsulin)?.let { warning ->
                    line(ConfirmationRole.WARNING, warning)
                }
            }

            if (state.siteChange) {
                line(ConfirmationRole.NORMAL, rh.gs(R.string.record_pump_site_change))
            }

            if (state.insulinCartridgeChange) {
                line(ConfirmationRole.NORMAL, rh.gs(R.string.record_insulin_cartridge_change))
            }

            if (state.insulinChanged) {
                line(
                    ConfirmationRole.WARNING,
                    rh.gs(R.string.fill_insulin_change, state.selectedInsulin?.insulinLabel ?: "")
                )
            }

            if (state.notes.isNotEmpty()) {
                line(ConfirmationRole.NORMAL, rh.gs(CoreUiR.string.confirmation_line, rh.gs(CoreUiR.string.notes_label), state.notes))
            }

            if (state.eventTimeChanged) {
                line(ConfirmationRole.NORMAL, rh.gs(CoreUiR.string.confirmation_line, rh.gs(CoreUiR.string.time), dateUtil.dateAndTimeString(state.eventTime)))
            }

            if (state.siteRotationEnabled && state.siteLocation != TE.Location.NONE) {
                line(ConfirmationRole.NORMAL, rh.gs(CoreUiR.string.confirmation_line, rh.gs(CoreUiR.string.site_location), translator.translate(state.siteLocation)))
            }
        }
    }

    fun confirmAndSave() {
        val state = confirmedState ?: return
        val eventTime = state.eventTime - (state.eventTime % 1000)
        val notes = state.notes

        if (!state.hasAction) {
            _sideEffect.tryEmit(SideEffect.ShowNoActionDialog)
            return
        }

        val doProfileSwitch = state.insulinChanged
        val hasPrimeBolus = state.insulinAfterConstraints > 0

        // All work runs on appScope, not viewModelScope: the dialog navigates back the moment
        // confirm is tapped, which cancels viewModelScope. The prime bolus is also launched
        // independently so the site/insulin change logging below is never gated behind the
        // (potentially long) prime completing — previously a prime would drop those records.
        if (hasPrimeBolus) {
            // Prime now rides the shared executor (one audited path): it logs the user entry, sets the
            // PRIMING treatment + notes, and the profile switch is sequenced on prime success.
            appScope.launch {
                wizardBolusExecutor.deliverFillBolus(
                    amount = state.insulinAfterConstraints,
                    notes = notes,
                    source = Sources.FillDialog,
                    onError = { _sideEffect.tryEmit(SideEffect.ShowDeliveryError(it)) },
                    onSuccess = {
                        // After successful prime, do profile switch if insulin changed
                        if (doProfileSwitch) {
                            appScope.launch {
                                activateInsulin(state.selectedInsulin!!)
                            }
                        }
                    }
                )
            }
        } else {
            // No prime — do profile switch immediately if insulin changed
            if (doProfileSwitch) {
                appScope.launch {
                    activateInsulin(state.selectedInsulin!!)
                }
            }
        }

        // Site change → routed through the master via the batch channel (one audited writer), like the prime +
        // insulin activation above. The master persists CANNULA_CHANGE and re-derives the SITE_CHANGE audit category.
        if (state.siteChange) {
            val location = state.siteLocation.takeIf { it != TE.Location.NONE }
            val arrow = state.siteArrow.takeIf { it != TE.Arrow.NONE }
            appScope.launch {
                recordTherapyEvent(
                    BatchAction.TherapyEvent(
                        teType = TE.Type.CANNULA_CHANGE, timestamp = eventTime, note = notes.ifEmpty { null },
                        location = location, arrow = arrow, source = Sources.FillDialog
                    )
                )
            }
        }

        // Insulin cartridge change (offset +1s to avoid the site-change timestamp collision the master dedups on).
        if (state.insulinCartridgeChange) {
            appScope.launch {
                recordTherapyEvent(
                    BatchAction.TherapyEvent(
                        teType = TE.Type.INSULIN_CHANGE, timestamp = eventTime + 1000, note = notes.ifEmpty { null },
                        source = Sources.FillDialog
                    )
                )
            }
        }
    }

    /** Record a careportal therapy event through the master-controlled batch channel (no confirmation UI — prepare→commit). */
    private suspend fun recordTherapyEvent(action: BatchAction.TherapyEvent) {
        val label = rh.gs(CoreUiR.string.careportal)
        when (val prepared = batchExecutor.prepare(listOf(action), action.source, label)) {
            is ActionProgress.Prepared -> {
                val committed = batchExecutor.commit(prepared.id, action.source, label)
                if (committed is ActionProgress.Rejected)
                    aapsLogger.warn(LTag.UI, "Fill therapy event commit rejected: ${committed.reason} ${committed.detail}")
            }

            is ActionProgress.Rejected -> aapsLogger.warn(LTag.UI, "Fill therapy event prepare rejected: ${prepared.reason} ${prepared.detail}")
            else                       -> Unit
        }
    }

    /**
     * Non-interactive insulin activation for the fill flow (a chained profile switch after a prime) — relays through
     * the master-controlled [BatchExecutor] like everything else, but with NO confirmation UI: prepare then commit
     * back-to-back. Master → local; client → signed round-trip. Failures are surfaced by the round-trip's app-level
     * modal (client); on the master-local path they have no UI here, so a [ActionProgress.Rejected] prepare/commit is
     * logged (no longer silently swallowed), matching the prior fire-and-forget semantics otherwise.
     */
    private suspend fun activateInsulin(iCfg: ICfg) {
        val label = rh.gs(CoreUiR.string.activate_insulin)
        when (val prepared = batchExecutor.prepare(listOf(BatchAction.InsulinActivate(iCfg)), Sources.FillDialog, label)) {
            is ActionProgress.Prepared -> {
                val committed = batchExecutor.commit(prepared.id, Sources.FillDialog, label)
                if (committed is ActionProgress.Rejected)
                    aapsLogger.warn(LTag.UI, "Fill insulin activation commit rejected: ${committed.reason} ${committed.detail}")
            }

            is ActionProgress.Rejected -> aapsLogger.warn(LTag.UI, "Fill insulin activation prepare rejected: ${prepared.reason} ${prepared.detail}")
            else                       -> Unit
        }
    }

    fun decimalFormat(): DecimalFormat =
        decimalFormatter.pumpSupportedBolusFormat(uiState.value.bolusStep)

}
