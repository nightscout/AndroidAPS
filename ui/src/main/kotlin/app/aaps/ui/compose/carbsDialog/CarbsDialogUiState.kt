package app.aaps.ui.compose.carbsDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit

@Immutable
data class CarbsDialogUiState(
    // User input
    val carbs: Int = 0,
    val timeOffsetMinutes: Int = 0,
    val durationHours: Int = 0,
    val hypoTtChecked: Boolean = false,
    val eatingSoonTtChecked: Boolean = false,
    val activityTtChecked: Boolean = false,
    val alarmChecked: Boolean = false,
    val bolusReminderChecked: Boolean = false,
    val notes: String = "",
    val eventTime: Long = System.currentTimeMillis(),
    val eventTimeOriginal: Long = System.currentTimeMillis(),

    // Config (set once during init)
    val maxCarbs: Int = 0,
    val carbsButtonIncrement1: Int = 5,
    val carbsButtonIncrement2: Int = 10,
    val carbsButtonIncrement3: Int = 20,
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val showNotesFromPreferences: Boolean = false,
    val showBolusReminder: Boolean = false,
    val hypoTtTarget: Double = 0.0,
    val hypoTtDuration: Int = 0,
    val eatingSoonTtTarget: Double = 0.0,
    val eatingSoonTtDuration: Int = 0,
    val activityTtTarget: Double = 0.0,
    val activityTtDuration: Int = 0,
    val maxCarbsDurationHours: Long = 10,
    val simpleMode: Boolean = true
)

val CarbsDialogUiState.eventTimeChanged: Boolean
    get() = eventTime != eventTimeOriginal

val CarbsDialogUiState.alarmEnabled: Boolean
    get() = carbs > 0 && timeOffsetMinutes > 0
