package app.aaps.pump.omnipod.omnipod5.ui.wizard.compose
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import app.aaps.pump.omnipod.common.R

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.omnipod.omnipod5.O5PumpPlugin
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandInitializePod
import app.aaps.pump.omnipod.common.queue.command.CommandInsertCannula
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.rx.bus.RxBus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.rxSingle
import app.aaps.pump.omnipod.common.R as CommonR

/**
 * The pod wizard of Omnipod 5: activation (pairing, priming, cannula) and deactivation.
 *
 * The wizard does not talk to the pod. Each step that does is one command of the queue, and [O5PumpPlugin] runs it.
 * A step that failed can be run again from the retry button: the driver does not send the commands the pod already
 * took.
 */
@Stable
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class O5OmnipodWizardViewModel @Inject constructor(
    private val o5PumpPlugin: O5PumpPlugin,
    private val podStateManager: O5PodStateManager,
    private val preferences: Preferences,
    rh: ResourceHelper,
    private val commandQueue: CommandQueue,
    insulinManager: InsulinManager,
    persistenceLayer: PersistenceLayer,
    profileFunction: ProfileFunction,
    profileRepository: ProfileRepository,
    pumpEnactResultProvider: () -> PumpEnactResult,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus
) : OmnipodWizardViewModel(
    logger,
    aapsSchedulers,
    pumpEnactResultProvider,
    profileFunction,
    profileRepository,
    insulinManager,
    persistenceLayer,
    rh,
    rxBus
) {

    public override val pumpSource: Sources = Sources.Omnipod5

    init {
        initializeWizard()
    }

    override val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    override val showSiteLocationStep: Boolean
        get() = preferences.get(BooleanKey.SiteRotationManagePump)

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))


    // Each action is a command of the queue: the driver talks to the pod, not this screen.

    override fun doInitializePod(): Single<PumpEnactResult> = rxSingle {
        commandQueue.customCommand(CommandInitializePod())
    }

    override fun doInsertCannula(): Single<PumpEnactResult> = rxSingle {
        commandQueue.customCommand(CommandInsertCannula())
    }

    override fun doDeactivatePod(): Single<PumpEnactResult> = rxSingle {
        commandQueue.customCommand(CommandDeactivatePod())
    }



    override fun discardPod() {
        o5PumpPlugin.removeDiscardedPod()
    }

    override fun isPodInAlarm(): Boolean = podStateManager.alarmType != null

    override fun isPodActivationTimeExceeded(): Boolean = podStateManager.isPodActivationTimeExceeded

    override fun isPodDeactivatable(): Boolean =
        podStateManager.ltk != null && podStateManager.controllerId != null && podStateManager.podId != null



    @StringRes
    override fun getTitleForStep(step: OmnipodWizardStep): Int = when (step) {
        OmnipodWizardStep.PROFILE_GATE           -> app.aaps.core.ui.R.string.pump_wizard_profile_gate_title
        OmnipodWizardStep.START_POD_ACTIVATION   -> CommonR.string.omnipod_common_pod_activation_wizard_start_pod_activation_title
        OmnipodWizardStep.SELECT_INSULIN         -> app.aaps.core.ui.R.string.select_insulin
        OmnipodWizardStep.INITIALIZE_POD         -> CommonR.string.omnipod_common_pod_activation_wizard_initialize_pod_title
        OmnipodWizardStep.SITE_LOCATION          -> app.aaps.core.ui.R.string.site_location
        OmnipodWizardStep.ATTACH_POD             -> CommonR.string.omnipod_common_pod_activation_wizard_attach_pod_title
        OmnipodWizardStep.INSERT_CANNULA         -> CommonR.string.omnipod_common_pod_activation_wizard_insert_cannula_title
        OmnipodWizardStep.POD_ACTIVATED          -> CommonR.string.omnipod_common_pod_activation_wizard_pod_activated_title
        OmnipodWizardStep.START_POD_DEACTIVATION -> CommonR.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_title
        OmnipodWizardStep.DEACTIVATE_POD         -> CommonR.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_title
        OmnipodWizardStep.POD_DEACTIVATED        -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_title
        OmnipodWizardStep.POD_DISCARDED          -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_discarded_title
    }

    @StringRes
    override fun getTextForStep(step: OmnipodWizardStep): Int = when (step) {
        OmnipodWizardStep.PROFILE_GATE           -> 0
        OmnipodWizardStep.START_POD_ACTIVATION   -> CommonR.string.omnipod_5_pod_activation_wizard_start_pod_activation_text
        OmnipodWizardStep.SELECT_INSULIN         -> app.aaps.core.ui.R.string.select_insulin_description
        OmnipodWizardStep.INITIALIZE_POD         -> CommonR.string.omnipod_5_pod_activation_wizard_initialize_pod_text
        OmnipodWizardStep.SITE_LOCATION          -> app.aaps.core.ui.R.string.select_site_location
        OmnipodWizardStep.ATTACH_POD             -> CommonR.string.omnipod_common_pod_activation_wizard_attach_pod_text
        OmnipodWizardStep.INSERT_CANNULA         -> CommonR.string.omnipod_common_pod_activation_wizard_insert_cannula_text
        OmnipodWizardStep.POD_ACTIVATED          -> CommonR.string.omnipod_common_pod_activation_wizard_pod_activated_text
        OmnipodWizardStep.START_POD_DEACTIVATION -> CommonR.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_text
        OmnipodWizardStep.DEACTIVATE_POD         -> CommonR.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
        OmnipodWizardStep.POD_DEACTIVATED        -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_text
        OmnipodWizardStep.POD_DISCARDED          -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_discarded_text
    }

}
