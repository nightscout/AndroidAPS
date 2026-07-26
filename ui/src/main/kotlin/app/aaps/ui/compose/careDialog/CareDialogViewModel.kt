package app.aaps.ui.compose.careDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.data.ui.confirmationLines
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class CareDialogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val persistenceLayer: PersistenceLayer,
    private val batchExecutor: BatchExecutor,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val translator: Translator,
    private val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(CareDialogUiState())
    val uiState: StateFlow<CareDialogUiState> = _uiState.asStateFlow()

    init {
        val eventType = CareportalEventType.entries[savedStateHandle.get<Int>("eventTypeOrdinal") ?: 0]
        val units = profileFunction.getUnits()
        val currentBg = profileUtil.fromMgdlToUnits(
            glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0
        )
        val showNotes = preferences.get(BooleanKey.OverviewShowNotesInDialogs)
        val siteRotation = preferences.get(BooleanKey.SiteRotationManageCgm)

        _uiState.update {
            CareDialogUiState(
                eventType = eventType,
                meterType = TE.MeterType.SENSOR,
                bgValue = currentBg,
                duration = 0.0,
                notes = "",
                eventTime = dateUtil.now(),
                eventTimeChanged = false,
                glucoseUnits = units,
                showNotesFromPreferences = showNotes,
                siteRotationManageCgm = siteRotation
            )
        }
        if (eventType == CareportalEventType.SENSOR_INSERT && siteRotation) {
            loadLastSensorLocation()
        }
    }

    private fun loadLastSensorLocation() {
        viewModelScope.launch {
            try {
                val allEntries = persistenceLayer.getTherapyEventDataFromTime(
                    dateUtil.now() - T.days(45).msecs(), false
                ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
                val lastEntry = allEntries
                    .filter { it.type == TE.Type.SENSOR_CHANGE && it.location != null && it.location != TE.Location.NONE }
                    .maxByOrNull { it.timestamp }
                _uiState.update {
                    it.copy(
                        siteRotationEntries = allEntries,
                        lastSiteLocationString = if (lastEntry != null) translator.translate(lastEntry.location) else it.lastSiteLocationString
                    )
                }
            } catch (_: Exception) {
                // ignore
            }
        }
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

    fun bodyType(): BodyType = BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))

    fun siteRotationEntries(): List<TE> = uiState.value.siteRotationEntries

    fun updateMeterType(meterType: TE.MeterType) {
        _uiState.update { it.copy(meterType = meterType) }
    }

    fun updateBgValue(value: Double) {
        // When user changes BG value, auto-switch from Sensor to Finger (matches old bgTextWatcher)
        val state = uiState.value
        val newMeterType = if (state.meterType == TE.MeterType.SENSOR) TE.MeterType.FINGER else state.meterType
        _uiState.update { it.copy(bgValue = value, meterType = newMeterType) }
    }

    fun updateDuration(value: Double) {
        _uiState.update { it.copy(duration = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateEventTime(timeMillis: Long) {
        _uiState.update { it.copy(eventTime = timeMillis, eventTimeChanged = true) }
    }

    private var confirmedState: CareDialogUiState? = null

    fun buildConfirmationSummary(): List<ConfirmationLine> {
        val state = uiState.value
        confirmedState = state
        return confirmationLines {
            line(ConfirmationRole.NORMAL, rh.gs(R.string.confirm_treatment))

            if (state.showBgSection) {
                line(
                    ConfirmationRole.NORMAL,
                    rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.glucose_type), translator.translate(state.meterType))
                )
                val unitResId = if (state.glucoseUnits == GlucoseUnit.MGDL)
                    app.aaps.core.ui.R.string.mgdl else app.aaps.core.ui.R.string.mmol
                val bgWithUnit = rh.gs(
                    app.aaps.core.ui.R.string.value_with_unit,
                    profileUtil.stringInCurrentUnitsDetect(state.bgValue),
                    rh.gs(unitResId)
                )
                line(
                    ConfirmationRole.PRIMARY,
                    rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(app.aaps.core.ui.R.string.bg_label), bgWithUnit)
                )
            }

            if (state.showDurationSection) {
                line(
                    ConfirmationRole.NORMAL,
                    rh.gs(
                        app.aaps.core.ui.R.string.confirmation_line,
                        rh.gs(app.aaps.core.ui.R.string.duration_label),
                        rh.gs(app.aaps.core.ui.R.string.format_mins, state.duration.toInt())
                    )
                )
            }

            if (state.notes.isNotEmpty()) {
                line(
                    ConfirmationRole.NORMAL,
                    rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(app.aaps.core.ui.R.string.notes_label), state.notes)
                )
            }

            if (state.eventTimeChanged) {
                line(
                    ConfirmationRole.NORMAL,
                    rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(app.aaps.core.ui.R.string.time), dateUtil.dateAndTimeString(state.eventTime))
                )
            }

            if (state.showSiteRotationSection && state.siteLocation != TE.Location.NONE) {
                line(
                    ConfirmationRole.NORMAL,
                    rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(app.aaps.core.ui.R.string.site_location), translator.translate(state.siteLocation))
                )
            }
        }
    }

    fun confirmAndSave() {
        val state = confirmedState ?: return
        val eventType = state.eventType
        val eventTime = state.eventTime - (state.eventTime % 1000)

        val isSensorChange = eventType == CareportalEventType.SENSOR_INSERT
        val location = if (isSensorChange) state.siteLocation.takeIf { it != TE.Location.NONE } else null
        val arrow = if (isSensorChange) state.siteArrow.takeIf { it != TE.Arrow.NONE } else null

        // Route through the master via the client-control batch channel (RoleBranch): on a client a signed
        // round-trip, on a master a local persist — the master is the SOLE writer (no local insert here; the event
        // syncs back via NS treatments). On an OFFLINE client the batch is rejected (not queued), consistent with bolus/scene.
        val action = BatchAction.TherapyEvent(
            teType = eventType.toTEType(),
            timestamp = eventTime,
            glucoseMgdl = if (state.showBgSection) profileUtil.convertToMgdl(state.bgValue, state.glucoseUnits) else null,
            glucoseType = if (state.showBgSection) state.meterType else null,
            durationMinutes = if (state.showDurationSection) state.duration.toInt() else 0,
            note = state.notes.ifEmpty { null },
            location = location,
            arrow = arrow,
            source = eventType.toSource()
        )
        val label = rh.gs(app.aaps.core.ui.R.string.careportal)

        // appScope, not viewModelScope: the screen navigates back immediately after confirm.
        appScope.launch {
            when (val prepared = batchExecutor.prepare(listOf(action), action.source, label)) {
                is ActionProgress.Prepared -> {
                    val committed = batchExecutor.commit(prepared.id, action.source, label)
                    if (committed is ActionProgress.Rejected)
                        aapsLogger.warn(LTag.UI, "Therapy event rejected: ${committed.reason} ${committed.detail}")
                }

                is ActionProgress.Rejected -> aapsLogger.warn(LTag.UI, "Therapy event prepare rejected: ${prepared.reason}")
                else                       -> Unit
            }
        }
    }

    private fun CareportalEventType.toTEType(): TE.Type = when (this) {
        CareportalEventType.BGCHECK        -> TE.Type.FINGER_STICK_BG_VALUE
        CareportalEventType.SENSOR_INSERT  -> TE.Type.SENSOR_CHANGE
        CareportalEventType.BATTERY_CHANGE -> TE.Type.PUMP_BATTERY_CHANGE
        CareportalEventType.NOTE           -> TE.Type.NOTE
        CareportalEventType.EXERCISE       -> TE.Type.EXERCISE
        CareportalEventType.QUESTION       -> TE.Type.QUESTION
        CareportalEventType.ANNOUNCEMENT   -> TE.Type.ANNOUNCEMENT
    }

    private fun CareportalEventType.toSource(): Sources = when (this) {
        CareportalEventType.BGCHECK        -> Sources.BgCheck
        CareportalEventType.SENSOR_INSERT  -> Sources.SensorInsert
        CareportalEventType.BATTERY_CHANGE -> Sources.BatteryChange
        CareportalEventType.NOTE           -> Sources.Note
        CareportalEventType.EXERCISE       -> Sources.Exercise
        CareportalEventType.QUESTION       -> Sources.Question
        CareportalEventType.ANNOUNCEMENT   -> Sources.Announcement
    }
}
