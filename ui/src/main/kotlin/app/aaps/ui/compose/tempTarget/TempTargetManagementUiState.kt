package app.aaps.ui.compose.tempTarget

import androidx.compose.runtime.Immutable

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage

/**
 * UI state for TempTargetManagementScreen
 */
@Immutable
data class TempTargetManagementUiState(
    /** Currently active temporary target (if any) */
    val activeTT: TT? = null,

    /** Remaining time in milliseconds for active TT */
    val remainingTimeMs: Long? = null,

    /** List of user-defined presets (minimum 3 default presets) */
    val presets: List<TTPreset> = emptyList(),

    /** Index of preset matching active TT (null = no match, show standalone active card) */
    val activePresetIndex: Int? = null,

    /** Currently selected card index in carousel (0 = active TT if exists, then presets) */
    val currentCardIndex: Int = 0,

    /** Currently selected preset (null if custom/active card selected) */
    val selectedPreset: TTPreset? = null,

    // ===== Editor fields (saved to preset via Save button) =====

    /** Name for custom presets (empty for default presets) */
    val editorName: String = "",

    /** Target value in user's current units (mg/dL or mmol/L for display in slider) */
    val editorTarget: Double = 100.0,

    /** Duration in milliseconds (converted to minutes for display) */
    val editorDuration: Long = 60L * 60L * 1000L, // 60 minutes default

    // ===== Activation fields (NOT saved to preset, only for activation) =====

    /** Date/time for activation (default: now) */
    val eventTime: Long = System.currentTimeMillis(),

    /** Whether user modified the event time */
    val eventTimeChanged: Boolean = false,

    /** Optional notes for this activation */
    val notes: String = "",

    /** Whether to show notes field (from preferences) */
    val showNotesField: Boolean = false,

    // ===== Loading/Error state =====

    // ===== Screen mode =====

    /** Current screen mode (PLAY = activate only, EDIT = full editing) */
    val screenMode: ScreenMode = ScreenMode.EDIT,

    // ===== Loading/Error state =====

    /** Loading indicator */
    val isLoading: Boolean = true,

    /** Error message if loading/saving failed */
    val snackbarMessage: SnackbarMessage? = null
)

/**
 * Extension function to get display name for a TTPreset
 * Returns localized string from nameRes or custom name
 */
fun TTPreset.getDisplayName(rh: ResourceHelper): String =
    nameRes?.let { rh.gs(it) } ?: name ?: "Unknown"
