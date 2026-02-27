package app.aaps.ui.compose.fillDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.ui.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.abs

@Stable
class FillDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    private val commandQueue: CommandQueue,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val persistenceLayer: PersistenceLayer,
    val preferences: Preferences,
    val config: Config,
    private val decimalFormatter: DecimalFormatter,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<FillDialogUiState>
        field = MutableStateFlow(FillDialogUiState())

    sealed class SideEffect {
        data class ShowSiteRotationDialog(val timestamp: Long) : SideEffect()
        data object ShowNoActionDialog : SideEffect()
        data class ShowDeliveryError(val comment: String) : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private var lastPreselect: FillPreselect? = null

    fun init(preselect: FillPreselect = FillPreselect.NONE) {
        if (lastPreselect == preselect) return
        lastPreselect = preselect
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep

        uiState.update {
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
                showBolus = !config.AAPSCLIENT,
                showNotesFromPreferences = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                simpleMode = preferences.get(BooleanKey.GeneralSimpleMode)
            )
        }
    }

    fun refreshPresetButtons() {
        uiState.update {
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
        uiState.update {
            it.copy(
                insulin = value,
                insulinAfterConstraints = constrained,
                constraintApplied = abs(constrained - value) > 0.01
            )
        }
    }

    fun updateSiteChange(checked: Boolean) {
        uiState.update { it.copy(siteChange = checked) }
    }

    fun updateCartridgeChange(checked: Boolean) {
        uiState.update { it.copy(insulinCartridgeChange = checked) }
    }

    fun updateNotes(value: String) {
        uiState.update { it.copy(notes = value) }
    }

    fun updateEventTime(timeMillis: Long) {
        uiState.update { it.copy(eventTime = timeMillis, eventTimeChanged = true) }
    }

    fun buildConfirmationSummary(): List<String> {
        val state = uiState.value
        val lines = mutableListOf<String>()
        val bolusStep = state.bolusStep

        if (state.insulinAfterConstraints > 0) {
            lines.add(rh.gs(R.string.fill_warning))
            lines.add("")
            lines.add(
                rh.gs(app.aaps.core.ui.R.string.bolus) + ": " +
                    decimalFormatter.toPumpSupportedBolus(state.insulinAfterConstraints, bolusStep)
            )
            if (state.constraintApplied) {
                lines.add(
                    rh.gs(
                        app.aaps.core.ui.R.string.bolus_constraint_applied_warn,
                        state.insulin,
                        state.insulinAfterConstraints
                    )
                )
            }
        }

        if (state.siteChange) {
            lines.add(rh.gs(R.string.record_pump_site_change))
        }

        if (state.insulinCartridgeChange) {
            lines.add(rh.gs(R.string.record_insulin_cartridge_change))
        }

        if (state.notes.isNotEmpty()) {
            lines.add(rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + state.notes)
        }

        if (state.eventTimeChanged) {
            lines.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(state.eventTime))
        }

        return lines
    }

    fun confirmAndSave() {
        val state = uiState.value
        val eventTime = state.eventTime - (state.eventTime % 1000)
        val notes = state.notes

        val hasAction = state.insulinAfterConstraints > 0 || state.siteChange || state.insulinCartridgeChange

        if (!hasAction) {
            sideEffect.tryEmit(SideEffect.ShowNoActionDialog)
            return
        }

        // Prime bolus
        if (state.insulinAfterConstraints > 0) {
            uel.log(
                action = Action.PRIME_BOLUS, source = Sources.FillDialog,
                note = notes,
                value = ValueWithUnit.Insulin(state.insulinAfterConstraints)
            )
            requestPrimeBolus(state.insulinAfterConstraints, notes)
        }

        // Site change
        if (state.siteChange) {
            viewModelScope.launch {
                try {
                    persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = TE(
                            timestamp = eventTime,
                            type = TE.Type.CANNULA_CHANGE,
                            note = notes,
                            glucoseUnit = GlucoseUnit.MGDL
                        ),
                        action = Action.SITE_CHANGE, source = Sources.FillDialog,
                        note = notes,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(eventTime).takeIf { state.eventTimeChanged },
                            ValueWithUnit.TEType(TE.Type.CANNULA_CHANGE)
                        )
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.UI, "Failed to save site change", e)
                }
            }

            if (preferences.get(BooleanKey.SiteRotationManageCgm)) {
                sideEffect.tryEmit(SideEffect.ShowSiteRotationDialog(eventTime))
            }
        }

        // Insulin cartridge change (offset by 1 second if site change also recorded)
        if (state.insulinCartridgeChange) {
            viewModelScope.launch {
                try {
                    persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = TE(
                            timestamp = eventTime + 1000,
                            type = TE.Type.INSULIN_CHANGE,
                            note = notes,
                            glucoseUnit = GlucoseUnit.MGDL
                        ),
                        action = Action.RESERVOIR_CHANGE, source = Sources.FillDialog,
                        note = notes,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(eventTime).takeIf { state.eventTimeChanged },
                            ValueWithUnit.TEType(TE.Type.INSULIN_CHANGE)
                        )
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.UI, "Failed to save insulin change", e)
                }
            }
        }
    }

    fun decimalFormat(): DecimalFormat =
        decimalFormatter.pumpSupportedBolusFormat(uiState.value.bolusStep)

    private fun requestPrimeBolus(insulin: Double, notes: String) {
        val detailedBolusInfo = DetailedBolusInfo().also {
            it.insulin = insulin
            it.bolusType = BS.Type.PRIMING
            it.notes = notes
        }
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (!result.success) {
                    sideEffect.tryEmit(SideEffect.ShowDeliveryError(result.comment))
                }
            }
        })
    }
}
