package app.aaps.pump.omnipod.eros

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.text.TextUtils
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.T.Companion.msecs
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.OmnipodEros
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.PumpState
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round.isSame
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.DateTimeUtil.getTimeInFutureFromMinutes
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.dialog.RileyLinkBLEConfigActivity
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpInfo
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkIntentPreferenceKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkLongKey
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandSuspendDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandUpdateAlertConfiguration
import app.aaps.pump.omnipod.eros.data.RLHistoryItemOmnipod
import app.aaps.pump.omnipod.eros.driver.communication.action.service.ExpirationReminderBuilder
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoRecentPulseLog
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.driver.definition.BeepConfigType
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.driver.util.TimeUtil
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosActiveAlertsChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosFaultEventChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosTbrChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosUncertainTbrRecovered
import app.aaps.pump.omnipod.eros.extensions.fromJsonString
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.keys.ErosBooleanPreferenceKey
import app.aaps.pump.omnipod.eros.keys.ErosLongNonPreferenceKey
import app.aaps.pump.omnipod.eros.keys.ErosStringNonPreferenceKey
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.queue.command.CommandGetPodStatus
import app.aaps.pump.omnipod.eros.queue.command.CommandReadPulseLog
import app.aaps.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import app.aaps.pump.omnipod.eros.ui.OmnipodErosOverviewFragment
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Instant
import java.util.Optional
import java.util.function.Supplier
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
class OmnipodErosPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val podStateManager: ErosPodStateManager,
    private val aapsOmnipodErosManager: AapsOmnipodErosManager,
    private val fabricPrivacy: FabricPrivacy,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val dateUtil: DateUtil,
    private val aapsOmnipodUtil: AapsOmnipodUtil,
    private val rileyLinkUtil: RileyLinkUtil,
    private val omnipodAlertUtil: OmnipodAlertUtil,
    private val profileFunction: ProfileFunction,
    private val pumpSync: PumpSync,
    private val uiInteraction: UiInteraction,
    private val erosHistoryDatabase: ErosHistoryDatabase,
    private val decimalFormatter: DecimalFormatter,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(OmnipodErosOverviewFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_pod_128)
        .pluginName(R.string.omnipod_eros_name)
        .shortName(R.string.omnipod_eros_name_short)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.omnipod_eros_pump_description),
    ownPreferences = listOf(
        ErosBooleanPreferenceKey::class.java, ErosLongNonPreferenceKey::class.java, ErosStringNonPreferenceKey::class.java
    ),
    aapsLogger, rh, preferences, commandQueue
), Pump, RileyLinkPumpDevice, OmnipodEros, OwnDatabasePlugin {

    private val disposable = CompositeDisposable()
    private val displayConnectionMessages = false
    private val statusChecker: Runnable

    // variables for handling statuses and history
    private var firstRun = true
    private var hasTimeDateOrTimeZoneChanged = false
    private var lastTimeDateOrTimeZoneUpdate: Instant = Instant.ofEpochSecond(0L)
    private var rileyLinkOmnipodService: RileyLinkOmnipodService? = null
    private var busy = false
    private var timeChangeRetries = 0
    private var nextPodWarningCheck: Long = 0

    // Required by RileyLinkPumpDevice interface.
    // Kind of redundant because we also store last successful and last failed communication in PodStateManager
    /**
     * Get the last communication time with the Pod. In the current implementation, this
     * doesn't have to mean that a command was successfully executed as the Pod could also return an ErrorResponse or PodFaultEvent
     * For getting the last time a command was successfully executed, use PodStateManager.getLastSuccessfulCommunication
     */

    override var lastConnectionTimeMillis: Long = 0
    private val loopHandler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var serviceConnection: ServiceConnection? = null

    private val pumpType = PumpType.OMNIPOD_EROS
    override val pumpDescription = PumpDescription().fillFor(pumpType)

    init {

        this.serviceConnection
        statusChecker = object : Runnable {
            override fun run() {
                if (commandQueue.size() == 0) {
                    if (podStateManager.isPodRunning && !podStateManager.isSuspended) aapsOmnipodErosManager.cancelSuspendedFakeTbrIfExists()
                    else aapsOmnipodErosManager.createSuspendedFakeTbrIfNotExists()

                    if (this@OmnipodErosPumpPlugin.hasTimeDateOrTimeZoneChanged) commandQueue.customCommand(CommandHandleTimeChange(false), null)
                    if (!this@OmnipodErosPumpPlugin.verifyPodAlertConfiguration()) commandQueue.customCommand(CommandUpdateAlertConfiguration(), null)
                    if (aapsOmnipodErosManager.isAutomaticallyAcknowledgeAlertsEnabled && podStateManager.isPodActivationCompleted &&
                        !podStateManager.isPodDead && podStateManager.activeAlerts.size() > 0 && !commandQueue.isCustomCommandInQueue(CommandSilenceAlerts::class.java)
                    ) queueAcknowledgeAlertsCommand()
                } else
                    aapsLogger.debug(LTag.PUMP, "Skipping Pod status check because command queue is not empty")
                updatePodWarningNotifications()
                loopHandler.postDelayed(this, STATUS_CHECK_INTERVAL_MILLIS)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is connected")
                val mLocalBinder = service as RileyLinkOmnipodService.LocalBinder
                rileyLinkOmnipodService = mLocalBinder.serviceInstance
                rileyLinkOmnipodService?.let { rileyLinkOmnipodService ->
                    rileyLinkOmnipodService.verifyConfiguration()
                    Thread {
                        for (@Suppress("unused") i in 0..19) {
                            SystemClock.sleep(5000)

                            aapsLogger.debug(LTag.PUMP, "Starting Omnipod-RileyLink service")
                            if (rileyLinkOmnipodService.setNotInPreInit()) break
                        }
                    }.start()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is disconnected")
                rileyLinkOmnipodService = null
            }
        }

        loopHandler.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MILLIS)

        // We can't do this in PodStateManager itself, because JodaTimeAndroid.init() hasn't been called yet
        // When PodStateManager is created, which causes an IllegalArgumentException for DateTimeZones not being recognized
        podStateManager.loadPodState()

        lastConnectionTimeMillis = preferences.get(RileyLinkLongKey.LastGoodDeviceCommunicationTime)

        val intent = Intent(context, RileyLinkOmnipodService::class.java)
        serviceConnection?.let { context.bindService(intent, it, Context.BIND_AUTO_CREATE) }

        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ serviceConnection?.let { context.unbindService(it) } }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOmnipodErosTbrChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ handleCancelledTbr() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOmnipodErosUncertainTbrRecovered::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ handleUncertainTbrRecovery() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOmnipodErosActiveAlertsChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ handleActivePodAlerts() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOmnipodErosFaultEventChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ handlePodFaultEvent() }, fabricPrivacy::logException)
        // Pass only to setup wizard
        disposable += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> rxBus.send(EventSWRLStatus(event.getStatus(context))) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(OmnipodBooleanPreferenceKey.BasalBeepsEnabled.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.BolusBeepsEnabled.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.TbrBeepsEnabled.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.SmbBeepsEnabled.key) ||
                               event.isChanged(ErosBooleanPreferenceKey.ShowSuspendDeliveryButton.key) ||
                               event.isChanged(ErosBooleanPreferenceKey.ShowPulseLogButton.key) ||
                               event.isChanged(ErosBooleanPreferenceKey.ShowRileyLinkStatsButton.key) ||
                               event.isChanged(RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel.key) ||
                               event.isChanged(ErosBooleanPreferenceKey.BatteryChangeLogging.key) ||
                               event.isChanged(ErosBooleanPreferenceKey.TimeChangeEnabled.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.SoundUncertainBolusNotification.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.SoundUncertainSmbNotification.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.SoundUncertainTbrNotification.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.AutomaticallyAcknowledgeAlerts.key)
                           ) {
                               aapsOmnipodErosManager.reloadSettings()
                           } else if (event.isChanged(OmnipodBooleanPreferenceKey.ExpirationReminder.key) ||
                               event.isChanged(OmnipodIntPreferenceKey.ExpirationAlarmHours.key) ||
                               event.isChanged(OmnipodBooleanPreferenceKey.LowReservoirAlert.key) ||
                               event.isChanged(OmnipodIntPreferenceKey.LowReservoirAlertUnits.key)
                           ) {
                               if (!verifyPodAlertConfiguration()) {
                                   commandQueue.customCommand(CommandUpdateAlertConfiguration(), null)
                               }
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           // See if a bolus was active before the app previously exited
                           // If so, add it to history
                           // Needs to be done after EventAppInitialized because otherwise, TreatmentsPlugin.onStart() hasn't been called yet
                           // so it didn't initialize a TreatmentService yet, resulting in a NullPointerException
                           if (preferences.getIfExists(ErosStringNonPreferenceKey.ActiveBolus) != null) {
                               val activeBolusString = preferences.get(ErosStringNonPreferenceKey.ActiveBolus)
                               aapsLogger.warn(LTag.PUMP, "Found active bolus in preferences: {}. Adding Treatment.", activeBolusString)
                               try {
                                   aapsOmnipodErosManager.addBolusToHistory(DetailedBolusInfo().fromJsonString(activeBolusString))
                               } catch (ex: Exception) {
                                   aapsLogger.error(LTag.PUMP, "Failed to add active bolus to history", ex)
                               }
                               preferences.remove(ErosStringNonPreferenceKey.ActiveBolus)
                           }
                       }, fabricPrivacy::logException)
    }

    override fun isRileyLinkReady(): Boolean = rileyLinkServiceData.rileyLinkServiceState.isReady()

    private fun handleCancelledTbr() {
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        if (!podStateManager.isTempBasalRunning && tbr != null && !aapsOmnipodErosManager.hasSuspendedFakeTbr()) {
            aapsOmnipodErosManager.reportCancelledTbr()
        }
    }

    private fun handleUncertainTbrRecovery() {
        val tempBasal = pumpSync.expectedPumpState().temporaryBasal

        if (podStateManager.isTempBasalRunning && tempBasal == null) {
            if (podStateManager.hasTempBasal()) {
                aapsLogger.warn(LTag.PUMP, "Registering TBR that AAPS was unaware of")
                val pumpId = aapsOmnipodErosManager.addTbrSuccessToHistory(
                    podStateManager.tempBasalStartTime.millis,
                    TempBasalPair(podStateManager.tempBasalAmount, false, podStateManager.tempBasalDuration.standardMinutes.toInt())
                )

                pumpSync.syncTemporaryBasalWithPumpId(
                    podStateManager.tempBasalStartTime.millis,
                    podStateManager.tempBasalAmount,
                    podStateManager.tempBasalDuration.millis,
                    true,
                    TemporaryBasalType.NORMAL,
                    pumpId,
                    PumpType.OMNIPOD_EROS,
                    serialNumber()
                )
            } else {
                // Not sure what's going on. Notify the user
                aapsLogger.error(LTag.PUMP, "Unknown TBR in both Pod state and AAPS")
                uiInteraction.addNotificationWithSound(Notification.OMNIPOD_UNKNOWN_TBR, rh.gs(R.string.omnipod_eros_error_tbr_running_but_aaps_not_aware), Notification.NORMAL, app.aaps.core.ui.R.raw.boluserror)
            }
        } else if (!podStateManager.isTempBasalRunning && tempBasal != null) {
            aapsLogger.warn(LTag.PUMP, "Removing AAPS TBR that actually hadn't succeeded")
            pumpSync.invalidateTemporaryBasal(tempBasal.id, Sources.OmnipodEros, tempBasal.timestamp)
        }

        rxBus.send(EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS))
    }

    private fun handleActivePodAlerts() {
        if (podStateManager.isPodActivationCompleted && !podStateManager.isPodDead) {
            val activeAlerts = podStateManager.activeAlerts
            if (activeAlerts.size() > 0) {
                val alerts = TextUtils.join(", ", aapsOmnipodUtil.getTranslatedActiveAlerts(podStateManager))
                val notificationText = rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_pod_alerts, activeAlerts.size(), alerts)
                uiInteraction.addNotification(Notification.OMNIPOD_POD_ALERTS, notificationText, Notification.URGENT)
                pumpSync.insertAnnouncement(notificationText, null, PumpType.OMNIPOD_EROS, serialNumber())

                if (aapsOmnipodErosManager.isAutomaticallyAcknowledgeAlertsEnabled && !commandQueue.isCustomCommandInQueue(CommandSilenceAlerts::class.java)) {
                    queueAcknowledgeAlertsCommand()
                }
            }
        }
    }

    private fun handlePodFaultEvent() {
        if (podStateManager.isPodFaulted) {
            val notificationText = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_pod_fault_description, podStateManager.faultEventCode.value, podStateManager.faultEventCode.name)
            pumpSync.insertAnnouncement(notificationText, null, PumpType.OMNIPOD_EROS, serialNumber())
        }
    }

    override fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMP, "OmnipodPumpPlugin.onStop()")
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
        serviceConnection?.let { context.unbindService(it) }
        serviceConnection = null
        disposable.clear()
    }

    private fun queueAcknowledgeAlertsCommand() {
        commandQueue.customCommand(CommandSilenceAlerts(), object : Callback() {
            override fun run() {
                aapsLogger.debug(LTag.PUMP, "Acknowledge alerts result: {} ({})", result.success, result.comment)
            }
        })
    }

    private fun updatePodWarningNotifications() {
        if (System.currentTimeMillis() > this.nextPodWarningCheck) {
            if (!podStateManager.isPodRunning) {
                uiInteraction.addNotification(Notification.OMNIPOD_POD_NOT_ATTACHED, rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_pod_not_attached), Notification.NORMAL)
            } else {
                rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED))

                if (podStateManager.isSuspended) {
                    uiInteraction.addNotification(Notification.OMNIPOD_POD_SUSPENDED, rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_pod_suspended), Notification.NORMAL)
                } else {
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED))

                    if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) {
                        uiInteraction.addNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC, rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_time_out_of_sync), Notification.NORMAL)
                    } else {
                        rxBus.send(EventDismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC))
                    }
                }
            }

            this.nextPodWarningCheck = getTimeInFutureFromMinutes(15)
        }
    }

    override fun isInitialized(): Boolean = isConnected() && podStateManager.isPodActivationCompleted
    override fun isConnected(): Boolean = rileyLinkOmnipodService?.isInitialized == true
    override fun isConnecting(): Boolean = rileyLinkOmnipodService?.isInitialized != true

    override fun isHandshakeInProgress(): Boolean {
        if (displayConnectionMessages) {
            aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress [OmnipodPumpPlugin] - default (empty) implementation.")
        }
        return false
    }

    // TODO is this correct?
    override fun isBusy(): Boolean = busy || rileyLinkOmnipodService == null || !podStateManager.isPodRunning

    override fun setBusy(busy: Boolean) {
        this.busy = busy
    }

    override fun isSuspended(): Boolean = !podStateManager.isPodRunning || podStateManager.isSuspended

    override fun triggerPumpConfigurationChangedEvent() {
        rxBus.send(EventRileyLinkDeviceStatusChange())
    }

    override val rileyLinkService: RileyLinkOmnipodService? get() = rileyLinkOmnipodService

    override val pumpInfo: RileyLinkPumpInfo
        get() =
            RileyLinkPumpInfo(rh.gs(R.string.omnipod_eros_frequency), "Eros", if (podStateManager.isPodInitialized) podStateManager.address.toString() else "-")

    // Required by RileyLinkPumpDevice interface.
    // Kind of redundant because we also store last successful and last failed communication in PodStateManager
    /**
     * Set the last communication time with the Pod to now. In the current implementation, this
     * doesn't have to mean that a command was successfully executed as the Pod could also return an ErrorResponse or PodFaultEvent
     * For setting the last time a command was successfully executed, use PodStateManager.setLastSuccessfulCommunication
     */
    override fun setLastCommunicationToNow() {
        lastConnectionTimeMillis = System.currentTimeMillis()
    }

    /**
     * We don't do periodical status requests because that could drain the Pod's battery
     * The only actual status requests we send to the Pod here are on startup (in [initializeAfterRileyLinkConnection()][.initializeAfterRileyLinkConnection]),
     * When explicitly requested through SMS commands
     * And when the basal and/or temp basal status is uncertain
     * When the user explicitly requested it by clicking the Refresh button on the Omnipod tab (which is executed through [.executeCustomCommand])
     */
    override fun getPumpStatus(reason: String) {
        if (firstRun) {
            initializeAfterRileyLinkConnection()
            firstRun = false
        } else {
            if ("SMS" == reason) {
                aapsLogger.info(LTag.PUMP, "Acknowledged AAPS getPumpStatus request it was requested through an SMS")
                getPodStatus()
            } else if (podStateManager.isPodRunning && (!podStateManager.isBasalCertain() || !podStateManager.isTempBasalCertain())) {
                aapsLogger.info(LTag.PUMP, "Acknowledged AAPS getPumpStatus request because basal and/or temp basal is uncertain")
                getPodStatus()
            }
        }
    }

    private fun getPodStatus(): PumpEnactResult {
        return executeCommand(OmnipodCommandType.GET_POD_STATUS) { aapsOmnipodErosManager.getPodStatus() }!!
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        if (!podStateManager.hasPodState()) return pumpEnactResultProvider.get().enacted(false).success(false).comment("Null pod state")
        val result: PumpEnactResult = executeCommand(OmnipodCommandType.SET_BASAL_PROFILE) { aapsOmnipodErosManager.setBasalProfile(profile, true) }!!

        aapsLogger.info(LTag.PUMP, "Basal Profile was set: " + result.success)

        return result
    }

    override fun isThisProfileSet(profile: Profile): Boolean =
        if (!podStateManager.isPodActivationCompleted) {
            // When no Pod is active, return true here in order to prevent AAPS from setting a profile
            // When we activate a new Pod, we just use ProfileFunction to set the currently active profile
            true
        } else podStateManager.basalSchedule == AapsOmnipodErosManager.mapProfileToBasalSchedule(profile)

    override val lastBolusTime: Long? get() = null
    override val lastBolusAmount: Double? get() = null
    override val lastDataTime: Long get() = if (podStateManager.isPodInitialized) podStateManager.lastSuccessfulCommunication.millis else 0

    override val baseBasalRate: Double
        get() =
            if (!podStateManager.isPodRunning) 0.0
            else podStateManager.basalSchedule?.rateAt(TimeUtil.toDuration(DateTime.now())) ?: 0.0

    override val reservoirLevel: Double
        get() =
            if (!podStateManager.isPodRunning) 0.0
            // Omnipod only reports reservoir level when it's 50 units or less.
            // When it's over 50 units, we don't know, so return some default over 50 units
            else podStateManager.reservoirLevel ?: RESERVOIR_OVER_50_UNITS_DEFAULT

    override val batteryLevel: Int?
        get() = if (aapsOmnipodErosManager.isShowRileyLinkBatteryLevel) rileyLinkServiceData.batteryLevel else null

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin == 0.0 || detailedBolusInfo.carbs > 0) {
            throw IllegalArgumentException(detailedBolusInfo.toString(), Exception())
        }
        return deliverBolus(detailedBolusInfo)
    }

    override fun stopBolusDelivering() {
        executeCommand<PumpEnactResult?>(OmnipodCommandType.CANCEL_BOLUS) { aapsOmnipodErosManager.cancelBolus() }
    }

    // if enforceNew is true, current temp basal is cancelled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes)

        if (durationInMinutes <= 0 || durationInMinutes % OmnipodConstants.BASAL_STEP_DURATION.standardMinutes != 0L) {
            return pumpEnactResultProvider.get().success(false).comment(rh.gs(R.string.omnipod_eros_error_set_temp_basal_failed_validation, OmnipodConstants.BASAL_STEP_DURATION.standardMinutes))
        }

        // read current TBR
        val tbrCurrent = readTBR()

        if (tbrCurrent != null) {
            aapsLogger.info(
                LTag.PUMP, "setTempBasalAbsolute: Current Basal: duration: {} min, rate={}",
                msecs(tbrCurrent.duration).mins(), tbrCurrent.rate
            )
        }

        if (tbrCurrent != null && !enforceNew) {
            if (isSame(tbrCurrent.rate, absoluteRate)) {
                aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - No enforceNew and same rate. Exiting.")
                return pumpEnactResultProvider.get().success(true).enacted(false)
            }
        }

        val result: PumpEnactResult =
            executeCommand(OmnipodCommandType.SET_TEMPORARY_BASAL) { aapsOmnipodErosManager.setTemporaryBasal(TempBasalPair(absoluteRate, false, durationInMinutes)) }!!

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - setTBR. Response: " + result.success)

        if (result.success) {
            preferences.inc(ErosLongNonPreferenceKey.TbrsSet)
        }

        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val tbrCurrent = readTBR()

        if (tbrCurrent == null) {
            aapsLogger.info(LTag.PUMP, "cancelTempBasal - TBR already cancelled.")
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }

        return executeCommand<PumpEnactResult?>(OmnipodCommandType.CANCEL_TEMPORARY_BASAL) { aapsOmnipodErosManager.cancelTemporaryBasal() }!!
    }

    override fun manufacturer(): ManufacturerType = pumpType.manufacturer()
    override fun model(): PumpType = pumpType
    override fun serialNumber(): String = if (podStateManager.isPodInitialized) podStateManager.address.toString() else "-"

    override fun executeCustomAction(customActionType: CustomActionType) {
        aapsLogger.warn(LTag.PUMP, "Unknown custom action: $customActionType")
    }

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        if (!podStateManager.hasPodState()) return pumpEnactResultProvider.get().enacted(false).success(false).comment("Null pod state")
        if (customCommand is CommandSilenceAlerts) {
            return executeCommand<PumpEnactResult?>(OmnipodCommandType.ACKNOWLEDGE_ALERTS) { aapsOmnipodErosManager.acknowledgeAlerts() }
        }
        if (customCommand is CommandGetPodStatus) {
            return getPodStatus()
        }
        if (customCommand is CommandReadPulseLog) {
            return retrievePulseLog()
        }
        if (customCommand is CommandSuspendDelivery) {
            return executeCommand<PumpEnactResult?>(OmnipodCommandType.SUSPEND_DELIVERY) { aapsOmnipodErosManager.suspendDelivery() }
        }
        if (customCommand is CommandResumeDelivery) {
            return executeCommand<PumpEnactResult?>(OmnipodCommandType.RESUME_DELIVERY) { aapsOmnipodErosManager.setBasalProfile(profileFunction.getProfile(), false) }
        }
        if (customCommand is CommandDeactivatePod) {
            return executeCommand<PumpEnactResult?>(OmnipodCommandType.DEACTIVATE_POD) { aapsOmnipodErosManager.deactivatePod() }
        }
        if (customCommand is CommandHandleTimeChange) {
            return handleTimeChange(customCommand.requestedByUser)
        }
        if (customCommand is CommandUpdateAlertConfiguration) {
            return updateAlertConfiguration()
        }
        if (customCommand is CommandPlayTestBeep) {
            return executeCommand<PumpEnactResult?>(OmnipodCommandType.PLAY_TEST_BEEP) { aapsOmnipodErosManager.playTestBeep(BeepConfigType.BEEEP) }
        }

        aapsLogger.warn(LTag.PUMP, "Unsupported custom command: " + customCommand.javaClass.getName())
        return pumpEnactResultProvider.get().success(false).enacted(false).comment(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_unsupported_custom_command, customCommand.javaClass.getName()))
    }

    private fun retrievePulseLog(): PumpEnactResult {
        var result: PodInfoRecentPulseLog
        try {
            result = executeCommand(OmnipodCommandType.READ_POD_PULSE_LOG) { aapsOmnipodErosManager.readPulseLog() }!!
        } catch (ex: Exception) {
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(aapsOmnipodErosManager.translateException(ex))
        }

        uiInteraction.runAlarm(rh.gs(R.string.omnipod_eros_pod_management_pulse_log_value) + ":\n" + result.toString(), rh.gs(R.string.omnipod_eros_pod_management_pulse_log), 0)
        return pumpEnactResultProvider.get().success(true).enacted(false)
    }

    private fun updateAlertConfiguration(): PumpEnactResult {
        val expirationReminderTimeBeforeShutdown = omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown()
        val lowReservoirAlertUnits = omnipodAlertUtil.getLowReservoirAlertUnits()

        val alertConfigurations = ExpirationReminderBuilder(podStateManager) //
            .expirationAdvisory(
                expirationReminderTimeBeforeShutdown != null,
                Optional.ofNullable<Duration?>(expirationReminderTimeBeforeShutdown).orElse(Duration.ZERO)
            )
            .lowReservoir(lowReservoirAlertUnits != null, Optional.ofNullable<Int>(lowReservoirAlertUnits).orElse(0))
            .build()

        val result: PumpEnactResult = executeCommand(OmnipodCommandType.CONFIGURE_ALERTS) { aapsOmnipodErosManager.configureAlerts(alertConfigurations) }!!

        if (result.success) {
            aapsLogger.info(LTag.PUMP, "Successfully configured alerts in Pod")

            podStateManager.expirationAlertTimeBeforeShutdown = expirationReminderTimeBeforeShutdown
            podStateManager.lowReservoirAlertUnits = lowReservoirAlertUnits

            uiInteraction.addNotificationValidFor(
                Notification.OMNIPOD_POD_ALERTS_UPDATED,
                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation_expiration_alerts_updated),
                Notification.INFO, 60
            )
        } else {
            aapsLogger.warn(LTag.PUMP, "Failed to configure alerts in Pod")
        }

        return result
    }

    private fun handleTimeChange(requestedByUser: Boolean): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "Setting time, requestedByUser={}", requestedByUser)
        val result: PumpEnactResult =
            if (requestedByUser || aapsOmnipodErosManager.isTimeChangeEventEnabled) {
                executeCommand(OmnipodCommandType.SET_TIME) { aapsOmnipodErosManager.setTime(!requestedByUser) }!!
            } else {
                // Even if automatically changing the time is disabled, we still want to at least do a GetStatus request,
                // in order to update the Pod's activation time, which we need for calculating the time on the Pod
                getPodStatus()
            }

        if (result.success) {
            this.hasTimeDateOrTimeZoneChanged = false
            timeChangeRetries = 0

            if (!requestedByUser && aapsOmnipodErosManager.isTimeChangeEventEnabled) {
                uiInteraction.addNotificationValidFor(
                    Notification.TIME_OR_TIMEZONE_CHANGE,
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation_time_on_pod_updated),
                    Notification.INFO, 60
                )
            }
        } else {
            if (!requestedByUser) {
                timeChangeRetries++

                if (timeChangeRetries > 3) {
                    if (aapsOmnipodErosManager.isTimeChangeEventEnabled) {
                        uiInteraction.addNotificationValidFor(
                            Notification.TIME_OR_TIMEZONE_CHANGE,
                            rh.gs(R.string.omnipod_eros_error_automatic_time_or_timezone_change_failed),
                            Notification.INFO, 60
                        )
                    }
                    this.hasTimeDateOrTimeZoneChanged = false
                    timeChangeRetries = 0
                }
            }
        }

        return result
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.info(LTag.PUMP, "Time, Date and/or TimeZone changed. [changeType=" + timeChangeType.name + ", eventHandlingEnabled=" + aapsOmnipodErosManager.isTimeChangeEventEnabled + "]")

        val now = Instant.now()
        if (timeChangeType == TimeChangeType.TimeChanged && now.isBefore(lastTimeDateOrTimeZoneUpdate.plus(Duration.standardDays(1L)))) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because not a TZ or DST time change and the last one happened less than 24 hours ago.")
            return
        }
        if (!podStateManager.isPodRunning) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because no Pod is active")
            return
        }

        aapsLogger.info(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver")
        lastTimeDateOrTimeZoneUpdate = now
        hasTimeDateOrTimeZoneChanged = true
    }

    override fun isUnreachableAlertTimeoutExceeded(unreachableTimeoutMilliseconds: Long): Boolean {
        // We have a separate notification for when no Pod is active, see doPodCheck()
        if (podStateManager.isPodActivationCompleted && podStateManager.lastSuccessfulCommunication != null) {
            val currentTimeMillis = System.currentTimeMillis()

            if (podStateManager.lastSuccessfulCommunication.millis + unreachableTimeoutMilliseconds < currentTimeMillis) {
                // We exceeded the user defined alert threshold. However, as we don't send periodical status requests to the Pod to prevent draining it's battery,
                // Exceeding the threshold alone is not a reason to trigger an alert: it could very well be that we just didn't need to send any commands for a while
                // Below return statement covers these cases in which we will trigger an alert:
                // - Sending the last command to the Pod failed
                // - The Pod is suspended
                // - RileyLink is in an error state
                // - RileyLink has been connecting for over RILEY_LINK_CONNECT_TIMEOUT
                return (podStateManager.lastFailedCommunication != null && podStateManager.lastSuccessfulCommunication.isBefore(podStateManager.lastFailedCommunication)) ||
                    podStateManager.isSuspended ||
                    rileyLinkServiceData.rileyLinkServiceState.isError() ||  // The below clause is a hack for working around the RL service state forever staying in connecting state on startup if the RL is switched off / unreachable
                    (rileyLinkServiceData.rileyLinkServiceState.isConnecting() && rileyLinkServiceData.lastServiceStateChange + RILEY_LINK_CONNECT_TIMEOUT_MILLIS < currentTimeMillis)
            }
        }

        return false
    }

    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun canHandleDST(): Boolean = false
    override fun finishHandshaking() {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "finishHandshaking [OmnipodPumpPlugin] - default (empty) implementation.")
    }

    override fun connect(reason: String) {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "connect (reason={}) [PumpPluginAbstract] - default (empty) implementation.$reason")
    }

    override fun waitForDisconnectionInSeconds(): Int = 0

    override fun disconnect(reason: String) {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation.$reason")
    }

    override fun stopConnecting() {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.")
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        if (percent == 0) {
            return setTempBasalAbsolute(0.0, durationInMinutes, profile, enforceNew, tbrType)
        } else {
            var absoluteValue = profile.getBasal() * (percent / 100.0)
            absoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteValue)
            aapsLogger.warn(
                LTag.PUMP,
                "setTempBasalPercent [OmnipodPumpPlugin] - You are trying to use setTempBasalPercent with percent other then 0% ($percent). This will start setTempBasalAbsolute, with calculated value ($absoluteValue). Result might not be 100% correct."
            )
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType)
        }
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [OmnipodPumpPlugin] - Not implemented.")
        return getOperationNotSupportedWithCustomText(app.aaps.pump.common.R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [OmnipodPumpPlugin] - Not implemented.")
        return getOperationNotSupportedWithCustomText(app.aaps.pump.common.R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun loadTDDs(): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [OmnipodPumpPlugin] - Not implemented.")
        return getOperationNotSupportedWithCustomText(app.aaps.pump.common.R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun isUseRileyLinkBatteryLevel(): Boolean = aapsOmnipodErosManager.isShowRileyLinkBatteryLevel

    override fun isBatteryChangeLoggingEnabled(): Boolean = aapsOmnipodErosManager.isBatteryChangeLoggingEnabled

    private fun initializeAfterRileyLinkConnection() {
        if (podStateManager.getActivationProgress().isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
            var success = false
            var i = 0
            while (STARTUP_STATUS_REQUEST_TRIES > i) {
                val result = getPodStatus()
                if (result.success) {
                    success = true
                    aapsLogger.debug(LTag.PUMP, "Successfully retrieved Pod status on startup")
                    break
                }
                i++
            }
            if (!success) {
                aapsLogger.warn(LTag.PUMP, "Failed to retrieve Pod status on startup")
                uiInteraction.addNotification(Notification.OMNIPOD_STARTUP_STATUS_REFRESH_FAILED, rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_refresh_status_on_startup), Notification.NORMAL)
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "Not retrieving Pod status on startup: no Pod running")
        }

        fabricPrivacy.logCustom("OmnipodPumpInit")
    }

    private fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        val result: PumpEnactResult = executeCommand(OmnipodCommandType.SET_BOLUS) { aapsOmnipodErosManager.bolus(detailedBolusInfo) }!!

        if (result.success) {
            if (detailedBolusInfo.bolusType == BS.Type.SMB)
                preferences.inc(ErosLongNonPreferenceKey.SmbsDelivered)
            else
                preferences.inc(ErosLongNonPreferenceKey.BolusesDelivered)
        }

        return result
    }

    private fun <T> executeCommand(commandType: OmnipodCommandType, supplier: Supplier<T?>): T? {
        try {
            aapsLogger.debug(LTag.PUMP, "Executing command: {}", commandType)

            rileyLinkUtil.rileyLinkHistory.add(RLHistoryItemOmnipod(commandType))

            return supplier.get()
        } finally {
            rxBus.send(EventRefreshOverview("Omnipod command: " + commandType.name, false))
            rxBus.send(EventOmnipodErosPumpValuesChanged())
        }
    }

    private fun verifyPodAlertConfiguration(): Boolean {
        if (podStateManager.isPodRunning) {
            val expirationReminderHoursBeforeShutdown = omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown()
            val lowReservoirAlertUnits = omnipodAlertUtil.getLowReservoirAlertUnits()

            if (expirationReminderHoursBeforeShutdown != podStateManager.expirationAlertTimeBeforeShutdown || lowReservoirAlertUnits != podStateManager.lowReservoirAlertUnits) {
                aapsLogger.warn(
                    LTag.PUMP, "Configured alerts in Pod don't match AAPS settings: expirationReminderHoursBeforeShutdown = {} (AAPS) vs {} Pod, " +
                        "lowReservoirAlertUnits = {} (AAPS) vs {} (Pod)", expirationReminderHoursBeforeShutdown, podStateManager.expirationAlertTimeBeforeShutdown,
                    lowReservoirAlertUnits, podStateManager.lowReservoirAlertUnits
                )
                return false
            }
        }
        return true
    }

    private fun readTBR(): PumpState.TemporaryBasal? {
        return pumpSync.expectedPumpState().temporaryBasal
    }

    private fun getOperationNotSupportedWithCustomText(resourceId: Int): PumpEnactResult {
        return pumpEnactResultProvider.get().success(false).enacted(false).comment(resourceId)
    }

    override fun clearAllTables() {
        erosHistoryDatabase.clearAllTables()
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val rileyLinkCategory = PreferenceCategory(context)
        parent.addPreference(rileyLinkCategory)
        rileyLinkCategory.apply {
            key = "omnipod_eros_riley_link"
            title = rh.gs(R.string.omnipod_eros_preferences_category_riley_link)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = RileyLinkIntentPreferenceKey.MacAddressSelector, title = app.aaps.pump.common.hw.rileylink.R.string.rileylink_configuration,
                    intent = Intent(context, RileyLinkBLEConfigActivity::class.java)
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = RileylinkBooleanPreferenceKey.OrangeUseScanning,
                    title = app.aaps.pump.common.hw.rileylink.R.string.orange_use_scanning_level,
                    summary = app.aaps.pump.common.hw.rileylink.R.string.orange_use_scanning_level_summary
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel,
                    title = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level,
                    summary = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level_summary
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = ErosBooleanPreferenceKey.BatteryChangeLogging,
                    title = R.string.omnipod_eros_preferences_battery_change_logging_enabled,
                    summary = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level_summary
                )
            )
        }
        val beepCategory = PreferenceCategory(context)
        parent.addPreference(beepCategory)
        beepCategory.apply {
            key = "omnipod_eros_beeps"
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
        }
        val alertsCategory = PreferenceCategory(context)
        parent.addPreference(alertsCategory)
        alertsCategory.apply {
            key = "omnipod_eros_alerts"
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
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = OmnipodBooleanPreferenceKey.AutomaticallyAcknowledgeAlerts,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_automatically_silence_alerts
                )
            )
            val notificationsCategory = PreferenceCategory(context)
            parent.addPreference(notificationsCategory)
            notificationsCategory.apply {
                key = "omnipod_eros_notifications"
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
            }

        }
        val otherCategory = PreferenceCategory(context)
        parent.addPreference(otherCategory)
        otherCategory.apply {
            key = "omnipod_eros_other_settings"
            title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_category_other)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = ErosBooleanPreferenceKey.ShowSuspendDeliveryButton,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_suspend_delivery_button_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = ErosBooleanPreferenceKey.ShowPulseLogButton,
                    title = R.string.omnipod_eros_preferences_pulse_log_button_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = ErosBooleanPreferenceKey.ShowRileyLinkStatsButton,
                    title = R.string.omnipod_eros_preferences_riley_link_stats_button_enabled
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = ErosBooleanPreferenceKey.TimeChangeEnabled,
                    title = app.aaps.pump.omnipod.common.R.string.omnipod_common_preferences_time_change_enabled
                )
            )
        }

    }

    companion object {

        const val STARTUP_STATUS_REQUEST_TRIES: Int = 2
        const val RESERVOIR_OVER_50_UNITS_DEFAULT: Double = 75.0
        private const val RILEY_LINK_CONNECT_TIMEOUT_MILLIS = 3 * 60 * 1000L // 3 minutes
        private const val STATUS_CHECK_INTERVAL_MILLIS = 60 * 1000L // 1 minute
    }
}
