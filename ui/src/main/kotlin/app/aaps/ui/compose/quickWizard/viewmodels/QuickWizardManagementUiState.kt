package app.aaps.ui.compose.quickWizard.viewmodels

import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage

/**
 * UI state for QuickWizard management screen
 */
data class QuickWizardManagementUiState(
    val isLoading: Boolean = true,
    val entries: List<QuickWizardEntry> = emptyList(),
    val selectedIndex: Int = 0,
    val selectedGuid: String = "",
    val currentCardIndex: Int = 0,
    val hasUnsavedChanges: Boolean = false,
    val snackbarMessage: SnackbarMessage? = null,

    // Editor fields (match EditQuickWizardDialog fields)
    val editorMode: QuickWizardMode = QuickWizardMode.WIZARD,
    val editorButtonText: String = "",
    val editorInsulin: Double = 0.0,
    val editorCarbs: Int = 0,
    val editorCarbTime: Int = 0,
    val editorValidFrom: Int = 0,           // seconds from midnight
    val editorValidTo: Int = 86340,         // seconds from midnight (23:59)
    val editorUseBG: Boolean = true,
    val editorUseCOB: Boolean = false,
    val editorUseIOB: Boolean = true,
    val editorUsePositiveIOBOnly: Boolean = false,
    val editorUseTrend: TrendOption = TrendOption.NO,
    val editorUseSuperBolus: Boolean = false,
    val editorUseTempTarget: Boolean = false,
    val editorUseAlarm: Boolean = false,
    val editorPercentage: Int = 100,
    val editorDevicePhone: Boolean = true,
    val editorDeviceWatch: Boolean = true,
    val editorUseEcarbs: Boolean = false,
    val editorTime: Int = 0,                // eCarbs time in minutes
    val editorDuration: Int = 0,            // eCarbs duration in hours
    val editorCarbs2: Int = 0,              // eCarbs additional carbs

    // UI control
    val showNotesField: Boolean = false,
    val showSuperBolusOption: Boolean = false,
    val showWearOptions: Boolean = false,

    // Screen mode
    val screenMode: ScreenMode = ScreenMode.EDIT
)

/**
 * Trend options matching QuickWizardEntry constants
 */
enum class TrendOption {

    NO,             // QuickWizardEntry.NO = 1
    YES,            // QuickWizardEntry.YES = 0
    POSITIVE_ONLY,  // QuickWizardEntry.POSITIVE_ONLY = 2
    NEGATIVE_ONLY   // QuickWizardEntry.NEGATIVE_ONLY = 3
}

/**
 * Convert TrendOption enum to QuickWizardEntry constant
 */
fun TrendOption.toInt(): Int = when (this) {
    TrendOption.YES           -> QuickWizardEntry.YES
    TrendOption.NO            -> QuickWizardEntry.NO
    TrendOption.POSITIVE_ONLY -> QuickWizardEntry.POSITIVE_ONLY
    TrendOption.NEGATIVE_ONLY -> QuickWizardEntry.NEGATIVE_ONLY
}

/**
 * Convert QuickWizardEntry constant to TrendOption enum
 */
fun Int.toTrendOption(): TrendOption = when (this) {
    QuickWizardEntry.YES           -> TrendOption.YES
    QuickWizardEntry.POSITIVE_ONLY -> TrendOption.POSITIVE_ONLY
    QuickWizardEntry.NEGATIVE_ONLY -> TrendOption.NEGATIVE_ONLY
    else                           -> TrendOption.NO
}
