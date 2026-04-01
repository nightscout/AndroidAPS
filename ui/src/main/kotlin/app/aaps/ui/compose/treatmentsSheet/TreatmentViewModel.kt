package app.aaps.ui.compose.treatmentsSheet

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.ui.compose.main.QuickWizardItem
import app.aaps.ui.compose.navigation.ElementAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
@Stable
class TreatmentViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val constraintChecker: ConstraintsChecker,
    private val quickWizard: QuickWizard,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val dexcomBoyda: DexcomBoyda,
    private val elementAvailability: ElementAvailability
) : ViewModel() {

    val uiState: StateFlow<TreatmentUiState>
        field = MutableStateFlow(TreatmentUiState())

    init {
        setupEventListeners()
        refreshState()
    }

    private fun setupEventListeners() {
        merge(
            preferences.observe(BooleanKey.OverviewShowCgmButton).drop(1).map {},
            preferences.observe(BooleanKey.OverviewShowCalibrationButton).drop(1).map {},
            preferences.observe(BooleanKey.OverviewShowTreatmentButton).drop(1).map {},
            preferences.observe(BooleanKey.OverviewShowInsulinButton).drop(1).map {},
            preferences.observe(BooleanKey.OverviewShowCarbsButton).drop(1).map {},
            preferences.observe(BooleanKey.OverviewShowWizardButton).drop(1).map {},
            preferences.observe(BooleanKey.GeneralSimpleMode).drop(1).map {},
        ).onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventRefreshOverview::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
    }

    fun refreshState() {
        viewModelScope.launch {
            val isDexcomSource = dexcomBoyda.isEnabled()

            val quickWizardItems = buildQuickWizardItems()

            // Check element availability (plugin-dependent) + user preference
            val showCgm = elementAvailability.isAvailable(ElementType.CGM_XDRIP) && preferences.get(BooleanKey.OverviewShowCgmButton)
            val showCalibration = elementAvailability.isAvailable(ElementType.CALIBRATION)
                && iobCobCalculator.ads.actualBg() != null
                && preferences.get(BooleanKey.OverviewShowCalibrationButton)
            val showTreatment = preferences.get(BooleanKey.OverviewShowTreatmentButton)
            val showInsulin = preferences.get(BooleanKey.OverviewShowInsulinButton)
            val showCarbs = preferences.get(BooleanKey.OverviewShowCarbsButton)
            val showCalculator = preferences.get(BooleanKey.OverviewShowWizardButton)

            val showSettingsIcon = !preferences.simpleMode

            uiState.update { state ->
                state.copy(
                    showCgm = showCgm,
                    showCalibration = showCalibration,
                    showTreatment = showTreatment,
                    showInsulin = showInsulin,
                    showCarbs = showCarbs,
                    showCalculator = showCalculator,
                    isDexcomSource = isDexcomSource,
                    quickWizardItems = quickWizardItems,
                    showSettingsIcon = showSettingsIcon
                )
            }
        }
    }

    private suspend fun buildQuickWizardItems(): List<QuickWizardItem> {
        val activeEntries = quickWizard.list().filter { it.isActive() }
        if (activeEntries.isEmpty()) return emptyList()

        val lastBG = iobCobCalculator.ads.lastBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump
        val runningMode = loop.runningMode

        return activeEntries.map { entry ->
            when (entry.mode()) {
                QuickWizardMode.INSULIN -> buildInsulinItem(entry, pump, runningMode)
                QuickWizardMode.CARBS   -> buildCarbsItem(entry)
                QuickWizardMode.WIZARD  -> buildWizardItem(entry, lastBG, profile, profileName, pump, runningMode)
            }
        }
    }

    private fun buildInsulinItem(entry: QuickWizardEntry, pump: Pump, runningMode: RM.Mode?): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()
        val detail = rh.gs(R.string.format_insulin_units, entry.insulin())

        val disabledReason = when {
            !pump.isInitialized()                    -> rh.gs(R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(R.string.pump_disconnected)
            else                                     -> null
        }
        if (disabledReason != null)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = disabledReason)

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(entry.insulin(), aapsLogger)).value()
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - entry.insulin()) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    private fun buildCarbsItem(entry: QuickWizardEntry): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()
        val detail = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs())

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    private suspend fun buildWizardItem(
        entry: QuickWizardEntry,
        lastBG: InMemoryGlucoseValue?,
        profile: Profile?,
        profileName: String,
        pump: Pump,
        runningMode: RM.Mode?
    ): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()

        val globalReason = when {
            lastBG == null                           -> rh.gs(R.string.wizard_no_actual_bg)
            profile == null                          -> rh.gs(R.string.noprofile)
            !pump.isInitialized()                    -> rh.gs(R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(R.string.pump_disconnected)
            else                                     -> null
        }
        if (globalReason != null)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, disabledReason = globalReason)

        val wizard = entry.doCalc(profile!!, profileName, lastBG!!)
        if (wizard.calculatedTotalInsulin <= 0.0)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, disabledReason = rh.gs(app.aaps.ui.R.string.wizard_no_insulin_required))

        val detail = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs()) +
            " " + rh.gs(R.string.format_insulin_units, wizard.calculatedTotalInsulin)

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints)
        if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }
}
