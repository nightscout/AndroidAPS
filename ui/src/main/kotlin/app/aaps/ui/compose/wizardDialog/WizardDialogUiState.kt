package app.aaps.ui.compose.wizardDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit

enum class CarbsType(
    val carbsPercent: Int,
    val eCarbsPercent: Int,
    val eCarbsDelayMinutes: Int,
    val eCarbsDurationHours: Int
) {

    BREAD(carbsPercent = 100, eCarbsPercent = 0, eCarbsDelayMinutes = 0, eCarbsDurationHours = 0),
    CAKE(carbsPercent = 90, eCarbsPercent = 20, eCarbsDelayMinutes = 60, eCarbsDurationHours = 2),
    PIZZA(carbsPercent = 80, eCarbsPercent = 35, eCarbsDelayMinutes = 60, eCarbsDurationHours = 3)
}

@Immutable
data class WizardDialogUiState(
    // User inputs
    val bg: Double = 0.0,
    val carbs: Int = 0,
    val carbsType: CarbsType = CarbsType.BREAD,
    val percentage: Int = 100,
    val directCorrection: Double = 0.0,
    val carbTime: Int = 0,
    val notes: String = "",
    val selectedProfileIndex: Int = 0,

    // Toggles
    val useBg: Boolean = true,
    val useTT: Boolean = false,
    val useTrend: Boolean = false,
    val useIOB: Boolean = true,
    val useCOB: Boolean = false,
    val alarmChecked: Boolean = false,
    val advancedExpanded: Boolean = false,
    val calculationExpanded: Boolean = false,

    // Config (set once in init)
    val maxCarbs: Int = 0,
    val maxBolus: Double = 0.0,
    val bolusStep: Double = 0.1,
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val profileNames: List<String> = emptyList(),
    val showNotes: Boolean = false,
    val hasTempTarget: Boolean = false,
    val useBolusAdvisor: Boolean = false,
    val defaultPercentage: Int = 100,
    val simpleMode: Boolean = false,
    val carbsButtonIncrement1: Int = 0,
    val carbsButtonIncrement2: Int = 0,
    val carbsButtonIncrement3: Int = 0,

    // Calculation results (updated on every recalc)
    val insulinFromBG: Double = 0.0,
    val insulinFromTrend: Double = 0.0,
    val insulinFromCarbs: Double = 0.0,
    val insulinFromCOB: Double = 0.0,
    val insulinFromBolusIOB: Double = 0.0,
    val insulinFromBasalIOB: Double = 0.0,
    val insulinFromCorrection: Double = 0.0,
    val trendDetail: String = "",
    val totalInsulin: Double = 0.0,
    val totalBeforePercentage: Double = 0.0,
    val insulinAfterConstraints: Double = 0.0,
    val carbsEquivalent: Double = 0.0,
    val calculatedPercentage: Int = 100,
    val constraintApplied: Boolean = false,
    val isf: Double = 0.0,
    val ic: Double = 0.0,
    val currentCOB: Double = 0.0,
    val totalIOB: Double = 0.0,
    val trend: Double = 0.0,
    val targetBGLow: Double = 0.0,
    val targetBGHigh: Double = 0.0,
    val hasResult: Boolean = false,
    val okVisible: Boolean = false,

    // Carbs type split (computed from carbs + carbsType)
    val effectiveCarbs: Int = 0,
    val eCarbs: Int = 0,
    val eCarbsDelayMinutes: Int = 0,
    val eCarbsDurationHours: Int = 0,

    // BG source info
    val hasBgData: Boolean = false,
    val bgAgeMinutes: Int = 0
)

val WizardDialogUiState.isMgdl: Boolean
    get() = units == GlucoseUnit.MGDL

val WizardDialogUiState.bgRange: ClosedFloatingPointRange<Double>
    get() = if (isMgdl) 0.0..500.0 else 0.0..30.0

val WizardDialogUiState.bgStep: Double
    get() = if (isMgdl) 1.0 else 0.1

val WizardDialogUiState.activeProfileName: String
    get() = profileNames.getOrElse(selectedProfileIndex) { "Active" }

val WizardDialogUiState.alarmEnabled: Boolean
    get() = (carbs > 0 && carbTime > 0) || eCarbs > 0
