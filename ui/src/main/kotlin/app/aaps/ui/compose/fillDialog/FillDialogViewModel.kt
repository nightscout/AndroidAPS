package app.aaps.ui.compose.fillDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
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
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
@Stable
class FillDialogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
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
    private val translator: Translator,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<FillDialogUiState>
        field = MutableStateFlow(FillDialogUiState())

    sealed class SideEffect {
        data object ShowNoActionDialog : SideEffect()
        data class ShowDeliveryError(val comment: String) : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    init {
        val preselect = FillPreselect.entries[savedStateHandle.get<Int>("preselect") ?: 0]
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
                siteRotationEnabled = preferences.get(BooleanKey.SiteRotationManagePump),
                showNotesFromPreferences = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                simpleMode = preferences.get(BooleanKey.GeneralSimpleMode)
            )
        }
        loadLastSiteLocation()
    }

    private fun loadLastSiteLocation() {
        viewModelScope.launch {
            try {
                val allEntries = persistenceLayer.getTherapyEventDataFromTime(
                    dateUtil.now() - app.aaps.core.data.time.T.days(45).msecs(), false
                ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
                siteRotationEntriesCache = allEntries
                val lastEntry = allEntries
                    .filter { it.type == TE.Type.CANNULA_CHANGE && it.location != null && it.location != TE.Location.NONE }
                    .maxByOrNull { it.timestamp }
                if (lastEntry != null) {
                    uiState.update {
                        it.copy(lastSiteLocationString = translator.translate(lastEntry.location))
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
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

    fun updateSiteLocation(location: TE.Location) {
        uiState.update {
            it.copy(
                siteLocation = location,
                selectedSiteLocationString = if (location != TE.Location.NONE) translator.translate(location) else null
            )
        }
    }

    fun updateSiteArrow(arrow: TE.Arrow) {
        uiState.update { it.copy(siteArrow = arrow) }
    }

    fun bodyType(): BodyType = BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))

    fun siteRotationEntries(): List<TE> {
        // Return cached entries from uiState or empty; actual data loaded async
        return siteRotationEntriesCache
    }

    private var siteRotationEntriesCache: List<TE> = emptyList()

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

        if (state.siteRotationEnabled && state.siteLocation != TE.Location.NONE) {
            lines.add(rh.gs(app.aaps.core.ui.R.string.site_location) + ": " + translator.translate(state.siteLocation))
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
                    val location = state.siteLocation.takeIf { it != TE.Location.NONE }
                    val arrow = state.siteArrow.takeIf { it != TE.Arrow.NONE }
                    persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = TE(
                            timestamp = eventTime,
                            type = TE.Type.CANNULA_CHANGE,
                            note = notes,
                            glucoseUnit = GlucoseUnit.MGDL,
                            location = location,
                            arrow = arrow
                        ),
                        action = Action.SITE_CHANGE, source = Sources.FillDialog,
                        note = notes,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(eventTime).takeIf { state.eventTimeChanged },
                            ValueWithUnit.TEType(TE.Type.CANNULA_CHANGE),
                            location?.let { ValueWithUnit.TELocation(it) },
                            arrow?.let { ValueWithUnit.TEArrow(it) }
                        )
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.UI, "Failed to save site change", e)
                }
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
