package app.aaps.pump.omnipod.dash.ui.wizard.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
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
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandInitializePod
import app.aaps.pump.omnipod.common.queue.command.CommandInsertCannula
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.rxSingle
import app.aaps.pump.omnipod.common.R as CommonR

@Stable
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Inject
class DashOmnipodWizardViewModel(
    private val omnipodDashPumpPlugin: OmnipodDashPumpPlugin,
    private val preferences: Preferences,
    rh: ResourceHelper,
    private val commandQueue: CommandQueue,
    private val fabricPrivacy: FabricPrivacy,
    insulinManager: InsulinManager,
    profileFunction: ProfileFunction,
    profileRepository: ProfileRepository,
    persistenceLayer: PersistenceLayer,
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

    override val pumpSource: Sources = Sources.OmnipodDash

    init {
        initializeWizard()
    }

    override val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    override val showSiteLocationStep: Boolean
        get() = preferences.get(BooleanKey.SiteRotationManagePump)

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))


    // region Actions. Each one is a command of the queue: the driver talks to the pod, not this screen.

    override fun doInitializePod(): Single<PumpEnactResult> = rxSingle {
        commandQueue.customCommand(CommandInitializePod())
    }

    override fun doInsertCannula(): Single<PumpEnactResult> = rxSingle {
        commandQueue.customCommand(CommandInsertCannula()).also {
            if (it.success) fabricPrivacy.logCustom("OmnipodDashPodActivated")
        }
    }

    override fun doDeactivatePod(): Single<PumpEnactResult> = rxSingle {
        commandQueue.customCommand(CommandDeactivatePod())
    }

    // endregion

    // region Pod state queries

    override fun discardPod() {
        omnipodDashPumpPlugin.removePod()
    }

    override fun isPodInAlarm(): Boolean = false // TODO: not yet implemented in Dash

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO: not yet implemented in Dash

    override fun isPodDeactivatable(): Boolean = true // TODO: not yet implemented in Dash

    // endregion

    // region String resources per step

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
        // PROFILE_GATE has its own composable that doesn't consume textResId — returned value is unused.
        OmnipodWizardStep.PROFILE_GATE           -> 0
        OmnipodWizardStep.START_POD_ACTIVATION   -> R.string.omnipod_dash_pod_activation_wizard_start_pod_activation_text
        OmnipodWizardStep.SELECT_INSULIN         -> app.aaps.core.ui.R.string.select_insulin_description
        OmnipodWizardStep.INITIALIZE_POD         -> R.string.omnipod_dash_pod_activation_wizard_initialize_pod_text
        OmnipodWizardStep.SITE_LOCATION          -> app.aaps.core.ui.R.string.select_site_location
        OmnipodWizardStep.ATTACH_POD             -> CommonR.string.omnipod_common_pod_activation_wizard_attach_pod_text
        OmnipodWizardStep.INSERT_CANNULA         -> CommonR.string.omnipod_common_pod_activation_wizard_insert_cannula_text
        OmnipodWizardStep.POD_ACTIVATED          -> CommonR.string.omnipod_common_pod_activation_wizard_pod_activated_text
        OmnipodWizardStep.START_POD_DEACTIVATION -> CommonR.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_text
        OmnipodWizardStep.DEACTIVATE_POD         -> CommonR.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
        OmnipodWizardStep.POD_DEACTIVATED        -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_text
        OmnipodWizardStep.POD_DISCARDED          -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_discarded_text
    }

    // endregion
}
