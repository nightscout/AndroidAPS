package app.aaps.ui.compose.careDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
class CareDialogViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val translator: Translator,
    private val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<CareDialogUiState>
        field = MutableStateFlow(CareDialogUiState())

    sealed class SideEffect {
        data class ShowSiteRotationDialog(val timestamp: Long) : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private var lastEventType: UiInteraction.EventType? = null

    fun initForEventType(eventType: UiInteraction.EventType) {
        if (lastEventType == eventType) return
        lastEventType = eventType
        val units = profileFunction.getUnits()
        val currentBg = profileUtil.fromMgdlToUnits(
            glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0
        )
        val showNotes = preferences.get(BooleanKey.OverviewShowNotesInDialogs)
        val siteRotation = preferences.get(BooleanKey.SiteRotationManageCgm)

        uiState.update {
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
    }

    fun updateMeterType(meterType: TE.MeterType) {
        uiState.update { it.copy(meterType = meterType) }
    }

    fun updateBgValue(value: Double) {
        // When user changes BG value, auto-switch from Sensor to Finger (matches old bgTextWatcher)
        val state = uiState.value
        val newMeterType = if (state.meterType == TE.MeterType.SENSOR) TE.MeterType.FINGER else state.meterType
        uiState.update { it.copy(bgValue = value, meterType = newMeterType) }
    }

    fun updateDuration(value: Double) {
        uiState.update { it.copy(duration = value) }
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

        lines.add(rh.gs(R.string.confirm_treatment))

        if (state.showBgSection) {
            lines.add(rh.gs(R.string.glucose_type) + ": " + translator.translate(state.meterType))
            val unitResId = if (state.glucoseUnits == GlucoseUnit.MGDL)
                app.aaps.core.ui.R.string.mgdl else app.aaps.core.ui.R.string.mmol
            lines.add(
                rh.gs(app.aaps.core.ui.R.string.bg_label) + ": " +
                    profileUtil.stringInCurrentUnitsDetect(state.bgValue) + " " +
                    rh.gs(unitResId)
            )
        }

        if (state.showDurationSection) {
            lines.add(
                rh.gs(app.aaps.core.ui.R.string.duration_label) + ": " +
                    rh.gs(app.aaps.core.ui.R.string.format_mins, state.duration.toInt())
            )
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
        val eventType = state.eventType

        val eventTime = state.eventTime - (state.eventTime % 1000)

        val therapyEvent = TE(
            timestamp = eventTime,
            type = eventType.toTEType(),
            glucoseUnit = state.glucoseUnits
        )

        val valuesWithUnit = mutableListOf<ValueWithUnit?>()

        if (state.showBgSection) {
            therapyEvent.glucoseType = state.meterType
            therapyEvent.glucose = state.bgValue
            valuesWithUnit.add(ValueWithUnit.fromGlucoseUnit(state.bgValue, state.glucoseUnits))
            valuesWithUnit.add(ValueWithUnit.TEMeterType(state.meterType))
        }

        if (state.showDurationSection) {
            therapyEvent.duration = T.mins(state.duration.toLong()).msecs()
            valuesWithUnit.add(
                ValueWithUnit.Minute(state.duration.toInt()).takeIf { state.duration != 0.0 }
            )
        }

        if (state.notes.isNotEmpty()) {
            therapyEvent.note = state.notes
        }

        therapyEvent.enteredBy = "AAPS"

        val source = eventType.toSource()

        valuesWithUnit.add(0, ValueWithUnit.Timestamp(eventTime).takeIf { state.eventTimeChanged })
        valuesWithUnit.add(1, ValueWithUnit.TEType(therapyEvent.type))

        viewModelScope.launch {
            try {
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = therapyEvent,
                    action = Action.CAREPORTAL,
                    source = source,
                    note = state.notes,
                    listValues = valuesWithUnit.filterNotNull()
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save therapy event", e)
            }
        }

        // Special case: sensor change + site rotation management
        if (therapyEvent.type == TE.Type.SENSOR_CHANGE && state.siteRotationManageCgm) {
            sideEffect.tryEmit(SideEffect.ShowSiteRotationDialog(therapyEvent.timestamp))
        }
    }

    private fun UiInteraction.EventType.toTEType(): TE.Type = when (this) {
        UiInteraction.EventType.BGCHECK        -> TE.Type.FINGER_STICK_BG_VALUE
        UiInteraction.EventType.SENSOR_INSERT  -> TE.Type.SENSOR_CHANGE
        UiInteraction.EventType.BATTERY_CHANGE -> TE.Type.PUMP_BATTERY_CHANGE
        UiInteraction.EventType.NOTE           -> TE.Type.NOTE
        UiInteraction.EventType.EXERCISE       -> TE.Type.EXERCISE
        UiInteraction.EventType.QUESTION       -> TE.Type.QUESTION
        UiInteraction.EventType.ANNOUNCEMENT   -> TE.Type.ANNOUNCEMENT
    }

    private fun UiInteraction.EventType.toSource(): Sources = when (this) {
        UiInteraction.EventType.BGCHECK        -> Sources.BgCheck
        UiInteraction.EventType.SENSOR_INSERT  -> Sources.SensorInsert
        UiInteraction.EventType.BATTERY_CHANGE -> Sources.BatteryChange
        UiInteraction.EventType.NOTE           -> Sources.Note
        UiInteraction.EventType.EXERCISE       -> Sources.Exercise
        UiInteraction.EventType.QUESTION       -> Sources.Question
        UiInteraction.EventType.ANNOUNCEMENT   -> Sources.Announcement
    }
}
