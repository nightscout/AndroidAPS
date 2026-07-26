package app.aaps.ui.compose.wizardDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
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
import app.aaps.core.objects.runningMode.PumpCommandGate
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.ui.compose.icons.IcCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs

@HiltViewModel
@Stable
class WizardDialogViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bolusWizardProvider: Provider<BolusWizard>,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    private val profileRepository: ProfileRepository,
    private val activePlugin: ActivePlugin,
    private val ch: ConcentrationHelper,
    private val iobCobCalculator: IobCobCalculator,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val config: Config,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger,
    private val runningModeGuard: RunningModeGuard,
    private val automation: Automation,
    private val wizardExecutor: WizardExecutor,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(WizardDialogUiState())
    val uiState: StateFlow<WizardDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data class ShowTempBasalError(val comment: String) : SideEffect()

        /** Close the wizard after the user confirmed the delivery (like the insulin dialog); Cancel keeps it open. */
        data object NavigateBack : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    private var wizard: BolusWizard? = null

    init {
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        val initialCarbs = savedStateHandle.get<String>("carbs")?.toIntOrNull()
        val initialNotes = savedStateHandle.get<String>("notes")

        val profileStore = profileRepository.profile.value ?: return

        val units = profileFunction.getUnits()

        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val maxBolus = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())

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
        persistenceLayer.getLastGlucoseValue().let {
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
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val totalIOB = bolusIob.iob + basalIob.basaliob

        val cantDeliverBolus = runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS) != null
        // An AAPSCLIENT always delivers via the master (deliverManualWizard), so it is NEVER forced record-only;
        // only a master's own can't-deliver conditions force the local record-only log (matches Insulin/Treatment dialog).
        val forcedRecordOnly = if (config.AAPSCLIENT) false else (cantDeliverBolus || !activePlugin.activePump.isInitialized())

        _uiState.update {
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
                carbsButtonIncrement1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1),
                carbsButtonIncrement2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2),
                carbsButtonIncrement3 = preferences.get(IntKey.OverviewCarbsButtonIncrement3),
                // BG card
                hasBgData = hasBgData,
                bgAgeMinutes = bgAgeMinutes,
                // Initial IOB display
                totalIOB = -totalIOB,
                forcedRecordOnly = forcedRecordOnly
            )
        }

        recalculate()
    }

    // --- Input update methods ---

    fun updateBg(value: Double) {
        val state = uiState.value
        val range = if (state.isMgdl) 0.0..500.0 else 0.0..30.0
        val clamped = value.coerceIn(range)
        _uiState.update { it.copy(bg = clamped) }
        recalculate()
    }

    fun updateCarbs(value: Int) {
        val state = uiState.value
        val clamped = value.coerceIn(0, state.maxCarbs)
        _uiState.update { it.copy(carbs = clamped) }
        recalculate()
    }

    fun addCarbs(increment: Int) {
        val state = uiState.value
        val newValue = (state.carbs + increment).coerceIn(0, state.maxCarbs)
        _uiState.update { it.copy(carbs = newValue) }
        recalculate()
    }

    fun updatePercentage(value: Int) {
        _uiState.update { it.copy(percentage = value) }
        recalculate()
    }

    fun updateDirectCorrection(value: Double) {
        val state = uiState.value
        val clamped = value.coerceIn(-state.maxBolus, state.maxBolus)
        _uiState.update { it.copy(directCorrection = clamped) }
        recalculate()
    }

    fun updateCarbTime(value: Int) {
        val clamped = value.coerceIn(-60, 60)
        _uiState.update { it.copy(carbTime = clamped) }
        recalculate()
    }

    fun updateCarbsType(value: CarbsType) {
        _uiState.update { it.copy(carbsType = value) }
        recalculate()
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun selectProfile(index: Int) {
        _uiState.update { it.copy(selectedProfileIndex = index) }
        recalculate()
    }

    // --- Toggle methods ---

    fun toggleBg(checked: Boolean) {
        _uiState.update {
            it.copy(
                useBg = checked,
                // TT depends on BG being checked
                useTT = if (!checked) false else it.useTT
            )
        }
        recalculate()
    }

    fun toggleTT(checked: Boolean) {
        _uiState.update { it.copy(useTT = checked) }
        recalculate()
    }

    fun toggleTrend(checked: Boolean) {
        _uiState.update { it.copy(useTrend = checked) }
        savePreferences()
        recalculate()
    }

    fun toggleIOB(checked: Boolean) {
        _uiState.update {
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
        _uiState.update {
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
        _uiState.update { it.copy(alarmChecked = checked) }
    }

    fun toggleAdvancedExpanded() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun toggleCalculationExpanded() {
        _uiState.update { it.copy(calculationExpanded = !it.calculationExpanded) }
    }

    fun refreshAfterSettings() {
        // Re-read preferences that may have changed in settings sheet
        val useTrend = preferences.get(BooleanNonKey.WizardIncludeTrend)
        val useCOB = preferences.get(BooleanNonKey.WizardIncludeCob)
        val useBolusAdvisor = preferences.get(BooleanKey.OverviewUseBolusAdvisor)
        val percentage = preferences.get(IntKey.OverviewBolusPercentage)
        _uiState.update {
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
        val profileStore = profileRepository.profile.value ?: return

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
            val signedTrendValue = (if (w.data.trend > 0) "+" else "") +
                profileUtil.fromMgdlToStringInUnits(w.data.trend * 3, state.units)
            rh.gs(app.aaps.core.ui.R.string.wizard_trend_detail, signedTrendValue, profileUtil.unitLabel)
        } else ""

        _uiState.update {
            it.copy(
                // Calculation results
                insulinFromBG = w.data.insulinFromBG,
                insulinFromTrend = w.data.insulinFromTrend,
                insulinFromCarbs = w.data.insulinFromCarbs,
                insulinFromCOB = w.data.insulinFromCOB,
                insulinFromBolusIOB = w.data.insulinFromBolusIOB,
                insulinFromBasalIOB = w.data.insulinFromBasalIOB,
                insulinFromCorrection = w.data.insulinFromCorrection,
                trendDetail = trendDetail,
                totalInsulin = w.data.calculatedTotalInsulin,
                totalBeforePercentage = w.data.totalBeforePercentageAdjustment,
                insulinAfterConstraints = w.data.insulinAfterConstraints,
                carbsEquivalent = w.data.carbsEquivalent,
                calculatedPercentage = w.data.calculatedPercentage,
                constraintApplied = abs(w.data.insulinAfterConstraints - w.data.calculatedTotalInsulin) >
                    ch.bolusStep(w.data.insulinAfterConstraints),
                isf = w.data.sens,
                ic = w.data.ic,
                currentCOB = cob,
                totalIOB = -(w.data.insulinFromBolusIOB + w.data.insulinFromBasalIOB),
                trend = w.data.trend,
                targetBGLow = 0.0, // not exposed directly
                targetBGHigh = 0.0,
                hasResult = true,
                okVisible = w.data.calculatedTotalInsulin > 0.0 || carbsAfterConstraint > 0,
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

    fun getConfirmationSummary(): List<ConfirmationLine> {
        val state = uiState.value
        return wizard?.buildConfirmationLines(
            advisor = false,
            eCarbsGrams = state.eCarbs,
            eCarbsDelayMinutes = state.eCarbsDelayMinutes + state.carbTime,
            eCarbsDurationHours = state.eCarbsDurationHours,
            forcedRecordOnly = state.forcedRecordOnly
        ) ?: emptyList()
    }

    /**
     * Record-only path (MASTER only — a client always delivers via the master). Used when the master can't deliver
     * (pump not initialized / mode forbids a bolus): log the wizard-calculated treatment + its BolusCalculatorResult
     * locally via [BolusWizard.executeNormal] with forcedRecordOnly. The DELIVERY path is [deliverManualWizard].
     * appScope: the screen pops on confirm, which would cancel viewModelScope before the write runs.
     */
    fun recordOnly() {
        val state = uiState.value
        appScope.launch {
            wizard?.executeNormal(
                onError = { comment -> _sideEffect.tryEmit(SideEffect.ShowDeliveryError(comment)) },
                eCarbsGrams = state.eCarbs,
                eCarbsDelayMinutes = state.eCarbsDelayMinutes + state.carbTime,
                eCarbsDurationHours = state.eCarbsDurationHours,
                forcedRecordOnly = true
            )
        }
    }

    fun savePreferences() {
        val state = uiState.value
        preferences.put(BooleanNonKey.WizardIncludeCob, state.useCOB)
        preferences.put(BooleanNonKey.WizardIncludeTrend, state.useTrend)
    }

    /**
     * Deliver the manual wizard bolus role-transparently via [WizardExecutor]: the master recomputes the dose on its
     * OWN profile (the dialog's profile selection travels), temp target, COB and IOB, caps it, and authors the
     * confirmation. Both roles render the master's EXACT lines via the shared [showWizardBolusConfirmation]; the user's
     * OK commits (the advisor fork chooses correction-only). No dose is ever computed on a client. This is the DELIVERY
     * path; a master that can't deliver records locally via [recordOnly] (master-only — a client always delivers).
     * appScope, not viewModelScope: the screen pops right after this call.
     */
    fun deliverManualWizard() {
        val state = uiState.value
        val effectiveCarbs = state.carbs * state.carbsType.carbsPercent / 100
        // null → recompute on the master's active profile (index 0 = "Active"); else the selected stored profile by name.
        val profileName = if (state.selectedProfileIndex == 0) null else state.profileNames.getOrNull(state.selectedProfileIndex)
        val inputs = WizardBolusExecutor.WizardInputs(
            bg = state.bg, carbs = effectiveCarbs, percentage = state.percentage, directCorrection = state.directCorrection,
            carbTime = state.carbTime, useBg = state.useBg, useCob = state.useCOB, useIob = state.useIOB,
            useTt = state.useTT, useTrend = state.useTrend, alarm = state.alarmChecked, notes = state.notes,
            eCarbsGrams = state.eCarbs, eCarbsDelayMinutes = state.eCarbsDelayMinutes + state.carbTime, eCarbsDurationHours = state.eCarbsDurationHours,
            profileName = profileName
        )
        val label = rh.gs(app.aaps.core.ui.R.string.clientcontrol_action_deliver_bolus)
        appScope.launch {
            when (val prepared = wizardExecutor.prepare(WizardExecutor.WizardSource.Manual(inputs), label)) {
                is ActionProgress.Prepared ->
                    showWizardBolusConfirmation(rxBus, rh, rh.gs(app.aaps.core.ui.R.string.boluswizard), IcCalculator, prepared.advisorApplies, prepared.lines, prepared.advisorLines) { asAdvisor ->
                        // Confirmed → close the wizard (Cancel leaves it open with inputs intact, like the insulin dialog).
                        _sideEffect.tryEmit(SideEffect.NavigateBack)
                        appScope.launch {
                            val result = wizardExecutor.commit(prepared.id, asAdvisor, Sources.WizardDialog, label)
                            // Device-local reminder cleanup on the ACTING device, mirroring the Carbs/Insulin dialogs:
                            // these one-shot Automation "remind me to bolus/eat" events live on the phone the user acted
                            // on, so the client must clear its own — confirm() runs on the master and can't reach them.
                            // (The "time to eat" alarm itself is still scheduled by confirm() on the master.) Only on a
                            // confirmed delivery; gated on the master's authoritative computed insulin/carbs.
                            if (result is ActionProgress.Applied) {
                                val detail = prepared.wizardDetail
                                if ((detail?.totalInsulin ?: 0.0) > 0.0) automation.removeAutomationEventBolusReminder()
                                // Advisor = "correct now, eat later" re-schedules an eat reminder, so don't clear it here.
                                if (!asAdvisor && (detail?.carbs ?: 0) > 0) automation.removeAutomationEventEatReminder()
                            }
                        }
                    }
                // Master-local compute failure (no modal) or client offline; a client round-trip failure already showed on the app modal.
                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.boluswizard), message = prepared.detail ?: rh.gs(prepared.reason.failTextResId())))

                else                       -> Unit // Unconfirmed → app modal
            }
        }
    }
}
