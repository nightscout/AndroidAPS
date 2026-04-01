package app.aaps.pump.omnipod.eros.ui.wizard.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import app.aaps.pump.omnipod.common.R as CommonR

@Stable
@HiltViewModel
class ErosOmnipodWizardViewModel @Inject constructor(
    private val aapsOmnipodManager: AapsOmnipodErosManager,
    private val podStateManager: AapsErosPodStateManager,
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val insulinManager: InsulinManager,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : OmnipodWizardViewModel(logger, aapsSchedulers, pumpEnactResultProvider) {

    init {
        viewModelScope.launch {
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            loadInsulins(insulins, activeLabel)
            loadSiteRotationEntriesInternal()
            _ready.value = true
        }
    }

    override val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    override val showSiteLocationStep: Boolean
        get() = preferences.get(BooleanKey.SiteRotationManagePump)

    private var siteRotationEntriesCache: List<TE> = emptyList()

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))

    override fun siteRotationEntries(): List<TE> = siteRotationEntriesCache

    private suspend fun loadSiteRotationEntriesInternal() {
        siteRotationEntriesCache = persistenceLayer.getTherapyEventDataFromTime(
            System.currentTimeMillis() - T.days(45).msecs(), false
        ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
    }

    override fun executeInsulinProfileSwitch() {
        val selected = selectedInsulin.value ?: return
        val activeLabel = activeInsulinLabel.value
        if (selected.insulinLabel == activeLabel) return
        viewModelScope.launch {
            profileFunction.createProfileSwitchWithNewInsulin(selected, Sources.OmnipodEros)
        }
    }

    override fun saveSiteLocation() {
        val location = getSelectedSiteLocation().takeIf { it != TE.Location.NONE } ?: return
        val arrow = getSelectedSiteArrow().takeIf { it != TE.Arrow.NONE }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val entries = persistenceLayer.getTherapyEventDataFromToTime(now - 60_000, now)
                    .filter { it.type == TE.Type.CANNULA_CHANGE }
                entries.firstOrNull()?.let { te ->
                    persistenceLayer.insertOrUpdateTherapyEvent(te.copy(location = location, arrow = arrow))
                }
            } catch (_: Exception) {
            }
        }
    }

    // region Action implementations — code copied verbatim from existing VMs

    override fun doInitializePod(): Single<PumpEnactResult> =
        Single.fromCallable { aapsOmnipodManager.initializePod() }

    override fun doInsertCannula(): Single<PumpEnactResult> =
        Single.fromCallable { aapsOmnipodManager.insertCannula(runBlocking { pumpSync.expectedPumpState() }.profile) }

    override fun doDeactivatePod(): Single<PumpEnactResult> =
        Single.create { source ->
            commandQueue.customCommand(CommandDeactivatePod(), object : Callback() {
                override fun run() {
                    source.onSuccess(result)
                }
            })
        }

    // endregion

    // region Pod state queries

    override fun discardPod() {
        aapsOmnipodManager.discardPodState()
    }

    override fun isPodInAlarm(): Boolean = podStateManager.isPodFaulted

    override fun isPodActivationTimeExceeded(): Boolean = podStateManager.isPodActivationTimeExceeded

    override fun isPodDeactivatable(): Boolean =
        podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

    // endregion

    // region String resources per step

    @StringRes
    override fun getTitleForStep(step: OmnipodWizardStep): Int = when (step) {
        OmnipodWizardStep.START_POD_ACTIVATION   -> CommonR.string.omnipod_common_pod_activation_wizard_start_pod_activation_title
        OmnipodWizardStep.SELECT_INSULIN         -> app.aaps.core.ui.R.string.select_insulin
        OmnipodWizardStep.INITIALIZE_POD         -> CommonR.string.omnipod_common_pod_activation_wizard_initialize_pod_title
        OmnipodWizardStep.ATTACH_POD             -> CommonR.string.omnipod_common_pod_activation_wizard_attach_pod_title
        OmnipodWizardStep.SITE_LOCATION          -> app.aaps.core.ui.R.string.site_location
        OmnipodWizardStep.INSERT_CANNULA         -> CommonR.string.omnipod_common_pod_activation_wizard_insert_cannula_title
        OmnipodWizardStep.POD_ACTIVATED          -> CommonR.string.omnipod_common_pod_activation_wizard_pod_activated_title
        OmnipodWizardStep.START_POD_DEACTIVATION -> CommonR.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_title
        OmnipodWizardStep.DEACTIVATE_POD         -> CommonR.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_title
        OmnipodWizardStep.POD_DEACTIVATED        -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_title
        OmnipodWizardStep.POD_DISCARDED          -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_discarded_title
    }

    @StringRes
    override fun getTextForStep(step: OmnipodWizardStep): Int = when (step) {
        OmnipodWizardStep.START_POD_ACTIVATION   -> R.string.omnipod_eros_pod_activation_wizard_start_pod_activation_text
        OmnipodWizardStep.SELECT_INSULIN         -> app.aaps.core.ui.R.string.select_insulin_description
        OmnipodWizardStep.INITIALIZE_POD         -> R.string.omnipod_eros_pod_activation_wizard_initialize_pod_text
        OmnipodWizardStep.ATTACH_POD             -> CommonR.string.omnipod_common_pod_activation_wizard_attach_pod_text
        OmnipodWizardStep.SITE_LOCATION          -> app.aaps.core.ui.R.string.select_site_location
        OmnipodWizardStep.INSERT_CANNULA         -> CommonR.string.omnipod_common_pod_activation_wizard_insert_cannula_text
        OmnipodWizardStep.POD_ACTIVATED          -> CommonR.string.omnipod_common_pod_activation_wizard_pod_activated_text
        OmnipodWizardStep.START_POD_DEACTIVATION -> CommonR.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_text
        OmnipodWizardStep.DEACTIVATE_POD         -> CommonR.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
        OmnipodWizardStep.POD_DEACTIVATED        -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_text
        OmnipodWizardStep.POD_DISCARDED          -> CommonR.string.omnipod_common_pod_deactivation_wizard_pod_discarded_text
    }

    // endregion
}
