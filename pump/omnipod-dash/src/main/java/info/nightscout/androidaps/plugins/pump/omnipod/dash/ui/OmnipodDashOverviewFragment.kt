package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonOverviewButtonsBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonOverviewPodInfoBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandHandleTimeChange
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandResumeDelivery
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandSilenceAlerts
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandSuspendDelivery
import info.nightscout.androidaps.plugins.pump.omnipod.dash.EventOmnipodDashPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.dash.OmnipodDashPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.databinding.OmnipodDashOverviewBinding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.databinding.OmnipodDashOverviewBluetoothStatusBinding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodConstants
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.core.ui.UIRunnable
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.events.EventQueueChanged
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// TODO generify; see OmnipodErosOverviewFragment
class OmnipodDashOverviewFragment : DaggerFragment() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var omnipodDashPumpPlugin: OmnipodDashPumpPlugin
    @Inject lateinit var podStateManager: OmnipodDashPodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var config: Config

    companion object {

        private const val REFRESH_INTERVAL_MILLIS = 15 * 1000L // 15 seconds
        private const val PLACEHOLDER = "-"
        private const val MAX_TIME_DEVIATION_MINUTES = 10L
    }

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateUi() }
            handler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        }
    }

    private var _binding: OmnipodDashOverviewBinding? = null
    private var _bluetoothStatusBinding: OmnipodDashOverviewBluetoothStatusBinding? = null
    private var _podInfoBinding: OmnipodCommonOverviewPodInfoBinding? = null
    private var _buttonBinding: OmnipodCommonOverviewButtonsBinding? = null

    // These properties are only valid between onCreateView and onDestroyView.
    val binding get() = _binding!!
    private val bluetoothStatusBinding get() = _bluetoothStatusBinding!!
    private val podInfoBinding get() = _podInfoBinding!!
    private val buttonBinding get() = _buttonBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodDashOverviewBinding.inflate(inflater, container, false).also {
            _buttonBinding = OmnipodCommonOverviewButtonsBinding.bind(it.root)
            _podInfoBinding = OmnipodCommonOverviewPodInfoBinding.bind(it.root)
            _bluetoothStatusBinding = OmnipodDashOverviewBluetoothStatusBinding.bind(it.root)
            _binding = it
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonBinding.buttonPodManagement.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.PREFERENCES,
                    UIRunnable { startActivity(Intent(context, DashPodManagementActivity::class.java)) }
                )
            }
        }

        buttonBinding.buttonResumeDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandResumeDelivery(),
                DisplayResultDialogCallback(
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_resume_delivery),
                    true
                ).messageOnSuccess(rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_confirmation_delivery_resumed))
            )
        }

        buttonBinding.buttonRefreshStatus.setOnClickListener {
            disablePodActionButtons()
            commandQueue.readStatus(
                rh.gs(R.string.requested_by_user),
                DisplayResultDialogCallback(
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_refresh_status),
                    false
                )
            )
        }

        buttonBinding.buttonSilenceAlerts.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSilenceAlerts(),
                DisplayResultDialogCallback(
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_silence_alerts),
                    false
                )
                    .messageOnSuccess(rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_confirmation_silenced_alerts))
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) }
            )
        }

        buttonBinding.buttonSuspendDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSuspendDelivery(),
                DisplayResultDialogCallback(
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_suspend_delivery),
                    true
                )
                    .messageOnSuccess(rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_confirmation_suspended_delivery))
            )
        }

        buttonBinding.buttonSetTime.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandHandleTimeChange(true),
                DisplayResultDialogCallback(rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_set_time), true)
                    .messageOnSuccess(rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_confirmation_time_on_pod_updated))
            )
        }
        if (config.isEngineeringMode()) {
            bluetoothStatusBinding.deliveryStatus.visibility = View.VISIBLE
            bluetoothStatusBinding.connectionQuality.visibility = View.VISIBLE
        }
        podInfoBinding.omnipodCommonOverviewLotNumberLayout.visibility = View.GONE
        podInfoBinding.omnipodCommonOverviewPodUniqueIdLayout.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        disposables += rxBus
            .toObservable(EventOmnipodDashPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    updateOmnipodStatus()
                    updatePodActionButtons()
                },
                fabricPrivacy::logException
            )
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    updateQueueStatus()
                    updatePodActionButtons()
                },
                fabricPrivacy::logException
            )
        disposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    updatePodActionButtons()
                },
                fabricPrivacy::logException
            )

        disposables += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .delay(30, TimeUnit.MILLISECONDS, aapsSchedulers.main)
            .subscribe(
                {
                    updateBluetoothConnectionStatus(it)
                },
                fabricPrivacy::logException
            )
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
        handler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _bluetoothStatusBinding = null
        _buttonBinding = null
        _podInfoBinding = null
    }

    private fun updateUi() {
        updateBluetoothStatus()
        updateOmnipodStatus()
        updatePodActionButtons()
        updateQueueStatus()
    }

    private fun updateBluetoothConnectionStatus(event: EventPumpStatusChanged) {
        val status = event.getStatus(requireContext())
        bluetoothStatusBinding.omnipodDashBluetoothStatus.text = status
    }

    private fun updateBluetoothStatus() {
        bluetoothStatusBinding.omnipodDashBluetoothAddress.text = podStateManager.bluetoothAddress
            ?: PLACEHOLDER

        val connectionSuccessPercentage = podStateManager.connectionSuccessRatio() * 100
        val connectionAttempts = podStateManager.failedConnectionsAfterRetries + podStateManager.successfulConnectionAttemptsAfterRetries
        val successPercentageString = String.format("%.2f %%", connectionSuccessPercentage)
        val quality =
            "${podStateManager.successfulConnectionAttemptsAfterRetries}/$connectionAttempts :: $successPercentageString"
        bluetoothStatusBinding.omnipodDashBluetoothConnectionQuality.text = quality
        val connectionStatsColor = rh.gac(
            context,
            when {
                connectionSuccessPercentage < 70 && podStateManager.successfulConnectionAttemptsAfterRetries > 50 ->
                    info.nightscout.core.ui.R.attr.warningColor
                connectionSuccessPercentage < 90 && podStateManager.successfulConnectionAttemptsAfterRetries > 50 ->
                    info.nightscout.core.ui.R.attr.omniYellowColor
                else                                                                                              ->
                    info.nightscout.core.ui.R.attr.defaultTextColor
            }
        )
        bluetoothStatusBinding.omnipodDashBluetoothConnectionQuality.setTextColor(connectionStatsColor)
        bluetoothStatusBinding.omnipodDashDeliveryStatus.text = podStateManager.deliveryStatus?.let {
            podStateManager.deliveryStatus.toString()
        } ?: PLACEHOLDER
    }

    private fun updateOmnipodStatus() {
        updateLastConnection()
        updateLastBolus()
        updateTempBasal()
        updatePodStatus()

        val errors = ArrayList<String>()

        if (podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID)) {
            podInfoBinding.uniqueId.text = PLACEHOLDER
            podInfoBinding.podLot.text = PLACEHOLDER
            podInfoBinding.podSequenceNumber.text = PLACEHOLDER
            podInfoBinding.firmwareVersion.text = PLACEHOLDER
            podInfoBinding.timeOnPod.text = PLACEHOLDER
            podInfoBinding.podExpiryDate.text = PLACEHOLDER
            podInfoBinding.podExpiryDate.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.defaultTextColor))
            podInfoBinding.baseBasalRate.text = PLACEHOLDER
            podInfoBinding.totalDelivered.text = PLACEHOLDER
            podInfoBinding.reservoir.text = PLACEHOLDER
            podInfoBinding.reservoir.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.defaultTextColor))
            podInfoBinding.podActiveAlerts.text = PLACEHOLDER
        } else {
            podInfoBinding.uniqueId.text = podStateManager.uniqueId.toString()
            podInfoBinding.podLot.text = podStateManager.lotNumber.toString()
            podInfoBinding.podSequenceNumber.text = podStateManager.podSequenceNumber.toString()
            podInfoBinding.firmwareVersion.text = rh.gs(
                R.string.omnipod_dash_overview_firmware_version_value,
                podStateManager.firmwareVersion.toString(),
                podStateManager.bluetoothVersion.toString()
            )

            val timeZone = podStateManager.timeZoneId?.let { timeZoneId ->
                podStateManager.timeZoneUpdated?.let { timeZoneUpdated ->
                    val tz = TimeZone.getTimeZone(timeZoneId)
                    val inDST = tz.inDaylightTime(Date(timeZoneUpdated))
                    val locale = resources.configuration.locales.get(0)
                    tz.getDisplayName(inDST, TimeZone.SHORT, locale)
                } ?: PLACEHOLDER
            } ?: PLACEHOLDER

            podInfoBinding.timeOnPod.text = podStateManager.time?.let {
                rh.gs(
                    info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_time_with_timezone,
                    dateUtil.dateAndTimeString(it.toEpochSecond() * 1000),
                    timeZone
                )
            } ?: PLACEHOLDER

            val timeDeviationTooBig = podStateManager.timeDrift?.let {
                Duration.ofMinutes(MAX_TIME_DEVIATION_MINUTES).minus(
                    it.abs()
                ).isNegative
            } ?: false
            podInfoBinding.timeOnPod.setTextColor(
                rh.gac(
                    context,
                    when {
                        !podStateManager.sameTimeZone ->
                            info.nightscout.core.ui.R.attr.omniMagentaColor
                        timeDeviationTooBig           ->
                            info.nightscout.core.ui.R.attr.omniYellowColor
                        else                          ->
                            info.nightscout.core.ui.R.attr.defaultTextColor
                    }
                )
            )

            // Update Pod expiry time
            val expiresAt = podStateManager.expiry
            podInfoBinding.podExpiryDate.text = expiresAt?.let {
                dateUtil.dateAndTimeString(it.toEpochSecond() * 1000)
            }
                ?: PLACEHOLDER
            podInfoBinding.podExpiryDate.setTextColor(
                rh.gac(
                    context,
                    when {
                        expiresAt != null && ZonedDateTime.now().isAfter(expiresAt)               ->
                            info.nightscout.core.ui.R.attr.warningColor
                        expiresAt != null && ZonedDateTime.now().isAfter(expiresAt.minusHours(4)) ->
                            info.nightscout.core.ui.R.attr.omniYellowColor
                        else                                                                      ->
                            info.nightscout.core.ui.R.attr.defaultTextColor
                    }
                )
            )

            podStateManager.alarmType?.let {
                errors.add(
                    rh.gs(
                        info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_pod_fault_description,
                        it.value,
                        it.toString()
                    )
                )
            }

            // base basal rate
            podInfoBinding.baseBasalRate.text =
                if (podStateManager.basalProgram != null && !podStateManager.isSuspended) {
                    rh.gs(
                        info.nightscout.core.ui.R.string.pump_base_basal_rate,
                        omnipodDashPumpPlugin.model()
                            .determineCorrectBasalSize(podStateManager.basalProgram!!.rateAt(System.currentTimeMillis()))
                    )
                } else {
                    PLACEHOLDER
                }

            // total delivered
            podInfoBinding.totalDelivered.text =
                if (podStateManager.isActivationCompleted && podStateManager.pulsesDelivered != null) {
                    rh.gs(
                        info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_overview_total_delivered_value,
                        (podStateManager.pulsesDelivered!! * PodConstants.POD_PULSE_BOLUS_UNITS)
                    )
                } else {
                    PLACEHOLDER
                }

            // reservoir
            if (podStateManager.pulsesRemaining == null) {
                podInfoBinding.reservoir.text =
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_overview_reservoir_value_over50)
                podInfoBinding.reservoir.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.defaultTextColor))
            } else {
                // TODO
                // val lowReservoirThreshold = (omnipodAlertUtil.lowReservoirAlertUnits
                //    ?: OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD).toDouble()
                val lowReservoirThreshold: Short = PodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD

                podInfoBinding.reservoir.text = rh.gs(
                    info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_overview_reservoir_value,
                    (podStateManager.pulsesRemaining!! * PodConstants.POD_PULSE_BOLUS_UNITS)
                )
                podInfoBinding.reservoir.setTextColor(
                    rh.gac(
                        context,
                        if (podStateManager.pulsesRemaining!! < lowReservoirThreshold) {
                            info.nightscout.core.ui.R.attr.warningColor
                        } else {
                            info.nightscout.core.ui.R.attr.defaultTextColor
                        }
                    )
                )
            }

            podInfoBinding.podActiveAlerts.text = podStateManager.activeAlerts?.let { it ->
                it.joinToString(System.lineSeparator()) { t -> translatedActiveAlert(t) }
            } ?: PLACEHOLDER
        }

        if (errors.size == 0) {
            podInfoBinding.errors.text = PLACEHOLDER
            podInfoBinding.errors.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.defaultTextColor))
        } else {
            podInfoBinding.errors.text = StringUtils.join(errors, System.lineSeparator())
            podInfoBinding.errors.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.warningColor))
        }
    }

    private fun translatedActiveAlert(alert: AlertType): String {
        val id = when (alert) {
            AlertType.LOW_RESERVOIR       ->
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_alert_low_reservoir
            AlertType.EXPIRATION          ->
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_alert_expiration_advisory
            AlertType.EXPIRATION_IMMINENT ->
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_alert_expiration
            AlertType.USER_SET_EXPIRATION ->
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_alert_expiration_advisory
            AlertType.AUTO_OFF            ->
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_alert_shutdown_imminent
            AlertType.SUSPEND_IN_PROGRESS ->
                R.string.omnipod_common_alert_delivery_suspended
            AlertType.SUSPEND_ENDED       ->
                R.string.omnipod_common_alert_delivery_suspended
            else                          ->
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_alert_unknown_alert
        }
        return rh.gs(id)
    }

    private fun updateLastConnection() {
        if (podStateManager.isUniqueIdSet) {
            podInfoBinding.lastConnection.text = readableDuration(
                Duration.ofMillis(
                    System.currentTimeMillis() -
                        podStateManager.lastUpdatedSystem,

                    )
            )
            val lastConnectionColor =
                rh.gac(
                    context,
                    if (omnipodDashPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().toMillis())) {
                        info.nightscout.core.ui.R.attr.warningColor
                    } else {
                        info.nightscout.core.ui.R.attr.defaultTextColor
                    }
                )
            podInfoBinding.lastConnection.setTextColor(lastConnectionColor)
        } else {
            podInfoBinding.lastConnection.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.defaultTextColor))
            podInfoBinding.lastConnection.text = PLACEHOLDER
        }
    }

    private fun updatePodStatus() {
        podInfoBinding.podStatus.text = if (podStateManager.activationProgress == ActivationProgress.NOT_STARTED) {
            rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_no_active_pod)
        } else if (!podStateManager.isActivationCompleted) {
            if (!podStateManager.isUniqueIdSet) {
                rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIME_COMPLETED)) {
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podStatus!!.isRunning()) {
                if (podStateManager.isSuspended) {
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_suspended)
                } else {
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_status_running)
                }
                /*
            } else if (podStateManager.podStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                rh.gs(R.string.omnipod_common_pod_status_pod_fault)
            } else if (podStateManager.podStatus == PodProgressStatus.INACTIVE) {
                rh.gs(R.string.omnipod_common_pod_status_inactive)
                 */
            } else {
                podStateManager.podStatus.toString()
            }
        }

        val podStatusColor = rh.gac(
            context,
            when {
                !podStateManager.isActivationCompleted || podStateManager.isPodKaput || podStateManager.isSuspended ->
                    info.nightscout.core.ui.R.attr.warningColor
                podStateManager.activeCommand != null                                                               ->
                    info.nightscout.core.ui.R.attr.omniYellowColor
                else                                                                                                ->
                    info.nightscout.core.ui.R.attr.defaultTextColor
            }
        )
        podInfoBinding.podStatus.setTextColor(podStatusColor)
    }

    private fun updateLastBolus() {

        var textColorAttr = info.nightscout.core.ui.R.attr.defaultTextColor
        podStateManager.activeCommand?.let {
            val requestedBolus = it.requestedBolus
            if (requestedBolus != null) {
                var text = rh.gs(
                    info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_overview_last_bolus_value,
                    omnipodDashPumpPlugin.model().determineCorrectBolusSize(requestedBolus),
                    rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname),
                    readableDuration(Duration.ofMillis(SystemClock.elapsedRealtime() - it.createdRealtime))
                )
                text += " (uncertain) "
                textColorAttr = info.nightscout.core.ui.R.attr.warningColor
                podInfoBinding.lastBolus.text = text
                podInfoBinding.lastBolus.setTextColor(rh.gac(context, textColorAttr))
                return
            }
        }

        podInfoBinding.lastBolus.setTextColor(rh.gac(context, textColorAttr))
        podStateManager.lastBolus?.let {
            // display requested units if delivery is in progress
            val bolusSize = it.deliveredUnits()
                ?: it.requestedUnits

            val text = rh.gs(
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_overview_last_bolus_value,
                omnipodDashPumpPlugin.model().determineCorrectBolusSize(bolusSize),
                rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname),
                readableDuration(Duration.ofMillis(System.currentTimeMillis() - it.startTime))
            )
            if (!it.deliveryComplete) {
                textColorAttr = info.nightscout.core.ui.R.attr.omniYellowColor
            }
            podInfoBinding.lastBolus.text = text
            podInfoBinding.lastBolus.setTextColor(rh.gac(context, textColorAttr))
            return
        }
        podInfoBinding.lastBolus.text = PLACEHOLDER
    }

    private fun updateTempBasal() {
        val tempBasal = podStateManager.tempBasal
        if (podStateManager.isActivationCompleted && podStateManager.tempBasalActive && tempBasal != null) {
            val startTime = tempBasal.startTime
            val rate = tempBasal.rate
            val duration = tempBasal.durationInMinutes

            val minutesRunning = Duration.ofMillis(System.currentTimeMillis() - startTime).toMinutes()

            podInfoBinding.tempBasal.text = rh.gs(
                info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_overview_temp_basal_value,
                rate,
                dateUtil.timeString(startTime),
                minutesRunning,
                duration
            )
        } else {
            podInfoBinding.tempBasal.text = PLACEHOLDER
        }
    }

    private fun updateQueueStatus() {
        if (isQueueEmpty()) {
            podInfoBinding.queue.visibility = View.GONE
        } else {
            podInfoBinding.queue.visibility = View.VISIBLE
            podInfoBinding.queue.text = commandQueue.spannedStatus().toString()
        }
    }

    private fun updatePodActionButtons() {
        updateRefreshStatusButton()
        updateResumeDeliveryButton()
        updateSilenceAlertsButton()
        updateSuspendDeliveryButton()
        updateSetTimeButton()
    }

    private fun disablePodActionButtons() {
        buttonBinding.buttonSilenceAlerts.isEnabled = false
        buttonBinding.buttonResumeDelivery.isEnabled = false
        buttonBinding.buttonSuspendDelivery.isEnabled = false
        buttonBinding.buttonSetTime.isEnabled = false
        buttonBinding.buttonRefreshStatus.isEnabled = false
    }

    private fun updateRefreshStatusButton() {
        buttonBinding.buttonRefreshStatus.isEnabled =
            podStateManager.isUniqueIdSet &&
                isQueueEmpty()
    }

    private fun updateResumeDeliveryButton() {
        if (podStateManager.isPodRunning &&
            (podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class.java))
        ) {
            buttonBinding.buttonResumeDelivery.visibility = View.VISIBLE
            buttonBinding.buttonResumeDelivery.isEnabled = isQueueEmpty()
        } else {
            buttonBinding.buttonResumeDelivery.visibility = View.GONE
        }
    }

    private fun updateSilenceAlertsButton() {
        if (!isAutomaticallySilenceAlertsEnabled() &&
            podStateManager.isPodRunning &&
            (
                podStateManager.activeAlerts!!.size > 0 ||
                    commandQueue.isCustomCommandInQueue(CommandSilenceAlerts::class.java)
                )
        ) {
            buttonBinding.buttonSilenceAlerts.visibility = View.VISIBLE
            buttonBinding.buttonSilenceAlerts.isEnabled = isQueueEmpty()
        } else {
            buttonBinding.buttonSilenceAlerts.visibility = View.GONE
        }
    }

    private fun updateSuspendDeliveryButton() {
        // If the Pod is currently suspended, we show the Resume delivery button instead.
        // disable the 'suspendDelivery' button.
        buttonBinding.buttonSuspendDelivery.visibility = View.GONE
    }

    private fun updateSetTimeButton() {
        if (podStateManager.isActivationCompleted && !podStateManager.sameTimeZone) {
            buttonBinding.buttonSetTime.visibility = View.VISIBLE
            buttonBinding.buttonSetTime.isEnabled = !podStateManager.isSuspended && isQueueEmpty()
        } else {
            buttonBinding.buttonSetTime.visibility = View.GONE
        }
    }

    private fun isAutomaticallySilenceAlertsEnabled(): Boolean {
        return sp.getBoolean(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_preferences_automatically_silence_alerts, false)
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
            uiInteraction.runAlarm(message, title, if (withSound) info.nightscout.core.ui.R.raw.boluserror else 0)
    }

    private fun displayOkDialog(title: String, message: String) {
        context?.let {
            UIRunnable {
                OKDialog.show(it, title, message, null)
            }.run()
        }
    }

    private fun readableDuration(duration: Duration): String {
        val hours = duration.toHours().toInt()
        val minutes = duration.toMinutes().toInt()
        val seconds = duration.seconds
        when {
            seconds < 10           -> {
                return rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_moments_ago)
            }

            seconds < 60           -> {
                return rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_less_than_a_minute_ago)
            }

            seconds < 60 * 60      -> { // < 1 hour
                return rh.gs(
                    info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_time_ago,
                    rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_minutes, minutes, minutes)
                )
            }

            seconds < 24 * 60 * 60 -> { // < 1 day
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return rh.gs(
                        info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_time_ago,
                        rh.gs(
                            info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_composite_time,
                            rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_hours, hours, hours),
                            rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_minutes, minutesLeft, minutesLeft)
                        )
                    )
                return rh.gs(
                    info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_time_ago,
                    rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_hours, hours, hours)
                )
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return rh.gs(
                        info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_time_ago,
                        rh.gs(
                            info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_composite_time,
                            rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_days, days, days),
                            rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_hours, hoursLeft, hoursLeft)
                        )
                    )
                return rh.gs(
                    info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_time_ago,
                    rh.gq(info.nightscout.androidaps.plugins.pump.omnipod.common.R.plurals.omnipod_common_days, days, days)
                )
            }
        }
    }

    private fun isQueueEmpty(): Boolean {
        return commandQueue.size() == 0 && commandQueue.performing() == null
    }

    // FIXME ideally we should just have access to LocalAlertUtils here
    private fun getPumpUnreachableTimeout(): Duration {
        return Duration.ofMinutes(
            sp.getInt(
                info.nightscout.core.utils.R.string.key_pump_unreachable_threshold_minutes,
                Constants.DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES
            ).toLong()
        )
    }

    inner class DisplayResultDialogCallback(
        private val errorMessagePrefix: String,
        private val withSoundOnError: Boolean
    ) : Callback() {

        private var messageOnSuccess: String? = null
        private var actionOnSuccess: Runnable? = null

        override fun run() {
            if (result.success) {
                val messageOnSuccess = this.messageOnSuccess
                if (messageOnSuccess != null) {
                    displayOkDialog(rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_confirmation), messageOnSuccess)
                }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(
                    rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_warning),
                    rh.gs(
                        info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon,
                        errorMessagePrefix,
                        result.comment
                    ),
                    withSoundOnError
                )
            }
        }

        fun messageOnSuccess(message: String): DisplayResultDialogCallback {
            messageOnSuccess = message
            return this
        }

        fun actionOnSuccess(action: Runnable): DisplayResultDialogCallback {
            actionOnSuccess = action
            return this
        }
    }
}
