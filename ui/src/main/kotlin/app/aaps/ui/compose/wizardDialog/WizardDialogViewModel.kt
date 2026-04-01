package app.aaps.ui.compose.wizardDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.wizard.BolusWizard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs

@HiltViewModel
@Stable
class WizardDialogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bolusWizardProvider: Provider<BolusWizard>,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    private val localProfileManager: LocalProfileManager,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val persistenceLayer: PersistenceLayer,
    val preferences: Preferences,
    val config: Config,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<WizardDialogUiState>
        field = MutableStateFlow(WizardDialogUiState())

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data class ShowTempBasalError(val comment: String) : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private var wizard: BolusWizard? = null

    init {
        val initialCarbs = savedStateHandle.get<String>("carbs")?.toIntOrNull()
        val initialNotes = savedStateHandle.get<String>("notes")

        val profileStore = localProfileManager.profile
        if (profileStore != null) {
            val units = profileFunction.getUnits()

            val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
            val maxBolus = constraintChecker.getMaxBolusAllowed().value()
            val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
            val tempTarget = runBlocking { persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) }

            // Build profile names list: "Active" + profile store names
            val profileList = mutableListOf(rh.gs(app.aaps.core.ui.R.string.active))
            profileList.addAll(profileStore.getProfileList().map { it.toString() })

            // Load saved preferences
            val useTrend = preferences.get(BooleanNonKey.WizardIncludeTrend)
            val useCOB = preferences.get(BooleanNonKey.WizardIncludeCob)
            val showNotes = preferences.get(BooleanKey.OverviewShowNotesInDialogs)
            val useBolusAdvisor = preferences.get(BooleanKey.OverviewUseBolusAdvisor)

            // Percentage: reset to 100% if last BG is too old
            var percentage = preferences.get(IntKey.OverviewBolusPercentage)
            val time = preferences.get(IntKey.OverviewResetBolusPercentageTime).toLong()
            runBlocking { persistenceLayer.getLastGlucoseValue() }.let {
                if (it != null) {
                    if (it.timestamp < dateUtil.now() - T.mins(time).msecs())
                        percentage = 100
                } else percentage = 100
            }

            // Current BG
            val actualBg = iobCobCalculator.ads.actualBg()
            val hasBgData = actualBg != null
            val currentBg = actualBg?.valueToUnits(units) ?: 0.0
            val bgAgeMinutes = if (actualBg != null) ((dateUtil.now() - actualBg.timestamp) / 60000).toInt() else 0

            // IOB for display
            val bolusIob = runBlocking { iobCobCalculator.calculateIobFromBolus() }.round()
            val basalIob = runBlocking { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() }.round()
            val totalIOB = bolusIob.iob + basalIob.basaliob

            uiState.update {
                WizardDialogUiState(
                    // User inputs
                    bg = currentBg,
                    carbs = initialCarbs ?: 0,
                    percentage = percentage,
                    directCorrection = 0.0,
                    carbTime = 0,
                    notes = initialNotes ?: "",
                    selectedProfileIndex = 0,
                    // Toggles
                    useBg = true,
                    useTT = true,
                    useTrend = useTrend,
                    useIOB = true,
                    useCOB = useCOB,
                    alarmChecked = false,
                    calculationExpanded = false,
                    // Config
                    maxCarbs = maxCarbs,
                    maxBolus = maxBolus,
                    bolusStep = bolusStep,
                    units = units,
                    profileNames = profileList,
                    showNotes = showNotes,
                    hasTempTarget = tempTarget != null,
                    useBolusAdvisor = useBolusAdvisor,
                    defaultPercentage = percentage,
                    simpleMode = preferences.simpleMode,
                    // BG card
                    hasBgData = hasBgData,
                    bgAgeMinutes = bgAgeMinutes,
                    // Initial IOB display
                    totalIOB = -totalIOB
                )
            }

            recalculate()
        }
    }

    // --- Input update methods ---

    fun updateBg(value: Double) {
        val state = uiState.value
        val range = if (state.isMgdl) 0.0..500.0 else 0.0..30.0
        val clamped = value.coerceIn(range)
        uiState.update { it.copy(bg = clamped) }
        recalculate()
    }

    fun updateCarbs(value: Int) {
        val state = uiState.value
        val clamped = value.coerceIn(0, state.maxCarbs)
        uiState.update { it.copy(carbs = clamped) }
        recalculate()
    }

    fun updatePercentage(value: Int) {
        val clamped = value.coerceIn(10, 200)
        uiState.update { it.copy(percentage = clamped) }
        recalculate()
    }

    fun updateDirectCorrection(value: Double) {
        val state = uiState.value
        val clamped = value.coerceIn(-state.maxBolus, state.maxBolus)
        uiState.update { it.copy(directCorrection = clamped) }
        recalculate()
    }

    fun updateCarbTime(value: Int) {
        val clamped = value.coerceIn(-60, 60)
        uiState.update {
            it.copy(
                carbTime = clamped,
                alarmChecked = clamped > 0
            )
        }
        recalculate()
    }

    fun updateCarbsType(value: CarbsType) {
        uiState.update { it.copy(carbsType = value) }
        recalculate()
    }

    fun updateNotes(value: String) {
        uiState.update { it.copy(notes = value) }
    }

    fun selectProfile(index: Int) {
        uiState.update { it.copy(selectedProfileIndex = index) }
        recalculate()
    }

    // --- Toggle methods ---

    fun toggleBg(checked: Boolean) {
        uiState.update {
            it.copy(
                useBg = checked,
                // TT depends on BG being checked
                useTT = if (!checked) false else it.useTT
            )
        }
        recalculate()
    }

    fun toggleTT(checked: Boolean) {
        uiState.update { it.copy(useTT = checked) }
        recalculate()
    }

    fun toggleTrend(checked: Boolean) {
        uiState.update { it.copy(useTrend = checked) }
        savePreferences()
        recalculate()
    }

    fun toggleIOB(checked: Boolean) {
        uiState.update {
            it.copy(
                useIOB = checked,
                // COB requires IOB
                useCOB = if (!checked) false else it.useCOB
            )
        }
        savePreferences()
        recalculate()
    }

    fun toggleCOB(checked: Boolean) {
        uiState.update {
            it.copy(
                useCOB = checked,
                // COB requires IOB
                useIOB = if (checked) true else it.useIOB
            )
        }
        savePreferences()
        recalculate()
    }

    fun toggleAlarm(checked: Boolean) {
        uiState.update { it.copy(alarmChecked = checked) }
    }

    fun toggleAdvancedExpanded() {
        uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun toggleCalculationExpanded() {
        uiState.update { it.copy(calculationExpanded = !it.calculationExpanded) }
    }

    fun refreshAfterSettings() {
        // Re-read preferences that may have changed in settings sheet
        val useTrend = preferences.get(BooleanNonKey.WizardIncludeTrend)
        val useCOB = preferences.get(BooleanNonKey.WizardIncludeCob)
        val useBolusAdvisor = preferences.get(BooleanKey.OverviewUseBolusAdvisor)
        val percentage = preferences.get(IntKey.OverviewBolusPercentage)
        uiState.update {
            it.copy(
                useTrend = useTrend,
                useCOB = useCOB,
                useBolusAdvisor = useBolusAdvisor,
                percentage = percentage,
                defaultPercentage = percentage
            )
        }
        recalculate()
    }

    // --- Calculation ---

    private fun recalculate() {
        viewModelScope.launch { recalculateSuspend() }
    }

    private suspend fun recalculateSuspend() {
        val state = uiState.value
        val profileStore = localProfileManager.profile ?: return

        // Resolve profile
        val profileName: String
        val specificProfile: app.aaps.core.interfaces.profile.Profile?
        if (state.selectedProfileIndex == 0) {
            specificProfile = profileFunction.getProfile()
            profileName = profileFunction.getProfileName()
        } else {
            val name = state.profileNames.getOrNull(state.selectedProfileIndex) ?: return
            profileName = name
            specificProfile = profileStore.getSpecificProfile(name)?.let { ProfileSealed.Pure(it, activePlugin) }
        }

        if (specificProfile == null) return

        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())

        // BG input: only pass if BG toggle is checked
        val bgInput = if (state.useBg) state.bg else 0.0

        // COB
        var cob = 0.0
        if (state.useCOB) {
            val cobInfo = iobCobCalculator.getCobInfo("Wizard COB")
            cobInfo.displayCob?.let { cob = it }
        }

        // Carbs type split: effective carbs for wizard, eCarbs for later scheduling
        val carbsType = state.carbsType
        val effectiveCarbs = state.carbs * carbsType.carbsPercent / 100
        val eCarbs = state.carbs * carbsType.eCarbsPercent / 100

        // Carbs constraint check (on effective carbs only)
        val carbsAfterConstraint = constraintChecker.applyCarbsConstraints(ConstraintObject(effectiveCarbs, aapsLogger)).value()

        // Direct correction value
        val correctionValue = state.directCorrection

        // Percentage is always applied
        val percentageCorrection = state.percentage

        val w = bolusWizardProvider.get().doCalc(
            specificProfile,
            profileName,
            tempTarget,
            carbsAfterConstraint,
            cob,
            bgInput,
            correctionValue,
            percentageCorrection,
            state.useBg,
            state.useCOB,
            state.useIOB,
            state.useIOB,
            false, // useSuperBolus - not available in Compose wizard
            state.useTT,
            state.useTrend,
            state.alarmChecked,
            state.notes,
            state.carbTime
        )

        wizard = w

        // Update temp target availability
        val hasTT = tempTarget != null

        // Format trend detail: signed 45-min BG projection
        val trendDetail = if (state.useTrend) {
            val signedTrendValue = (if (w.trend > 0) "+" else "") +
                profileUtil.fromMgdlToStringInUnits(w.trend * 3, state.units)
            rh.gs(app.aaps.core.ui.R.string.wizard_trend_detail, signedTrendValue, state.units.asText)
        } else ""

        uiState.update {
            it.copy(
                // Calculation results
                insulinFromBG = w.insulinFromBG,
                insulinFromTrend = w.insulinFromTrend,
                insulinFromCarbs = w.insulinFromCarbs,
                insulinFromCOB = w.insulinFromCOB,
                insulinFromBolusIOB = w.insulinFromBolusIOB,
                insulinFromBasalIOB = w.insulinFromBasalIOB,
                insulinFromCorrection = w.insulinFromCorrection,
                trendDetail = trendDetail,
                totalInsulin = w.calculatedTotalInsulin,
                totalBeforePercentage = w.totalBeforePercentageAdjustment,
                insulinAfterConstraints = w.insulinAfterConstraints,
                carbsEquivalent = w.carbsEquivalent,
                calculatedPercentage = w.calculatedPercentage,
                constraintApplied = abs(w.insulinAfterConstraints - w.calculatedTotalInsulin) >
                    activePlugin.activePump.pumpDescription.pumpType.determineCorrectBolusStepSize(w.insulinAfterConstraints),
                isf = w.sens,
                ic = w.ic,
                currentCOB = cob,
                totalIOB = -(w.insulinFromBolusIOB + w.insulinFromBasalIOB),
                trend = w.trend,
                targetBGLow = 0.0, // not exposed directly
                targetBGHigh = 0.0,
                hasResult = true,
                okVisible = w.calculatedTotalInsulin > 0.0 || carbsAfterConstraint > 0,
                hasTempTarget = hasTT,
                effectiveCarbs = effectiveCarbs,
                eCarbs = eCarbs,
                eCarbsDelayMinutes = carbsType.eCarbsDelayMinutes,
                eCarbsDurationHours = carbsType.eCarbsDurationHours
            )
        }
    }

    // --- Action methods ---

    fun hasAction(): Boolean =
        wizard?.let { it.insulinAfterConstraints > 0 || it.carbs > 0 || uiState.value.eCarbs > 0 } ?: false

    fun needsBolusAdvisor(): Boolean =
        wizard?.needsBolusAdvisor() ?: false

    fun getConfirmationSummary(): List<String> {
        val state = uiState.value
        return wizard?.buildConfirmationLines(
            advisor = false,
            eCarbsGrams = state.eCarbs,
            eCarbsDelayMinutes = state.eCarbsDelayMinutes + state.carbTime,
            eCarbsDurationHours = state.eCarbsDurationHours
        ) ?: emptyList()
    }

    fun getAdvisorSummary(): List<String> {
        val state = uiState.value
        return wizard?.buildConfirmationLines(
            advisor = true,
            eCarbsGrams = state.eCarbs,
            eCarbsDelayMinutes = state.eCarbsDelayMinutes + state.carbTime,
            eCarbsDurationHours = state.eCarbsDurationHours
        ) ?: emptyList()
    }

    fun executeNormal() {
        val state = uiState.value
        viewModelScope.launch {
            wizard?.executeNormal(
                onError = { comment ->
                    sideEffect.tryEmit(SideEffect.ShowDeliveryError(comment))
                },
                eCarbsGrams = state.eCarbs,
                eCarbsDelayMinutes = state.eCarbsDelayMinutes,
                eCarbsDurationHours = state.eCarbsDurationHours
            )
        }
    }

    fun executeBolusAdvisor() {
        val state = uiState.value
        wizard?.executeBolusAdvisor(
            onError = { comment ->
                sideEffect.tryEmit(SideEffect.ShowDeliveryError(comment))
            },
            eCarbsGrams = state.eCarbs,
            eCarbsDelayMinutes = state.eCarbsDelayMinutes,
            eCarbsDurationHours = state.eCarbsDurationHours
        )
    }

    fun savePreferences() {
        val state = uiState.value
        preferences.put(BooleanNonKey.WizardIncludeCob, state.useCOB)
        preferences.put(BooleanNonKey.WizardIncludeTrend, state.useTrend)
    }
}
