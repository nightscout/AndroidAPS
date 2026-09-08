package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable

/**
 * One point of a profile's daily curve: the time of day it starts at, and the value from there on.
 *
 * This and [SingleProfileState] used to sit next to `ProfileEditorViewModel` in its file. They are
 * plain values, but the view model beside them is Android bound and a file moves as a whole - so the
 * editor rows and their previews were pinned to androidMain by proximity rather than by any real
 * dependency.
 */
data class TimeValue(
    val timeSeconds: Int,
    val value: Double
)

/** One profile as the editor holds it, before it is turned back into a stored profile. */
@Immutable
data class SingleProfileState(
    val name: String = "",
    val mgdl: Boolean = true,
    val dia: Double = 5.0,
    val ic: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val isf: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val basal: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val targetLow: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val targetHigh: List<TimeValue> = listOf(TimeValue(0, 0.0))
)
