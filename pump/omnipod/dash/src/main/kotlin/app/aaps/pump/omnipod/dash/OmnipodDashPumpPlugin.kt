package app.aaps.pump.omnipod.dash

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.OmnipodDash
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.DateTimeUtil
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandDisableSuspendAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandUpdateAlertConfiguration
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepRepetitionType
import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepType
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants.Companion.POD_EXPIRATION_IMMINENT_ALERT_HOURS_REMAINING
import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType
import app.aaps.pump.omnipod.dash.driver.pod.state.CommandConfirmed
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.dash.history.data.BolusRecord
import app.aaps.pump.omnipod.dash.history.data.BolusType
import app.aaps.pump.omnipod.dash.history.data.TempBasalRecord
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import app.aaps.pump.omnipod.dash.keys.DashBooleanPreferenceKey
import app.aaps.pump.omnipod.dash.keys.DashStringNonPreferenceKey
import app.aaps.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import app.aaps.pump.omnipod.dash.util.Constants
import app.aaps.pump.omnipod.dash.util.mapProfileToBasalProgram
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.ceil

@Singleton
class OmnipodDashPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val omnipodManager: OmnipodDashManager,
    private val podStateManager: OmnipodDashPodStateManager,
    private val profileFunction: ProfileFunction,
    private val history: DashHistory,
    private val pumpSync: PumpSync,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val uiInteraction: UiInteraction,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val dashHistoryDatabase: DashHistoryDatabase
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(OmnipodDashOverviewFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_pod_128)
        .pluginName(R.string.omnipod_dash_name)
        .shortName(R.string.omnipod_dash_name_short)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.omnipod_dash_pump_description),
    ownPreferences = listOf(
        OmnipodBooleanPreferenceKey::class.java, OmnipodIntPreferenceKey::class.java,
        DashBooleanPreferenceKey::class.java, DashStringNonPreferenceKey::class.java
    ),
    aapsLogger, rh, preferences, commandQueue
),
    Pump, OmnipodDash, OwnDatabasePlugin {

    @Volatile var bolusCanceled = false
    @Volatile var bolusDeliveryInProgress = false

    private var statusChecker: Runnable
    private var nextPodWarningCheck: Long = 0
    @Volatile var stopConnecting: CountDownLatch? = null
    private var disposables: CompositeDisposable = CompositeDisposable()

    companion object {

        private const val BOLUS_RETRY_INTERVAL_MS = 2000.toLong()
        private const val BOLUS_RETRIES = 5 // number of retries for cancel/get bolus status
        private const val STATUS_CHECK_INTERVAL_MS = (60L * 1000)
        private const val RESERVOIR_OVER_50_UNITS_DEFAULT = 75.0

        private val pumpDescription = PumpDescription().fillFor(PumpType.OMNIPOD_DASH)
    }

    init {
        statusChecker = Runnable {
            refreshStatusOnUnacknowledgedCommands()
            updatePodWarnings()
            aapsLogger.info(LTag.PUMP, "statusChecker")

            try {
                createFakeTBRWhenNoActivePod()
                    .subscribeOn(aapsSchedulers.io)
                    .blockingAwait()
            } catch (e: Exception) {
                aapsLogger.warn(LTag.PUMP, "Error on createFakeTBRWhenNoActivePod=$e")
            }
            handler?.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MS)
        }
    }

    private fun createFakeTBRWhenNoActivePod(): Completable = Completable.defer {
        if (!podStateManager.isPodRunning) {
            val expectedState = pumpSync.expectedPumpState()
            val tbr = expectedState.temporaryBasal
            if (tbr == null || tbr.rate != 0.0) {
                aapsLogger.info(LTag.PUMP, "createFakeTBRWhenNoActivePod")
                pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = System.currentTimeMillis(),
                    rate = 0.0,
                    duration = T.mins(PodConstants.MAX_POD_LIFETIME.toMinutes()).msecs(),
                    isAbsolute = true,
                    type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                    pumpId = System.currentTimeMillis(), // we don't use this, just make sure it's unique
                    pumpType = PumpType.OMNIPOD_DASH,
                    pumpSerial = Constants.PUMP_SERIAL_FOR_FAKE_TBR // switching the serialNumber here would need a
                    // call to connectNewPump. If we do that, then we will have a TBR started by the "n/a" pump and
                    // cancelled by "4241". This did not work ok.
                )
            }
        }
        Completable.complete()
    }

    private fun updatePodWarnings() {
        if (System.currentTimeMillis() > nextPodWarningCheck) {
            if (!podStateManager.isPodRunning) {
                uiInteraction.addNotification(
                    Notification.OMNIPOD_POD_NOT_ATTACHED,
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_no_active_pod),
                    Notification.NORMAL
                )
            } else {
                rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED))
                if (podStateManager.isSuspended) {
                    showNotification(
                        Notification.OMNIPOD_POD_SUSPENDED,
                        rh.gs(R.string.insulin_delivery_suspended),
                        Notification.NORMAL,
                        app.aaps.core.ui.R.raw.boluserror
                    )
                } else {
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED))
                    if (!podStateManager.sameTimeZone) {
                        uiInteraction.addNotification(
                            Notification.OMNIPOD_TIME_OUT_OF_SYNC,
                            rh.gs(R.string.timezone_on_pod_is_different_from_the_timezone),
                            Notification.NORMAL
                        )
                    }
                }
            }
            nextPodWarningCheck = DateTimeUtil.getTimeInFutureFromMinutes(15)
        }
    }

    private fun refreshStatusOnUnacknowledgedCommands() {
        if (podStateManager.isPodRunning &&
            podStateManager.activeCommand != null &&
            commandQueue.size() == 0 &&
            commandQueue.performing() == null
        ) {
            commandQueue.readStatus(rh.gs(R.string.unconfirmed_command), null)
        }
    }

    override fun isInitialized(): Boolean {
        return podStateManager.isPodRunning
    }

    override fun isSuspended(): Boolean {
        return podStateManager.isSuspended
    }

    override fun isBusy(): Boolean {
        // prevents the queue from executing commands
        return podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)
    }

    override fun isConnected(): Boolean {

        return !podStateManager.isPodRunning ||
            podStateManager.bluetoothConnectionState == OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED
    }

    override fun isConnecting(): Boolean {
        return stopConnecting != null
    }

    override fun isHandshakeInProgress(): Boolean {
        return stopConnecting != null &&
            podStateManager.bluetoothConnectionState == OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED
    }

    override fun finishHandshaking() {
    }

    override fun connect(reason: String) {
        aapsLogger.info(LTag.PUMP, "connect reason=$reason")
        podStateManager.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
        synchronized(this) {
            stopConnecting?.let {
                aapsLogger.warn(LTag.PUMP, "Already connecting: $stopConnecting")
                return
            }
            val stop = CountDownLatch(1)
            stopConnecting = stop
        }
        thread(
            start = true,
            name = "ConnectionThread",
        ) {
            try {
                stopConnecting?.let {
                    omnipodManager.connect(it).ignoreElements()
                        .doOnComplete { podStateManager.incrementSuccessfulConnectionAttemptsAfterRetries() }
                        .blockingAwait()
                }
            } catch (e: Exception) {
                aapsLogger.info(LTag.PUMPCOMM, "connect error=$e")
            } finally {
                synchronized(this) {
                    stopConnecting = null
                }
            }
        }
    }

    override fun disconnect(reason: String) {
        aapsLogger.info(LTag.PUMP, "disconnect reason=$reason")
        stopConnecting?.countDown()
        omnipodManager.disconnect(false)
    }

    override fun stopConnecting() {
        aapsLogger.info(LTag.PUMP, "stopConnecting")
        podStateManager.incrementFailedConnectionsAfterRetries()
        stopConnecting?.countDown()
        omnipodManager.disconnect(true)
    }

    override fun getPumpStatus(reason: String) {
        aapsLogger.debug(LTag.PUMP, "getPumpStatus reason=$reason")
        if (reason != "REQUESTED BY USER" && !podStateManager.isActivationCompleted) {
            // prevent races on BLE when the pod is not activated
            return
        }

        try {
            getPodStatus()
                .doOnComplete {
                    aapsLogger.info(LTag.PUMP, "getPumpStatus executed with success")
                    if (!podStateManager.isActivationCompleted) {
                        val msg = podStateManager.recoverActivationFromPodStatus()
                        msg?.let {
                            // TODO: show dialog with "try again, the pod is busy now"
                            aapsLogger.info(LTag.PUMP, "recoverActivationFromPodStatus msg=$msg")
                        }
                    }
                }.blockingAwait()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error in getPumpStatus", e)
        }
    }

    private fun getPodStatus(): Completable = Completable.concat(
        listOf(
            omnipodManager
                .getStatus(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
                .ignoreElements(),
            history.updateFromState(podStateManager),
            podStateManager.updateActiveCommand()
                .map { handleCommandConfirmation(it) }
                .ignoreElement(),
            verifyPumpState(),
            checkPodKaput(),
        )
    )

    private fun checkPodKaput(): Completable = Completable.defer {
        if (podStateManager.isPodKaput) {
            val tbr = pumpSync.expectedPumpState().temporaryBasal
            if (tbr == null || tbr.rate != 0.0) {
                pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = System.currentTimeMillis(),
                    rate = 0.0,
                    duration = T.mins(PodConstants.MAX_POD_LIFETIME.toMinutes()).msecs(),
                    isAbsolute = true,
                    type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                    pumpId = System.currentTimeMillis(), // we don't use this, just make sure it's unique
                    pumpType = PumpType.OMNIPOD_DASH,
                    pumpSerial = serialNumber()
                )
            }
            podStateManager.lastBolus?.run {
                if (!deliveryComplete) {
                    val deliveredUnits = markComplete()
                    deliveryComplete = true
                    val bolusHistoryEntry = history.getById(historyId)
                    val sync = pumpSync.syncBolusWithPumpId(
                        timestamp = bolusHistoryEntry.createdAt,
                        amount = deliveredUnits,
                        pumpId = bolusHistoryEntry.pumpId(),
                        pumpType = PumpType.OMNIPOD_DASH,
                        pumpSerial = serialNumber(),
                        type = bolusType
                    )
                    aapsLogger.info(LTag.PUMP, "syncBolusWithPumpId on CANCEL_BOLUS returned: $sync")
                }
            }
            if (!podStateManager.alarmSynced) {
                podStateManager.alarmType?.let {
                    if (!commandQueue.isCustomCommandInQueue(CommandDeactivatePod::class.java)) {
                        showNotification(
                            Notification.OMNIPOD_POD_FAULT,
                            it.toString(),
                            Notification.URGENT,
                            app.aaps.core.ui.R.raw.boluserror
                        )
                    }
                    pumpSync.insertAnnouncement(
                        error = it.toString(),
                        pumpId = System.currentTimeMillis(),
                        pumpType = PumpType.OMNIPOD_DASH,
                        pumpSerial = serialNumber()
                    )
                    podStateManager.alarmSynced = true
                }
            }
        }
        Completable.complete()
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        if (!podStateManager.isActivationCompleted) {
            return pumpEnactResultProvider.get().success(true).enacted(true)
        }
        aapsLogger.debug(LTag.PUMP, "setNewBasalProfile profile=$profile")
        return setNewBasalProfile(profile, OmnipodCommandType.SET_BASAL_PROFILE)
    }

    private fun setNewBasalProfile(profile: Profile, historyType: OmnipodCommandType): PumpEnactResult {
        var deliverySuspended = false
        val basalProgram = mapProfileToBasalProgram(profile)
        return executeProgrammingCommand(
            pre = suspendDeliveryIfActive().doOnComplete {
                if (podStateManager.activeCommand == null) {
                    // suspend delivery is confirmed
                    deliverySuspended = true
                }
            },
            historyEntry = history.createRecord(
                commandType = historyType,
                basalProfileRecord = BasalValuesRecord(profile.getBasalValues().toList())
            ),
            activeCommandEntry = { historyId ->
                podStateManager.createActiveCommand(historyId, basalProgram = basalProgram)
            },
            command = omnipodManager.setBasalProgram(basalProgram, hasBasalBeepEnabled()).ignoreElements(),
            post = failWhenUnconfirmed(deliverySuspended),
            // mark as failed even if it worked OK and try again vs. mark ok and deny later
        ).toPumpEnactResultImpl()
    }

    private fun failWhenUnconfirmed(deliverySuspended: Boolean): Completable = Completable.defer {
        rxBus.send(EventTempBasalChange())
        if (podStateManager.activeCommand != null) {
            if (deliverySuspended) {
                showNotification(
                    Notification.FAILED_UPDATE_PROFILE,
                    rh.gs(R.string.failed_to_set_the_new_basal_profile),
                    Notification.URGENT,
                    app.aaps.core.ui.R.raw.boluserror
                )
            } else {
                showNotification(
                    Notification.FAILED_UPDATE_PROFILE,
                    rh.gs(R.string.setting_basal_profile_might_have_failed),
                    Notification.URGENT,
                    app.aaps.core.ui.R.raw.boluserror
                )
            }
            Completable.error(java.lang.IllegalStateException("Command not confirmed"))
        } else {
            showNotification(Notification.PROFILE_SET_OK, rh.gs(R.string.profile_set_ok), Notification.INFO, null)

            Completable.complete()
        }
    }

    private fun suspendDeliveryIfActive(): Completable = Completable.defer {
        if (podStateManager.deliveryStatus == DeliveryStatus.SUSPENDED)
            Completable.complete()
        else
            executeProgrammingCommand(
                historyEntry = history.createRecord(OmnipodCommandType.SUSPEND_DELIVERY),
                command = omnipodManager.suspendDelivery(hasBasalBeepEnabled())
                    .filter { podEvent -> podEvent.isCommandSent() }
                    .map {
                        pumpSyncTempBasal(
                            0.0,
                            PodConstants.MAX_POD_LIFETIME.toMinutes(),
                            PumpSync.TemporaryBasalType.PUMP_SUSPEND
                        )
                        rxBus.send(EventTempBasalChange())
                    }
                    .ignoreElements()
            ).doOnComplete {
                notifyOnUnconfirmed(
                    Notification.FAILED_UPDATE_PROFILE,
                    rh.gs(R.string.suspend_delivery_is_unconfirmed),
                    app.aaps.core.ui.R.raw.boluserror,
                )
            }
    }

    override fun onStart() {
        super.onStart()
        podStateManager.onStart()
        handler?.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MS)
        disposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    if (it.isChanged(OmnipodBooleanPreferenceKey.ExpirationReminder.key) ||
                        it.isChanged(OmnipodIntPreferenceKey.ExpirationReminderHours.key) ||
                        it.isChanged(OmnipodBooleanPreferenceKey.ExpirationAlarm.key) ||
                        it.isChanged(OmnipodIntPreferenceKey.ExpirationAlarmHours.key) ||
                        it.isChanged(OmnipodBooleanPreferenceKey.LowReservoirAlert.key) ||
                        it.isChanged(OmnipodIntPreferenceKey.LowReservoirAlertUnits.key)
                    ) {
                        commandQueue.customCommand(CommandUpdateAlertConfiguration(), null)
                    }
                },
                fabricPrivacy::logException
            )
    }

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }

    private fun observeDeliverySuspended(): Completable = Completable.defer {
        if (podStateManager.deliveryStatus == DeliveryStatus.SUSPENDED)
            Completable.complete()
        else {
            Completable.error(java.lang.IllegalStateException("Expected suspended delivery"))
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!podStateManager.isActivationCompleted) {
            // prevent setBasal requests
            return true
        }
        if (podStateManager.isSuspended) {
            // set new basal profile failed midway
            return false
        }
        val running = podStateManager.basalProgram
        val equal = (mapProfileToBasalProgram(profile) == running)
        aapsLogger.info(LTag.PUMP, "set: $equal. profile=$profile, running=$running")
        return equal
    }

    override val lastBolusTime: Long? get() = podStateManager.lastBolus?.startTime
    override val lastBolusAmount: Double? get() = podStateManager.lastBolus?.requestedUnits
    override val lastDataTime: Long get() = podStateManager.lastUpdatedSystem
    override val baseBasalRate: Double
        get() {
            val date = System.currentTimeMillis()
            val ret = podStateManager.basalProgram?.rateAt(date) ?: 0.0
            aapsLogger.info(LTag.PUMP, "baseBasalRate: $ret at $date}")
            return if (podStateManager.alarmType != null) {
                0.0
            } else
                ret
        }

    override val reservoirLevel: Double
        get() {
            if (podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)) {
                return 0.0
            }

            // Omnipod only reports reservoir level when there's < 1023 pulses left
            return podStateManager.pulsesRemaining?.let {
                it * PodConstants.POD_PULSE_BOLUS_UNITS
            } ?: RESERVOIR_OVER_50_UNITS_DEFAULT
        }

    // Omnipod Dash doesn't report it's battery level. We return 0 here and hide related fields in the UI
    override val batteryLevel: Int? = null

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        try {
            bolusDeliveryInProgress = true
            aapsLogger.info(LTag.PUMP, "Delivering treatment: $detailedBolusInfo $bolusCanceled")
            val requestedBolusAmount = detailedBolusInfo.insulin
            if (requestedBolusAmount > reservoirLevel) {
                return pumpEnactResultProvider.get()
                    .success(false)
                    .enacted(false)
                    .bolusDelivered(0.0)
                    .comment(rh.gs(R.string.omnipod_dash_not_enough_insulin))
            }
            if (podStateManager.deliveryStatus?.bolusDeliveringActive() == true) {
                return pumpEnactResultProvider.get()
                    .success(false)
                    .enacted(false)
                    .bolusDelivered(0.0)
                    .comment(rh.gs(R.string.omnipod_dash_bolus_already_in_progress))
            }

            var deliveredBolusAmount = 0.0

            val beepsConfigurationKey = if (detailedBolusInfo.bolusType == BS.Type.SMB)
                OmnipodBooleanPreferenceKey.SmbBeepsEnabled
            else
                OmnipodBooleanPreferenceKey.BolusBeepsEnabled
            val bolusBeeps = preferences.get(beepsConfigurationKey)
            aapsLogger.info(
                LTag.PUMP,
                "deliverTreatment: requestedBolusAmount=$requestedBolusAmount"
            )
            val ret = executeProgrammingCommand(
                pre = observeDeliveryNotCanceled(),
                historyEntry = history.createRecord(
                    commandType = OmnipodCommandType.SET_BOLUS,
                    bolusRecord = BolusRecord(
                        requestedBolusAmount,
                        BolusType.fromBolusInfoBolusType(detailedBolusInfo.bolusType)
                    )
                ),
                activeCommandEntry = { historyId ->
                    podStateManager.createActiveCommand(
                        historyId,
                        requestedBolus = requestedBolusAmount
                    )
                },
                command = omnipodManager.bolus(
                    detailedBolusInfo.insulin,
                    bolusBeeps,
                    bolusBeeps
                ).filter { podEvent -> podEvent.isCommandSent() }
                    .map { pumpSyncBolusStart(requestedBolusAmount, detailedBolusInfo.bolusType) }
                    .ignoreElements(),
                post = waitForBolusDeliveryToComplete(requestedBolusAmount, detailedBolusInfo.bolusType)
                    .map {
                        deliveredBolusAmount = it
                        aapsLogger.info(LTag.PUMP, "deliverTreatment: deliveredBolusAmount=$deliveredBolusAmount")
                    }
                    .ignoreElement()
            ).doFinally {
                if (detailedBolusInfo.bolusType == BS.Type.SMB) {
                    notifyOnUnconfirmed(
                        Notification.OMNIPOD_UNCERTAIN_SMB,
                        "Unable to verify whether SMB bolus ($requestedBolusAmount U) succeeded. " +
                            "<b>Refresh pod status to confirm or deny this command.",
                        app.aaps.core.ui.R.raw.boluserror
                    )
                } else {
                    if (podStateManager.activeCommand != null) {
                        val sound = if (hasBolusErrorBeepEnabled()) app.aaps.core.ui.R.raw.boluserror else 0
                        showErrorDialog(rh.gs(R.string.bolus_delivery_status_uncertain), sound)
                    }
                }
            }.toSingle {
                pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(deliveredBolusAmount)
            }.onErrorReturnItem(
                // success if canceled
                pumpEnactResultProvider.get().success(bolusCanceled).enacted(false)
            )
                .blockingGet()
            aapsLogger.info(
                LTag.PUMP,
                "deliverTreatment result: $ret. " +
                    "deliveredBolusAmount=$deliveredBolusAmount. " +
                    "ret bolus=${ret.bolusDelivered}" +
                    "bolusCanceled=$bolusCanceled"
            )
            return ret
        } finally {
            bolusCanceled = false
            bolusDeliveryInProgress = false
        }
    }

    private fun observeDeliveryNotCanceled(): Completable = Completable.defer {
        if (bolusCanceled) {
            Completable.error(java.lang.IllegalStateException("Bolus canceled"))
        } else {
            Completable.complete()
        }
    }

    private fun updateBolusProgressDialog(msg: String, percent: Int) {
        rxBus.send(EventOverviewBolusProgress(status = msg, percent = percent))
    }

    private fun waitForBolusDeliveryToComplete(
        requestedBolusAmount: Double,
        bolusType: BS.Type
    ): Single<Double> = Single.defer {

        if (bolusCanceled && podStateManager.activeCommand != null) {
            var errorGettingStatus: Throwable? = null
            for (tries in 1..BOLUS_RETRIES) {
                try {
                    getPodStatus().blockingAwait()
                    break
                } catch (err: Throwable) {
                    errorGettingStatus = err
                    aapsLogger.debug(LTag.PUMP, "waitForBolusDeliveryToComplete errorGettingStatus=$errorGettingStatus")
                    Thread.sleep(BOLUS_RETRY_INTERVAL_MS) // retry every 2 sec
                }
            }
            if (errorGettingStatus != null) {
                // requestedBolusAmount will be updated later, via pumpSync
                // This state is: cancellation requested and getPodStatus failed 5 times.
                return@defer Single.just(requestedBolusAmount)
            }
        }

        val estimatedDeliveryTimeSeconds = estimateBolusDeliverySeconds(requestedBolusAmount)
        aapsLogger.info(LTag.PUMP, "estimatedDeliveryTimeSeconds: $estimatedDeliveryTimeSeconds")
        var waited = 0
        while (waited < estimatedDeliveryTimeSeconds && !bolusCanceled) {
            waited += 1
            Thread.sleep(1000)
            if (bolusType == BS.Type.SMB) {
                continue
            }
            val percent = (waited.toFloat() / estimatedDeliveryTimeSeconds) * 100
            updateBolusProgressDialog(
                rh.gs(app.aaps.core.interfaces.R.string.bolus_delivering, Round.roundTo(percent * requestedBolusAmount / 100, PodConstants.POD_PULSE_BOLUS_UNITS)),
                percent.toInt()
            )
        }

        (1..BOLUS_RETRIES).forEach { tryNumber ->
            updateBolusProgressDialog(rh.gs(R.string.checking_delivery_status), 100)

            val cmd = if (bolusCanceled)
                cancelBolus()
            else
                getPodStatus()

            var errorGettingStatus: Throwable? = null
            try {
                cmd.blockingAwait()
            } catch (e: Exception) {
                errorGettingStatus = e
                aapsLogger.debug(LTag.PUMP, "waitForBolusDeliveryToComplete errorGettingStatus=$errorGettingStatus")
                Thread.sleep(BOLUS_RETRY_INTERVAL_MS) // retry every 3 sec
            }
            if (errorGettingStatus != null) {
                return@forEach
            }
            val bolusDeliveringActive = podStateManager.deliveryStatus?.bolusDeliveringActive() == true

            if (bolusDeliveringActive) {
                // delivery not complete yet
                val remainingUnits = podStateManager.lastBolus!!.bolusUnitsRemaining
                val percent = ((requestedBolusAmount - remainingUnits) / requestedBolusAmount) * 100
                updateBolusProgressDialog(
                    rh.gs(app.aaps.core.interfaces.R.string.bolus_delivering, Round.roundTo(requestedBolusAmount - remainingUnits, PodConstants.POD_PULSE_BOLUS_UNITS)),
                    percent.toInt()
                )

                val sleepSeconds = if (bolusCanceled)
                    BOLUS_RETRY_INTERVAL_MS
                else
                    estimateBolusDeliverySeconds(remainingUnits)
                Thread.sleep(sleepSeconds * 1000.toLong())
            } else {
                // delivery is complete. If pod is Kaput, we are handling this in getPodStatus
                return@defer Single.just(podStateManager.lastBolus!!.deliveredUnits()!!)
            }
        }
        Single.just(requestedBolusAmount) // will be updated later!
    }

    private fun cancelBolus(): Completable {
        val bolusBeeps = preferences.get(OmnipodBooleanPreferenceKey.BolusBeepsEnabled)
        return executeProgrammingCommand(
            historyEntry = history.createRecord(commandType = OmnipodCommandType.CANCEL_BOLUS),
            command = omnipodManager.stopBolus(bolusBeeps).ignoreElements(),
            checkNoActiveCommand = false,
        )
    }

    private fun estimateBolusDeliverySeconds(requestedBolusAmount: Double): Long {
        return ceil(requestedBolusAmount / PodConstants.POD_PULSE_BOLUS_UNITS).toLong() * 2 + 3
    }

    private fun pumpSyncBolusStart(
        requestedBolusAmount: Double,
        bolusType: BS.Type
    ): Boolean {
        require(requestedBolusAmount > 0) { "requestedBolusAmount has to be positive" }

        val activeCommand = podStateManager.activeCommand
        if (activeCommand == null) {
            throw IllegalArgumentException(
                "No active command or illegal podEvent: " +
                    "activeCommand=$activeCommand"
            )
        }
        val historyEntry = history.getById(activeCommand.historyId)
        val ret = pumpSync.syncBolusWithPumpId(
            timestamp = historyEntry.createdAt,
            amount = requestedBolusAmount,
            type = bolusType,
            pumpId = historyEntry.pumpId(),
            pumpType = PumpType.OMNIPOD_DASH,
            pumpSerial = serialNumber()
        )
        aapsLogger.debug(LTag.PUMP, "pumpSyncBolusStart: $ret")
        return ret
    }

    override fun stopBolusDelivering() {
        aapsLogger.info(LTag.PUMP, "stopBolusDelivering called")
        if (bolusDeliveryInProgress) {
            bolusCanceled = true
        }
    }

    override fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        val tempBasalBeeps = hasTempBasalBeepEnabled()
        aapsLogger.info(
            LTag.PUMP,
            "setTempBasalAbsolute: " +
                "duration=$durationInMinutes min :: " +
                "rate=$absoluteRate U/h :: " +
                "enforce=$enforceNew ::" +
                "tbrType=$tbrType"
        )

        val ret = executeProgrammingCommand(
            pre = observeNoActiveTempBasal(),
            historyEntry = history.createRecord(
                commandType = OmnipodCommandType.SET_TEMPORARY_BASAL,
                tempBasalRecord = TempBasalRecord(duration = durationInMinutes, rate = absoluteRate)
            ),
            activeCommandEntry = { historyId ->
                podStateManager.createActiveCommand(
                    historyId,
                    tempBasal = OmnipodDashPodStateManager.TempBasal(
                        startTime = System.currentTimeMillis(),
                        rate = absoluteRate,
                        durationInMinutes = durationInMinutes.toShort(),
                    )
                )
            },
            command = omnipodManager.setTempBasal(
                absoluteRate,
                durationInMinutes.toShort(),
                tempBasalBeeps
            )
                .filter { podEvent -> podEvent.isCommandSent() }
                .map { pumpSyncTempBasal(absoluteRate, durationInMinutes.toLong(), tbrType) }
                .ignoreElements(),
        ).doOnComplete {
            notifyOnUnconfirmed(
                Notification.OMNIPOD_TBR_ALERTS,
                rh.gs(R.string.setting_temp_basal_might_have_basal_failed),
                app.aaps.core.ui.R.raw.boluserror,
            )
        }.toPumpEnactResultImpl()

        if (ret.success && ret.enacted) {
            ret.isPercent(false).absolute(absoluteRate).duration(durationInMinutes)
        }
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: result=$ret")
        return ret
    }

    private fun pumpSyncTempBasal(
        absoluteRate: Double,
        durationInMinutes: Long,
        tbrType: PumpSync.TemporaryBasalType
    ): Boolean {
        val activeCommand = podStateManager.activeCommand
        if (activeCommand == null) {
            throw IllegalArgumentException(
                "No active command: " +
                    "activeCommand=$activeCommand"
            )
        }
        val historyEntry = history.getById(activeCommand.historyId)
        aapsLogger.debug(
            LTag.PUMP,
            "pumpSyncTempBasal: absoluteRate=$absoluteRate, durationInMinutes=$durationInMinutes pumpId=${historyEntry.pumpId()}"
        )
        val ret = pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = historyEntry.createdAt,
            rate = absoluteRate,
            duration = T.mins(durationInMinutes).msecs(),
            isAbsolute = true,
            type = tbrType,
            pumpId = historyEntry.pumpId(),
            pumpType = PumpType.OMNIPOD_DASH,
            pumpSerial = serialNumber()
        )
        aapsLogger.debug(LTag.PUMP, "pumpSyncTempBasal: $ret")
        return ret
    }

    private fun observeNoActiveTempBasal(): Completable {
        return Completable.defer {
            if (podStateManager.deliveryStatus?.tempBasalActive() == false) {
                // TODO: what happens if we try to cancel nonexistent temp basal?
                aapsLogger.info(LTag.PUMP, "No temporary basal to cancel")
                Completable.complete()
            } else {
                // enforceNew == true
                aapsLogger.info(LTag.PUMP, "Canceling existing temp basal")
                executeProgrammingCommand(
                    historyEntry = history.createRecord(OmnipodCommandType.CANCEL_TEMPORARY_BASAL),
                    command = omnipodManager.stopTempBasal(hasTempBasalBeepEnabled()).ignoreElements()
                ).doOnComplete {
                    notifyOnUnconfirmed(
                        Notification.OMNIPOD_TBR_ALERTS,
                        rh.gs(R.string.cancelling_temp_basal_might_have_failed),
                        app.aaps.core.ui.R.raw.boluserror,
                    )
                }
            }
        }
    }

    override fun setTempBasalPercent(
        percent: Int,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        // TODO i18n
        return pumpEnactResultProvider.get().success(false).enacted(false)
            .comment("Omnipod Dash driver does not support percentage temp basals")
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        // TODO i18n
        return pumpEnactResultProvider.get().success(false).enacted(false)
            .comment("Omnipod Dash driver does not support extended boluses")
    }

    private fun hasTempBasalBeepEnabled(): Boolean =
        preferences.get(OmnipodBooleanPreferenceKey.TbrBeepsEnabled)

    private fun hasBasalBeepEnabled(): Boolean =
        preferences.get(OmnipodBooleanPreferenceKey.BasalBeepsEnabled)

    private fun hasBolusErrorBeepEnabled(): Boolean =
        preferences.get(OmnipodBooleanPreferenceKey.SoundUncertainBolusNotification)

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (!podStateManager.tempBasalActive &&
            pumpSync.expectedPumpState().temporaryBasal == null
        ) {
            // nothing to cancel
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }

        return executeProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.CANCEL_TEMPORARY_BASAL),
            command = omnipodManager.stopTempBasal(hasTempBasalBeepEnabled()).ignoreElements(),
        ).doOnComplete {
            notifyOnUnconfirmed(
                Notification.OMNIPOD_TBR_ALERTS,
                rh.gs(R.string.cancel_temp_basal_result_is_uncertain),
                app.aaps.core.ui.R.raw.boluserror, // TODO: add setting for this
            )
        }.toPumpEnactResultImpl()
    }

    private fun notifyOnUnconfirmed(notificationId: Int, msg: String, sound: Int?) {
        if (podStateManager.activeCommand != null) {
            aapsLogger.debug(LTag.PUMP, "Notification for active command: ${podStateManager.activeCommand}")
            showNotification(notificationId, msg, Notification.URGENT, sound)
        }
    }

    private fun Completable.toPumpEnactResultImpl(): PumpEnactResult {
        return this.toSingleDefault(pumpEnactResultProvider.get().success(true).enacted(true))
            .doOnError { throwable ->
                aapsLogger.error(LTag.PUMP, "toPumpEnactResult, error executing command: $throwable")
            }
            .onErrorReturnItem(pumpEnactResultProvider.get().success(false).enacted(false))
            .blockingGet()
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        // TODO i18n
        return pumpEnactResultProvider.get().success(false).enacted(false)
            .comment("Omnipod Dash driver does not support extended boluses")
    }

    override val pumpDescription: PumpDescription = Companion.pumpDescription
    override fun manufacturer(): ManufacturerType = ManufacturerType.Insulet
    override fun model(): PumpType = pumpDescription.pumpType
    override fun serialNumber(): String = podStateManager.uniqueId?.toString() ?: Constants.PUMP_SERIAL_FOR_FAKE_TBR
    override val isFakingTempsByExtendedBoluses: Boolean = false

    override fun loadTDDs(): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false)
            .comment("Omnipod Dash driver does not support TDD")

    override fun canHandleDST(): Boolean = false
    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult {
        return when (customCommand) {
            is CommandSilenceAlerts            ->
                silenceAlerts()

            is CommandResumeDelivery           ->
                resumeDelivery()

            is CommandDeactivatePod            ->
                deactivatePod()

            is CommandHandleTimeChange         ->
                handleTimeChange()

            is CommandUpdateAlertConfiguration ->
                updateAlertConfiguration()

            is CommandPlayTestBeep             ->
                playTestBeep()

            is CommandDisableSuspendAlerts     ->
                disableSuspendAlerts()

            else                               -> {
                aapsLogger.warn(LTag.PUMP, "Unsupported custom command: " + customCommand.javaClass.name)
                pumpEnactResultProvider.get().success(false).enacted(false).comment(
                    rh.gs(
                        app.aaps.pump.omnipod.common.R.string.omnipod_common_error_unsupported_custom_command,
                        customCommand.javaClass.name
                    )
                )
            }
        }
    }

    private fun silenceAlerts(): PumpEnactResult {
        // TODO filter alert types
        return podStateManager.activeAlerts?.let {
            executeProgrammingCommand(
                historyEntry = history.createRecord(commandType = OmnipodCommandType.ACKNOWLEDGE_ALERTS),
                command = omnipodManager.silenceAlerts(it).ignoreElements(),
            ).toPumpEnactResultImpl()
        } ?: pumpEnactResultProvider.get().success(false).enacted(false).comment("No active alerts") // TODO i18n
    }

    private fun disableSuspendAlerts(): PumpEnactResult {
        val alerts = listOf(
            AlertConfiguration(
                AlertType.SUSPEND_ENDED,
                enabled = false,
                durationInMinutes = 0,
                autoOff = false,
                AlertTrigger.TimerTrigger(
                    0
                ),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
            ),
        )
        val ret = executeProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.CONFIGURE_ALERTS),
            command = omnipodManager.programAlerts(alerts).ignoreElements(),
        ).toPumpEnactResultImpl()
        if (ret.success && ret.enacted) {
            podStateManager.suspendAlertsEnabled = false
        }
        return ret
    }

    private fun resumeDelivery(): PumpEnactResult {
        return profileFunction.getProfile()?.let {
            executeProgrammingCommand(
                pre = observeDeliverySuspended(),
                historyEntry = history.createRecord(OmnipodCommandType.RESUME_DELIVERY, basalProfileRecord = BasalValuesRecord(it.getBasalValues().toList())),
                command = omnipodManager.setBasalProgram(mapProfileToBasalProgram(it), hasBasalBeepEnabled())
                    .ignoreElements()
            ).doFinally {
                notifyOnUnconfirmed(
                    Notification.FAILED_UPDATE_PROFILE,
                    rh.gs(R.string.unconfirmed_resumedelivery_command_please_refresh_pod_status),
                    app.aaps.core.ui.R.raw.boluserror
                )
            }.toPumpEnactResultImpl()
        } ?: pumpEnactResultProvider.get().success(false).enacted(false).comment("No profile active") // TODO i18n
    }

    private fun deactivatePod(): PumpEnactResult {
        var success = true
        val ret = executeProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.DEACTIVATE_POD),
            command = omnipodManager.deactivatePod().ignoreElements(),
            checkNoActiveCommand = false
        ).doOnComplete {
            if (podStateManager.activeCommand != null) {
                success = false
            } else {
                podStateManager.reset()
                rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_FAULT))
            }
        }.toPumpEnactResultImpl()
        if (!success) {
            ret.success(false)
        }
        return ret
    }

    private fun handleTimeChange(): PumpEnactResult {
        return profileFunction.getProfile()?.let {
            setNewBasalProfile(it, OmnipodCommandType.SET_TIME)
        } ?: pumpEnactResultProvider.get().success(false).enacted(false).comment("No profile active")
    }

    private fun updateAlertConfiguration(): PumpEnactResult {

        val expirationReminderEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder)
        val expirationReminderHours = preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours)
        val expirationAlarmEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationAlarm)
        val expirationAlarmHours = preferences.get(OmnipodIntPreferenceKey.ExpirationAlarmHours)
        val lowReservoirAlertEnabled = preferences.get(OmnipodBooleanPreferenceKey.LowReservoirAlert)
        val lowReservoirAlertUnits = preferences.get(OmnipodIntPreferenceKey.LowReservoirAlertUnits)

        when {
            podStateManager.sameAlertSettings(
                expirationReminderEnabled,
                expirationReminderHours,
                expirationAlarmEnabled,
                expirationAlarmHours,
                lowReservoirAlertEnabled,
                lowReservoirAlertUnits
            )                             -> {
                aapsLogger.debug(LTag.PUMP, "Ignoring updateAlertConfiguration because the settings did not change")
                return pumpEnactResultProvider.get().success(true).enacted(false)
            }

            !podStateManager.isPodRunning -> {
                aapsLogger.debug(LTag.PUMP, "Ignoring updateAlertConfiguration because there is no active pod")
                return pumpEnactResultProvider.get().success(true).enacted(false)
            }
        }

        val podLifeLeft = Duration.between(ZonedDateTime.now(), podStateManager.expiry)
        val expiryReminderDelay = podLifeLeft.minus(Duration.ofHours(expirationReminderHours.toLong()))
        if (expiryReminderDelay.isNegative) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "updateAlertConfiguration negative expiryAlertDuration=$expiryReminderDelay"
            )
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
        // expiry Alarm Delay, add 8 hours (grace period)
        val expiryAlarmDelay = podLifeLeft.minus(Duration.ofHours(expirationAlarmHours.toLong())).plus(Duration.ofHours(8))
        if (expiryAlarmDelay.isNegative) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "updateAlertConfiguration negative expiryAlarmDuration=$expiryAlarmDelay"
            )
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
        val expiryImminentDelay = podLifeLeft.minus(Duration.ofHours(POD_EXPIRATION_IMMINENT_ALERT_HOURS_REMAINING)).plus(Duration.ofHours(8))
        if (expiryImminentDelay.isNegative) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "updateAlertConfiguration negative expiryImminentDuration=$expiryImminentDelay"
            )
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
        val alerts = listOf(
            AlertConfiguration(
                AlertType.LOW_RESERVOIR,
                enabled = lowReservoirAlertEnabled,
                durationInMinutes = 0,
                autoOff = false,
                AlertTrigger.ReservoirVolumeTrigger((lowReservoirAlertUnits * 10).toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX
            ),
            AlertConfiguration(
                AlertType.USER_SET_EXPIRATION,
                enabled = expirationReminderEnabled,
                durationInMinutes = 0,
                autoOff = false,
                AlertTrigger.TimerTrigger(
                    expiryReminderDelay.toMinutes().toShort()
                ),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
            ),
            AlertConfiguration(
                AlertType.EXPIRATION,
                enabled = expirationAlarmEnabled,
                durationInMinutes = TimeUnit.HOURS.toMinutes((expirationAlarmHours - 1).toLong()).toShort(),
                autoOff = false,
                AlertTrigger.TimerTrigger(
                    expiryAlarmDelay.toMinutes().toShort()
                ),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX3
            ),
            AlertConfiguration(
                AlertType.EXPIRATION_IMMINENT,
                enabled = expirationAlarmEnabled,
                durationInMinutes = 0,
                autoOff = false,
                AlertTrigger.TimerTrigger(
                    expiryImminentDelay.toMinutes().toShort()
                ),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX3
            )
        )

        return executeProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.CONFIGURE_ALERTS),
            command = omnipodManager.programAlerts(alerts).ignoreElements(),
            post = podStateManager.updateExpirationAlertSettings(
                expirationReminderEnabled,
                expirationReminderHours,
                expirationAlarmEnabled,
                expirationAlarmHours
            ).andThen(
                podStateManager.updateLowReservoirAlertSettings(
                    lowReservoirAlertEnabled,
                    lowReservoirAlertUnits
                )
            )
        ).toPumpEnactResultImpl()
    }

    private fun playTestBeep(): PumpEnactResult {
        return executeProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.PLAY_TEST_BEEP),
            command = omnipodManager.playBeep(BeepType.LONG_SINGLE_BEEP).ignoreElements()
        ).toPumpEnactResultImpl()
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.info(LTag.PUMP, "Ignoring time change because automatic time handling is not implemented. timeChangeType=${timeChangeType.name}")
    }

    private fun executeProgrammingCommand(
        pre: Completable = Completable.complete(),
        historyEntry: Single<Long>,
        activeCommandEntry: (historyId: Long) -> Single<OmnipodDashPodStateManager.ActiveCommand> =
            { historyId -> podStateManager.createActiveCommand(historyId) },
        command: Completable,
        post: Completable = Completable.complete(),
        checkNoActiveCommand: Boolean = true
    ): Completable {
        return Completable.concat(
            listOf(
                pre,
                if (checkNoActiveCommand)
                    podStateManager.observeNoActiveCommand()
                else
                    Completable.complete(),
                historyEntry
                    .flatMap { activeCommandEntry(it) }
                    .ignoreElement(),
                command.doOnError {
                    podStateManager.activeCommand?.sendError = it
                    aapsLogger.error(LTag.PUMP, "Error executing command", it)
                }.onErrorComplete(),
                history.updateFromState(podStateManager),
                podStateManager.updateActiveCommand()
                    .map { handleCommandConfirmation(it) }
                    .ignoreElement(),
                verifyPumpState(),
                checkPodKaput(),
                refreshOverview(),
                post,
            )
        )
    }

    private fun refreshOverview(): Completable = Completable.defer {
        rxBus.send(EventRefreshOverview("Dash command", false))
        Completable.complete()
    }

    private fun handleCommandConfirmation(confirmation: CommandConfirmed) {
        val command = confirmation.command
        val historyEntry = history.getById(command.historyId)
        aapsLogger.debug(LTag.PUMPCOMM, "handling command confirmation: $confirmation ${historyEntry.commandType}")
        when (historyEntry.commandType) {
            OmnipodCommandType.CANCEL_TEMPORARY_BASAL -> {
                if (confirmation.success) {
                    val ret = pumpSync.syncStopTemporaryBasalWithPumpId(
                        historyEntry.createdAt,
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                    aapsLogger.info(LTag.PUMP, "syncStopTemporaryBasalWithPumpId ret=$ret pumpId=${historyEntry.pumpId()}")
                    podStateManager.tempBasal = null
                }
                rxBus.send(EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS))
            }

            OmnipodCommandType.RESUME_DELIVERY        -> {
                // We can't invalidate this command,
                // and this is why it is pumpSync-ed at this point
                if (confirmation.success) {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        historyEntry.createdAt,
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                    podStateManager.tempBasal = null
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED))
                    rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS))
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC))
                    commandQueue.customCommand(CommandDisableSuspendAlerts(rh), null)
                }
            }

            OmnipodCommandType.SET_BASAL_PROFILE      -> {
                if (confirmation.success) {
                    podStateManager.basalProgram = command.basalProgram
                    if (podStateManager.basalProgram == null) {
                        aapsLogger.warn(LTag.PUMP, "Saving null basal profile")
                    }
                    if (!commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                        // we are late-confirming this command. before that, we answered with success:false
                        rxBus.send(EventProfileSwitchChanged())
                    }
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        historyEntry.createdAt,
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED))
                    rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS))
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC))
                    commandQueue.customCommand(CommandDisableSuspendAlerts(rh), null)
                }
            }

            OmnipodCommandType.SET_TEMPORARY_BASAL    -> {
                // This treatment was synced before sending the command
                if (!confirmation.success) {
                    aapsLogger.info(LTag.PUMPCOMM, "temporary basal denied. PumpId: ${historyEntry.pumpId()}")
                    pumpSync.invalidateTemporaryBasalWithPumpId(
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                } else {
                    podStateManager.tempBasal = command.tempBasal
                }
                rxBus.send(EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS))
            }

            OmnipodCommandType.SUSPEND_DELIVERY       -> {
                if (!confirmation.success) {
                    pumpSync.invalidateTemporaryBasalWithPumpId(
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                } else {
                    podStateManager.tempBasal = null
                }
            }

            OmnipodCommandType.SET_BOLUS              -> {
                if (confirmation.success) {
                    if (command.requestedBolus == null) {
                        aapsLogger.error(LTag.PUMP, "Requested bolus not found: $command")
                    }
                    val record = historyEntry.record
                    if (record !is BolusRecord) {
                        aapsLogger.error(
                            LTag.PUMP,
                            "Expected SET_BOLUS history record to be a BolusRecord, found " +
                                "$record"
                        )
                    }
                    record as BolusRecord

                    podStateManager.createLastBolus(
                        record.amout,
                        command.historyId,
                        record.bolusType.toBolusInfoBolusType()
                    )
                } else {
                    pumpSync.syncBolusWithPumpId(
                        timestamp = historyEntry.createdAt,
                        amount = 0.0,
                        pumpId = historyEntry.pumpId(),
                        pumpType = PumpType.OMNIPOD_DASH,
                        pumpSerial = serialNumber(),
                        type = null
                    )
                }
                rxBus.send(EventDismissNotification(Notification.OMNIPOD_UNCERTAIN_SMB))
            }

            OmnipodCommandType.CANCEL_BOLUS           -> {
                if (confirmation.success) {
                    podStateManager.lastBolus?.run {
                        val deliveredUnits = markComplete()
                        if (deliveredUnits < 0) {
                            aapsLogger.error(LTag.PUMP, "Negative delivered units!!! $deliveredUnits")
                            return
                        }
                        val bolusHistoryEntry = history.getById(historyId)
                        val sync = pumpSync.syncBolusWithPumpId(
                            timestamp = bolusHistoryEntry.createdAt,
                            amount = deliveredUnits,
                            pumpId = bolusHistoryEntry.pumpId(),
                            pumpType = PumpType.OMNIPOD_DASH,
                            pumpSerial = serialNumber(),
                            type = bolusType
                        )
                        aapsLogger.info(LTag.PUMP, "syncBolusWithPumpId on CANCEL_BOLUS returned: $sync")
                    } ?: aapsLogger.error(LTag.PUMP, "Cancelled bolus that does not exist")
                }
            }

            else                                      ->
                aapsLogger.warn(
                    LTag.PUMP,
                    "Will not sync confirmed command of type: $historyEntry and " +
                        "success: ${confirmation.success}"
                )
        }
    }

    private fun verifyPumpState(): Completable = Completable.defer {
        aapsLogger.debug(LTag.PUMP, "verifyPumpState, AAPS: ${pumpSync.expectedPumpState().temporaryBasal} Pump: ${podStateManager.deliveryStatus}")
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        if (tbr != null && podStateManager.deliveryStatus?.basalActive() == true) {
            aapsLogger.error(LTag.PUMP, "AAPS expected a TBR running but pump has no TBR running! AAPS: ${pumpSync.expectedPumpState().temporaryBasal} Pump: ${podStateManager.deliveryStatus}")
            // Alert user
            val sound = if (hasBolusErrorBeepEnabled()) app.aaps.core.ui.R.raw.boluserror else 0
            showErrorDialog(rh.gs(R.string.temp_basal_out_of_sync), sound)
            // Sync stopped basal with AAPS
            val ret = pumpSync.syncStopTemporaryBasalWithPumpId(
                System.currentTimeMillis(), // Note: It would be nice if TBR end could be estimated, but this will add a lot of complexity
                tbr.id,
                PumpType.OMNIPOD_DASH,
                serialNumber()
            )
            aapsLogger.info(LTag.PUMP, "syncStopTemporaryBasalWithPumpId ret=$ret pumpId=${tbr.id}")
            podStateManager.tempBasal = null
        } else if (tbr == null && podStateManager.deliveryStatus?.tempBasalActive() == true) {
            aapsLogger.error(LTag.PUMP, "AAPS expected no TBR running but pump has a TBR running! AAPS: ${pumpSync.expectedPumpState().temporaryBasal} Pump: ${podStateManager.deliveryStatus}")
            // Alert user
            val sound = if (hasBolusErrorBeepEnabled()) app.aaps.core.ui.R.raw.boluserror else 0
            showErrorDialog(rh.gs(R.string.temp_basal_out_of_sync), sound)
            // If this is reached is reached there is probably a something wrong with the time (maybe it has changed?).
            // No way to calculate the TBR end time and update pumpSync properly.
            // Cancel TBR running on Pump
            return@defer observeNoActiveTempBasal()
                .concatWith(
                    podStateManager.updateActiveCommand()
                        .map { handleCommandConfirmation(it) }
                        .ignoreElement())
        }

        return@defer Completable.complete()
    }

    private fun showErrorDialog(message: String, sound: Int) {
        uiInteraction.runAlarm(message, rh.gs(app.aaps.core.ui.R.string.error), sound)
    }

    private fun showNotification(id: Int, message: String, urgency: Int, sound: Int?) {
        uiInteraction.addNotificationWithSound(
            id,
            message,
            urgency,
            if (sound != null && soundEnabledForNotificationType(id)) sound else null
        )
    }

    private fun soundEnabledForNotificationType(notificationType: Int): Boolean =
        when (notificationType) {
            Notification.OMNIPOD_TBR_ALERTS    -> preferences.get(OmnipodBooleanPreferenceKey.SoundUncertainTbrNotification)
            Notification.OMNIPOD_UNCERTAIN_SMB -> preferences.get(OmnipodBooleanPreferenceKey.SoundUncertainSmbNotification)
            Notification.OMNIPOD_POD_SUSPENDED -> preferences.get(DashBooleanPreferenceKey.SoundDeliverySuspendedNotification)
            else                               -> true
        }

    override fun clearAllTables() = dashHistoryDatabase.clearAllTables()

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val beepCategory = PreferenceCategory(context)
        parent.addPreference(beepCategory)
        beepCategory.apply {
            key = "omnipod_dash_beeps"
            title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_category_confirmation_beeps)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.BolusBeepsEnabled,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_bolus_beeps_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.BasalBeepsEnabled,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_basal_beeps_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.SmbBeepsEnabled,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_smb_beeps_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.TbrBeepsEnabled,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_tbr_beeps_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = DashBooleanPreferenceKey.UseBonding,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_dash_use_bonding
                )
            )
        }

        val alertsCategory = PreferenceCategory(context)
        parent.addPreference(alertsCategory)
        alertsCategory.apply {
            key = "omnipod_dash_alerts"
            title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_category_alerts)
            initialExpandedChildrenCount = 0

            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.ExpirationReminder,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_expiration_reminder_enabled,
                    summary = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_expiration_reminder_enabled_summary
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = OmnipodIntPreferenceKey.ExpirationReminderHours,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_expiration_reminder_hours_before_expiry
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.ExpirationAlarm,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_expiration_alarm_enabled,
                    summary = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_expiration_alarm_enabled_summary
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = OmnipodIntPreferenceKey.ExpirationAlarmHours,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_expiration_alarm_hours_before_shutdown
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.LowReservoirAlert,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_low_reservoir_alert_enabled
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = OmnipodIntPreferenceKey.LowReservoirAlertUnits,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_low_reservoir_alert_units
                )
            )

        }
        val notificationsCategory = PreferenceCategory(context)
        parent.addPreference(notificationsCategory)
        notificationsCategory.apply {
            key = "omnipod_dash_notifications"
            title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_category_notifications)
            initialExpandedChildrenCount = 0

            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.SoundUncertainTbrNotification,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_notification_uncertain_tbr_sound_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.SoundUncertainSmbNotification,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_notification_uncertain_smb_sound_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.SoundUncertainBolusNotification,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_notification_uncertain_bolus_sound_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = DashBooleanPreferenceKey.SoundDeliverySuspendedNotification,
                    title = R.string.omnipod_common_preferences_notification_delivery_suspended_sound_enabled
                )
            )
        }
    }
}
