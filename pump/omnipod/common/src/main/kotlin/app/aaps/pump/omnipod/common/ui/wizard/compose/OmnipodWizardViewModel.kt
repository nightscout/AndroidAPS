package app.aaps.pump.omnipod.common.ui.wizard.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.ui.compose.siteRotation.SiteLocationStepHost
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Provider

/**
 * Activation type: LONG (full 5-step) or SHORT (3-step, resume after prime).
 */
enum class ActivationType {

    LONG, SHORT
}

/**
 * Wizard type: activation or deactivation flow.
 */
enum class WizardType {

    ACTIVATION, DEACTIVATION
}

/**
 * Action execution state for action steps (InitializePod, InsertCannula, DeactivatePod).
 */
sealed class ActionState {

    data object Idle : ActionState()
    data object Executing : ActionState()
    data class Success(val result: PumpEnactResult) : ActionState()
    data class Error(val message: String) : ActionState()
}

/**
 * Abstract shared ViewModel for Omnipod activation/deactivation wizard.
 * Used by both Eros and Dash — pump-specific logic is in abstract methods.
 *
 * All RxJava pump communication code (doInitializePod, doInsertCannula, doDeactivatePod)
 * remains untouched. This VM only bridges RxJava to StateFlow for Compose observation.
 */
@Stable
abstract class OmnipodWizardViewModel(
    protected val logger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    protected val pumpEnactResultProvider: Provider<PumpEnactResult>
) : ViewModel(), SiteLocationStepHost {

    // region Step navigation

    private val _currentStep = MutableStateFlow<OmnipodWizardStep?>(null)
    val currentStep: StateFlow<OmnipodWizardStep?> = _currentStep

    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private var wizardPages: List<OmnipodWizardStep> = emptyList()
    private var wizardType: WizardType = WizardType.ACTIVATION

    /** Set to true by concrete VMs after init (insulins loaded, site rotation entries cached). */
    protected val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    // endregion

    // region Action state

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

    protected val disposable = CompositeDisposable()

    // endregion

    // region Events

    private val _events = MutableSharedFlow<OmnipodWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<OmnipodWizardEvent> = _events

    // endregion

    // region Insulin selection

    private val _availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    val availableInsulins: StateFlow<List<ICfg>> = _availableInsulins

    private val _selectedInsulin = MutableStateFlow<ICfg?>(null)
    val selectedInsulin: StateFlow<ICfg?> = _selectedInsulin

    private val _activeInsulinLabel = MutableStateFlow<String?>(null)
    val activeInsulinLabel: StateFlow<String?> = _activeInsulinLabel

    /** Whether insulin selection step should be shown (more than 1 insulin configured). */
    val showInsulinStep: Boolean get() = _availableInsulins.value.size > 1

    /** Whether concentration dropdown is enabled in preferences. */
    abstract val concentrationEnabled: Boolean

    fun selectInsulin(iCfg: ICfg) {
        _selectedInsulin.value = iCfg
    }

    /** Load available insulins. Called by concrete VMs during initialization. */
    protected fun loadInsulins(insulins: List<ICfg>, activeLabel: String?) {
        if (_availableInsulins.value.isNotEmpty()) return
        _availableInsulins.value = insulins
        _activeInsulinLabel.value = activeLabel
        _selectedInsulin.value = insulins.find { it.insulinLabel == activeLabel } ?: insulins.firstOrNull()
    }

    // endregion

    // region Site location (SiteLocationStepHost)

    private val _siteLocation = MutableStateFlow(TE.Location.NONE)
    override val siteLocation: StateFlow<TE.Location> = _siteLocation

    private val _siteArrow = MutableStateFlow(TE.Arrow.NONE)
    override val siteArrow: StateFlow<TE.Arrow> = _siteArrow

    override fun updateSiteLocation(location: TE.Location) {
        _siteLocation.value = location
    }

    override fun updateSiteArrow(arrow: TE.Arrow) {
        _siteArrow.value = arrow
    }

    override fun completeSiteLocation() {
        moveToNext()
    }

    override fun skipSiteLocation() {
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        moveToNext()
    }

    /** Whether site location step should be shown. */
    abstract val showSiteLocationStep: Boolean

    /** Get the selected site location (for persisting after activation). */
    fun getSelectedSiteLocation(): TE.Location = _siteLocation.value

    /** Get the selected site arrow (for persisting after activation). */
    fun getSelectedSiteArrow(): TE.Arrow = _siteArrow.value

    // endregion

    // region Initialization

    fun initializeActivation(type: ActivationType) {
        wizardType = WizardType.ACTIVATION
        wizardPages = buildList {
            if (type == ActivationType.LONG) {
                add(OmnipodWizardStep.START_POD_ACTIVATION)
                if (showInsulinStep) add(OmnipodWizardStep.SELECT_INSULIN)
                add(OmnipodWizardStep.INITIALIZE_POD)
            }
            add(OmnipodWizardStep.ATTACH_POD)
            if (showSiteLocationStep) add(OmnipodWizardStep.SITE_LOCATION)
            add(OmnipodWizardStep.INSERT_CANNULA)
            add(OmnipodWizardStep.POD_ACTIVATED)
        }
        _totalSteps.value = wizardPages.size
        _currentStepIndex.value = 0
        _currentStep.value = wizardPages.first()
        _canGoBack.value = false
    }

    fun initializeDeactivation() {
        wizardType = WizardType.DEACTIVATION
        wizardPages = listOf(
            OmnipodWizardStep.START_POD_DEACTIVATION,
            OmnipodWizardStep.DEACTIVATE_POD,
            OmnipodWizardStep.POD_DEACTIVATED
        )
        _totalSteps.value = wizardPages.size
        _currentStepIndex.value = 0
        _currentStep.value = wizardPages.first()
        _canGoBack.value = false
    }

    // endregion

    // region Navigation

    fun moveToNext() {
        val currentIndex = _currentStepIndex.value
        if (currentIndex < wizardPages.size - 1) {
            _actionState.value = ActionState.Idle
            _currentStepIndex.value = currentIndex + 1
            _currentStep.value = wizardPages[currentIndex + 1]
            _canGoBack.value = false
        } else {
            _events.tryEmit(OmnipodWizardEvent.Finish)
        }
    }

    /**
     * Navigate to a specific step (used for discard-pod branching in deactivation).
     * Replaces the current step without changing index, since POD_DISCARDED replaces POD_DEACTIVATED.
     */
    fun moveToStep(step: OmnipodWizardStep) {
        _actionState.value = ActionState.Idle
        _currentStep.value = step
        _canGoBack.value = false
    }

    fun finish() {
        _events.tryEmit(OmnipodWizardEvent.Finish)
    }

    // endregion

    // region Action execution (RxJava bridge)

    /**
     * Execute the action for the current step. Auto-called by action step composables.
     * Bridges from RxJava Single to StateFlow.
     */
    fun executeAction() {
        val step = _currentStep.value ?: return
        _actionState.value = ActionState.Executing

        disposable += getActionForStep(step)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribeBy(
                onSuccess = { result ->
                    if (result.success) {
                        _actionState.value = ActionState.Success(result)
                    } else {
                        _actionState.value = ActionState.Error(
                            result.comment ?: "Unknown error"
                        )
                    }
                },
                onError = { throwable ->
                    logger.error(LTag.PUMP, "Error executing wizard action for step $step", throwable)
                    _actionState.value = ActionState.Error(
                        throwable.message ?: "Unexpected error"
                    )
                }
            )
    }

    private fun getActionForStep(step: OmnipodWizardStep): Single<PumpEnactResult> = when (step) {
        OmnipodWizardStep.INITIALIZE_POD -> doInitializePod()
        OmnipodWizardStep.INSERT_CANNULA -> doInsertCannula()
        OmnipodWizardStep.DEACTIVATE_POD -> doDeactivatePod()
        else                             -> Single.error(IllegalStateException("Not an action step: $step"))
    }

    // endregion

    // region Abstract methods — pump-specific

    /** Execute pod initialization (part 1). RxJava Single — DO NOT MODIFY in subclasses, copy-paste existing code. */
    protected abstract fun doInitializePod(): Single<PumpEnactResult>

    /** Execute cannula insertion (part 2). RxJava Single — DO NOT MODIFY in subclasses, copy-paste existing code. */
    protected abstract fun doInsertCannula(): Single<PumpEnactResult>

    /** Execute pod deactivation. RxJava Single — DO NOT MODIFY in subclasses, copy-paste existing code. */
    protected abstract fun doDeactivatePod(): Single<PumpEnactResult>

    /** Discard pod state without deactivation (force reset). */
    abstract fun discardPod()

    /** Whether the pod is currently in alarm state. */
    abstract fun isPodInAlarm(): Boolean

    /** Whether pod activation time has been exceeded. */
    abstract fun isPodActivationTimeExceeded(): Boolean

    /** Whether the pod can be deactivated (has progressed far enough in activation). */
    abstract fun isPodDeactivatable(): Boolean

    /** Get the title string resource for a given step. */
    @StringRes abstract fun getTitleForStep(step: OmnipodWizardStep): Int

    /** Get the body text string resource for a given step. */
    @StringRes abstract fun getTextForStep(step: OmnipodWizardStep): Int

    /** Execute insulin profile switch if insulin was changed during activation. */
    abstract fun executeInsulinProfileSwitch()

    /** Persist site location to therapy event after successful activation. */
    abstract fun saveSiteLocation()

    // endregion

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }
}
