package app.aaps.pump.omnipod.dash.ui.wizard.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import app.aaps.pump.omnipod.dash.R
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import app.aaps.pump.omnipod.dash.util.Constants
import app.aaps.pump.omnipod.dash.util.I8n
import app.aaps.pump.omnipod.dash.util.mapProfileToBasalProgram
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import app.aaps.pump.omnipod.common.R as CommonR

@Stable
@HiltViewModel
class DashOmnipodWizardViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    private val podStateManager: OmnipodDashPodStateManager,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val history: DashHistory,
    private val commandQueue: CommandQueue,
    private val notificationManager: NotificationManager,
    private val pumpSync: PumpSync,
    private val fabricPrivacy: FabricPrivacy,
    private val insulinManager: InsulinManager,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
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
            profileFunction.createProfileSwitchWithNewInsulin(selected, Sources.OmnipodDash)
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
        Single.create { source ->
            val lowReservoirAlertEnabled = preferences.get(OmnipodBooleanPreferenceKey.LowReservoirAlert)
            val lowReservoirAlertUnits = preferences.get(OmnipodIntPreferenceKey.LowReservoirAlertUnits)
            val lowReservoirAlertTrigger = if (lowReservoirAlertEnabled) {
                AlertTrigger.ReservoirVolumeTrigger((lowReservoirAlertUnits * 10).toShort())
            } else
                null

            disposable += omnipodManager.activatePodPart1(lowReservoirAlertTrigger)
                .ignoreElements()
                .andThen(podStateManager.updateLowReservoirAlertSettings(lowReservoirAlertEnabled, lowReservoirAlertUnits))
                .andThen(
                    history.createRecord(
                        OmnipodCommandType.INITIALIZE_POD,
                        initialResult = InitialResult.SENT,
                        resolveResult = ResolvedResult.SUCCESS,
                        resolvedAt = System.currentTimeMillis(),
                    ).ignoreElement()
                )
                .subscribeBy(
                    onError = { throwable ->
                        logger.error(LTag.PUMP, "Error in Pod activation part 1", throwable)
                        source.onSuccess(
                            pumpEnactResultProvider.get()
                                .success(false)
                                .comment(I8n.textFromException(throwable, rh))
                        )
                    },
                    onComplete = {
                        logger.debug("Pod activation part 1 completed")
                        source.onSuccess(pumpEnactResultProvider.get().success(true))
                    }
                )
        }

    override fun doInsertCannula(): Single<PumpEnactResult> = Single.create { source ->
        val profile = runBlocking { pumpSync.expectedPumpState() }.profile
        if (profile == null) {
            source.onError(IllegalStateException("No profile set"))
        } else {
            val basalProgram = mapProfileToBasalProgram(profile)
            logger.debug(
                LTag.PUMPCOMM,
                "Mapped profile to basal program. profile={}, basalProgram={}",
                profile,
                basalProgram
            )
            val expirationReminderEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder)
            val expirationReminderHours = preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours)

            val expirationReminderHoursBeforeShutdown = if (expirationReminderEnabled)
                expirationReminderHours.toLong()
            else
                null

            val expirationAlarmEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationAlarm)
            val expirationAlarmHours = preferences.get(OmnipodIntPreferenceKey.ExpirationAlarmHours)

            val expirationAlarmHoursBeforeShutdown = if (expirationAlarmEnabled)
                expirationAlarmHours.toLong()
            else
                null

            disposable += omnipodManager.activatePodPart2(basalProgram, expirationReminderHoursBeforeShutdown, expirationAlarmHoursBeforeShutdown)
                .ignoreElements()
                .andThen(podStateManager.updateExpirationAlertSettings(expirationReminderEnabled, expirationReminderHours, expirationAlarmEnabled, expirationAlarmHours))
                .andThen(
                    history.createRecord(
                        OmnipodCommandType.INSERT_CANNULA,
                        basalProfileRecord = BasalValuesRecord(profile.getBasalValues().toList()),
                        initialResult = InitialResult.SENT,
                        resolveResult = ResolvedResult.SUCCESS,
                        resolvedAt = System.currentTimeMillis(),
                    ).ignoreElement()
                )
                .subscribeBy(
                    onError = { throwable ->
                        logger.error(LTag.PUMP, "Error in Pod activation part 2", throwable)
                        source.onSuccess(pumpEnactResultProvider.get().success(false).comment(I8n.textFromException(throwable, rh)))
                    },
                    onComplete = {
                        logger.debug("Pod activation part 2 completed")
                        podStateManager.basalProgram = basalProgram

                        runBlocking {
                            pumpSync.syncStopTemporaryBasalWithPumpId(
                                timestamp = System.currentTimeMillis(),
                                endPumpId = System.currentTimeMillis(),
                                pumpType = PumpType.OMNIPOD_DASH,
                                pumpSerial = Constants.PUMP_SERIAL_FOR_FAKE_TBR
                            )
                        }

                        pumpSync.connectNewPump()

                        runBlocking {
                            pumpSync.insertTherapyEventIfNewWithTimestamp(
                                timestamp = System.currentTimeMillis(),
                                type = TE.Type.CANNULA_CHANGE,
                                pumpType = PumpType.OMNIPOD_DASH,
                                pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                            )
                            pumpSync.insertTherapyEventIfNewWithTimestamp(
                                timestamp = System.currentTimeMillis(),
                                type = TE.Type.INSULIN_CHANGE,
                                pumpType = PumpType.OMNIPOD_DASH,
                                pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                            )
                        }
                        notificationManager.dismiss(NotificationId.OMNIPOD_POD_NOT_ATTACHED)
                        fabricPrivacy.logCustom("OmnipodDashPodActivated")
                        source.onSuccess(pumpEnactResultProvider.get().success(true))
                    }
                )
        }
    }

    override fun doDeactivatePod(): Single<PumpEnactResult> = Single.create { source ->
        commandQueue.customCommand(
            CommandDeactivatePod(),
            object : Callback() {
                override fun run() {
                    source.onSuccess(result)
                }
            }
        )
    }

    // endregion

    // region Pod state queries

    override fun discardPod() {
        podStateManager.reset()
        notificationManager.dismiss(NotificationId.OMNIPOD_POD_FAULT)
    }

    override fun isPodInAlarm(): Boolean = false // TODO: not yet implemented in Dash

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO: not yet implemented in Dash

    override fun isPodDeactivatable(): Boolean = true // TODO: not yet implemented in Dash

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
        OmnipodWizardStep.START_POD_ACTIVATION   -> R.string.omnipod_dash_pod_activation_wizard_start_pod_activation_text
        OmnipodWizardStep.SELECT_INSULIN         -> app.aaps.core.ui.R.string.select_insulin_description
        OmnipodWizardStep.INITIALIZE_POD         -> R.string.omnipod_dash_pod_activation_wizard_initialize_pod_text
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
