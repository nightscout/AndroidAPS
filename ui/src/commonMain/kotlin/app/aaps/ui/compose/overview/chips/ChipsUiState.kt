package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Immutable

/**
 * What the overview chips show.
 *
 * These used to sit next to `ChipsViewModel` in its file. They are plain values with no Android in
 * them, but the view model beside them is Android bound, and a file moves as a whole - so every
 * composable and preview that reads these was pinned to androidMain by proximity rather than by any
 * real dependency. They live here so they can be shared.
 */
@Immutable
data class IobUiState(
    val text: String = "",
    val iobTotal: Double = 0.0
)

@Immutable
data class CobUiState(
    val text: String = "",
    val carbsReq: Int = 0,
    val cobValue: Double = 0.0
)

@Immutable
data class SensitivityUiState(
    val asText: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val dialogText: String = "",
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)
