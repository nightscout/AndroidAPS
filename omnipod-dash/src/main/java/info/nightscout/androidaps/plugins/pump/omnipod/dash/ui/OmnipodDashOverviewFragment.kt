package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
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
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

// TODO generify; see OmnipodErosOverviewFragment
class OmnipodDashOverviewFragment : DaggerFragment() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var omnipodDashPumpPlugin: OmnipodDashPumpPlugin
    @Inject lateinit var podStateManager: OmnipodDashPodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var buildHelper: BuildHelper

    companion object {

        private const val REFRESH_INTERVAL_MILLIS = 15 * 1000L // 15 seconds
        private const val PLACEHOLDER = "-"
        private const val MAX_TIME_DEVIATION_MINUTES = 10L
    }

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler(Looper.getMainLooper())
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateUi() }
            loopHandler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        }
    }

    private var _binding: OmnipodDashOverviewBinding? = null
    private var _bluetoothStatusBinding: OmnipodDashOverviewBluetoothStatusBinding? = null
    private var _podInfoBinding: OmnipodCommonOverviewPodInfoBinding? = null
    private var _buttonBinding: OmnipodCommonOverviewButtonsBinding? = null

    // These properties are only valid between onCreateView and
    // onDestroyView.
    val binding get() = _binding!!
    val bluetoothStatusBinding get() = _bluetoothStatusBinding!!
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
            // TODO add protection
            startActivity(Intent(context, DashPodManagementActivity::class.java))
        }

        buttonBinding.buttonResumeDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandResumeDelivery(),
                DisplayResultDialogCallback(
                    resourceHelper.gs(R.string.omnipod_common_error_failed_to_resume_delivery),
                    true
                ).messageOnSuccess(resourceHelper.gs(R.string.omnipod_common_confirmation_delivery_resumed))
            )
        }

        buttonBinding.buttonRefreshStatus.setOnClickListener {
            disablePodActionButtons()
            commandQueue.readStatus(
                "REQUESTED BY USER",
                DisplayResultDialogCallback(
                    resourceHelper.gs(R.string.omnipod_common_error_failed_to_refresh_status),
                    false
                )
            )
        }

        buttonBinding.buttonSilenceAlerts.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSilenceAlerts(),
                DisplayResultDialogCallback(
                    resourceHelper.gs(R.string.omnipod_common_error_failed_to_silence_alerts),
                    false
                )
                    .messageOnSuccess(resourceHelper.gs(R.string.omnipod_common_confirmation_silenced_alerts))
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) }
            )
        }

        buttonBinding.buttonSuspendDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSuspendDelivery(),
                DisplayResultDialogCallback(
                    resourceHelper.gs(R.string.omnipod_common_error_failed_to_suspend_delivery),
                    true
                )
                    .messageOnSuccess(resourceHelper.gs(R.string.omnipod_common_confirmation_suspended_delivery))
            )
        }

        buttonBinding.buttonSetTime.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandHandleTimeChange(true),
                DisplayResultDialogCallback(resourceHelper.gs(R.string.omnipod_common_error_failed_to_set_time), true)
                    .messageOnSuccess(resourceHelper.gs(R.string.omnipod_common_confirmation_time_on_pod_updated))
            )
        }
        if (buildHelper.isEngineeringMode()) {
            bluetoothStatusBinding.deliveryStatus.visibility = View.VISIBLE
            bluetoothStatusBinding.connectionQuality.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
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
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
        loopHandler.removeCallbacks(refreshLoop)
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

    private fun updateBluetoothStatus() {
        bluetoothStatusBinding.omnipodDashBluetoothAddress.text = podStateManager.bluetoothAddress
            ?: PLACEHOLDER
        bluetoothStatusBinding.omnipodDashBluetoothStatus.text =
            when (podStateManager.bluetoothConnectionState) {
                OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED ->
                    "{fa-bluetooth}"
                OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED ->
                    "{fa-bluetooth-b}"
                OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING ->
                    "{fa-bluetooth-b spin}"
            }

        val connectionSuccessPercentage = podStateManager.connectionSuccessRatio() * 100
        val successPercentageString = String.format("%.2f %%", podStateManager.connectionSuccessRatio())
        val quality =
            "${podStateManager.successfulConnections}/${podStateManager.connectionAttempts} :: $successPercentageString"
        bluetoothStatusBinding.omnipodDashBluetoothConnectionQuality.text = quality
        val connectionStatsColor = when {
            connectionSuccessPercentage > 90 ->
                Color.WHITE
            connectionSuccessPercentage > 60 ->
                Color.YELLOW
            else ->
                Color.RED
        }
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
            podInfoBinding.podExpiryDate.setTextColor(Color.WHITE)
            podInfoBinding.baseBasalRate.text = PLACEHOLDER
            podInfoBinding.totalDelivered.text = PLACEHOLDER
            podInfoBinding.reservoir.text = PLACEHOLDER
            podInfoBinding.reservoir.setTextColor(Color.WHITE)
            podInfoBinding.podActiveAlerts.text = PLACEHOLDER
        } else {
            podInfoBinding.uniqueId.text = podStateManager.uniqueId.toString()
            podInfoBinding.podLot.text = podStateManager.lotNumber.toString()
            podInfoBinding.podSequenceNumber.text = podStateManager.podSequenceNumber.toString()
            podInfoBinding.firmwareVersion.text = resourceHelper.gs(
                R.string.omnipod_dash_overview_firmware_version_value,
                podStateManager.firmwareVersion.toString(),
                podStateManager.bluetoothVersion.toString()
            )

            // Update time on Pod
            podInfoBinding.timeOnPod.text = podStateManager.time?.let {
                resourceHelper.gs(
                    R.string.omnipod_common_time_with_timezone,
                    dateUtil.dateAndTimeString(it.toEpochSecond() * 1000),
                    podStateManager.timeZone.getDisplayName(true, TimeZone.SHORT)
                )
            } ?: PLACEHOLDER

            val timeDeviationTooBig = podStateManager.timeDrift?.let {
                Duration.ofMinutes(MAX_TIME_DEVIATION_MINUTES).minus(
                    it.abs()
                ).isNegative
            } ?: false
            podInfoBinding.timeOnPod.setTextColor(
                when {
                    !podStateManager.sameTimeZone ->
                        Color.MAGENTA
                    timeDeviationTooBig ->
                        Color.YELLOW
                    else ->
                        Color.WHITE
                }
            )

            // Update Pod expiry time
            val expiresAt = podStateManager.expiry
            podInfoBinding.podExpiryDate.text = expiresAt?.let {
                dateUtil.dateAndTimeString(it.toEpochSecond() * 1000)
            }
                ?: PLACEHOLDER
            podInfoBinding.podExpiryDate.setTextColor(
                if (expiresAt != null && ZonedDateTime.now().isAfter(expiresAt))
                    Color.RED
                else
                    Color.WHITE
            )

            podStateManager.alarmType?.let {
                errors.add(
                    resourceHelper.gs(
                        R.string.omnipod_common_pod_status_pod_fault_description,
                        it.value,
                        it.toString()
                    )
                )
            }

            // base basal rate
            podInfoBinding.baseBasalRate.text =
                if (podStateManager.basalProgram != null && !podStateManager.isSuspended) {
                    resourceHelper.gs(
                        R.string.pump_basebasalrate,
                        omnipodDashPumpPlugin.model()
                            .determineCorrectBasalSize(podStateManager.basalProgram!!.rateAt(Date()))
                    )
                } else {
                    PLACEHOLDER
                }

            // total delivered
            podInfoBinding.totalDelivered.text =
                if (podStateManager.isActivationCompleted && podStateManager.pulsesDelivered != null) {
                    resourceHelper.gs(
                        R.string.omnipod_common_overview_total_delivered_value,
                        podStateManager.pulsesDelivered!! * 0.05
                    )
                } else {
                    PLACEHOLDER
                }

            // reservoir
            if (podStateManager.pulsesRemaining == null) {
                podInfoBinding.reservoir.text =
                    resourceHelper.gs(R.string.omnipod_common_overview_reservoir_value_over50)
                podInfoBinding.reservoir.setTextColor(Color.WHITE)
            } else {
                // TODO
                // val lowReservoirThreshold = (omnipodAlertUtil.lowReservoirAlertUnits
                //    ?: OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD).toDouble()
                val lowReservoirThreshold: Short = 20

                podInfoBinding.reservoir.text = resourceHelper.gs(
                    R.string.omnipod_common_overview_reservoir_value,
                    (podStateManager.pulsesRemaining!! * 0.05)
                )
                podInfoBinding.reservoir.setTextColor(
                    if (podStateManager.pulsesRemaining!! < lowReservoirThreshold) {
                        Color.RED
                    } else {
                        Color.WHITE
                    }
                )
            }

            podInfoBinding.podActiveAlerts.text = podStateManager.activeAlerts?.let { it ->
                it.joinToString(",") { it.toString() }
            } ?: PLACEHOLDER
        }

        if (errors.size == 0) {
            podInfoBinding.errors.text = PLACEHOLDER
            podInfoBinding.errors.setTextColor(Color.WHITE)
        } else {
            podInfoBinding.errors.text = StringUtils.join(errors, System.lineSeparator())
            podInfoBinding.errors.setTextColor(Color.RED)
        }
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
                if (omnipodDashPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().toMillis())) {
                    Color.RED
                } else {
                    Color.WHITE
                }
            podInfoBinding.lastConnection.setTextColor(lastConnectionColor)
        } else {
            podInfoBinding.lastConnection.setTextColor(Color.WHITE)
            podInfoBinding.lastConnection.text = PLACEHOLDER
        }
    }

    private fun updatePodStatus() {
        podInfoBinding.podStatus.text = if (podStateManager.activationProgress == ActivationProgress.NOT_STARTED) {
            resourceHelper.gs(R.string.omnipod_common_pod_status_no_active_pod)
        } else if (!podStateManager.isActivationCompleted) {
            if (!podStateManager.isUniqueIdSet) {
                resourceHelper.gs(R.string.omnipod_common_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIME_COMPLETED)) {
                    resourceHelper.gs(R.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    resourceHelper.gs(R.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podStatus!!.isRunning()) {
                if (podStateManager.isSuspended) {
                    resourceHelper.gs(R.string.omnipod_common_pod_status_suspended)
                } else {
                    resourceHelper.gs(R.string.omnipod_common_pod_status_running)
                }
                /*
            } else if (podStateManager.podStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                resourceHelper.gs(R.string.omnipod_common_pod_status_pod_fault)
            } else if (podStateManager.podStatus == PodProgressStatus.INACTIVE) {
                resourceHelper.gs(R.string.omnipod_common_pod_status_inactive)
                 */
            } else {
                podStateManager.podStatus.toString()
            }
        }

        val podStatusColor = when {
            !podStateManager.isActivationCompleted || podStateManager.isPodKaput || podStateManager.isSuspended ->
                Color.RED
            podStateManager.activeCommand != null ->
                Color.YELLOW
            else ->
                Color.WHITE
        }
        podInfoBinding.podStatus.setTextColor(podStatusColor)
    }

    private fun updateLastBolus() {

        var textColor = Color.WHITE
        podStateManager.activeCommand?.let {
            val requestedBolus = it.requestedBolus
            if (requestedBolus != null) {
                var text = resourceHelper.gs(
                    R.string.omnipod_common_overview_last_bolus_value,
                    omnipodDashPumpPlugin.model().determineCorrectBolusSize(requestedBolus),
                    resourceHelper.gs(R.string.insulin_unit_shortname),
                    readableDuration(Duration.ofMillis(SystemClock.elapsedRealtime() - it.createdRealtime))
                )
                text += " (uncertain) "
                textColor = Color.RED
                podInfoBinding.lastBolus.text = text
                podInfoBinding.lastBolus.setTextColor(textColor)
                return
            }
        }

        podInfoBinding.lastBolus.setTextColor(textColor)
        podStateManager.lastBolus?.let {
            // display requested units if delivery is in progress
            val bolusSize = it.deliveredUnits()
                ?: it.requestedUnits

            val text = resourceHelper.gs(
                R.string.omnipod_common_overview_last_bolus_value,
                omnipodDashPumpPlugin.model().determineCorrectBolusSize(bolusSize),
                resourceHelper.gs(R.string.insulin_unit_shortname),
                readableDuration(Duration.ofMillis(System.currentTimeMillis() - it.startTime))
            )
            if (!it.deliveryComplete) {
                textColor = Color.YELLOW
            }
            podInfoBinding.lastBolus.text = text
            podInfoBinding.lastBolus.setTextColor(textColor)
        }
    }

    private fun updateTempBasal() {
        val tempBasal = podStateManager.tempBasal
        if (podStateManager.isActivationCompleted && podStateManager.tempBasalActive && tempBasal != null) {
            val startTime = tempBasal.startTime
            val rate = tempBasal.rate
            val duration = tempBasal.durationInMinutes

            val minutesRunning = 0 // TODO

            podInfoBinding.tempBasal.text = resourceHelper.gs(
                R.string.omnipod_common_overview_temp_basal_value,
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
        if (isSuspendDeliveryButtonEnabled() &&
            podStateManager.isPodRunning &&
            (!podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandSuspendDelivery::class.java))
        ) {
            buttonBinding.buttonSuspendDelivery.visibility = View.VISIBLE
            buttonBinding.buttonSuspendDelivery.isEnabled =
                podStateManager.isPodRunning && !podStateManager.isSuspended && isQueueEmpty()
        } else {
            buttonBinding.buttonSuspendDelivery.visibility = View.GONE
        }
    }

    private fun updateSetTimeButton() {
        // TODO
        /*
        if (podStateManager.isPodRunning && (podStateManager.timeDeviatesMoreThan(Duration.standardMinutes(5)) || commandQueue.isCustomCommandInQueue(CommandHandleTimeChange::class.java))) {
            buttonBinding.buttonSetTime.visibility = View.VISIBLE
            buttonBinding.buttonSetTime.isEnabled = !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            buttonBinding.buttonSetTime.visibility = View.GONE
        }
         */
    }

    private fun isAutomaticallySilenceAlertsEnabled(): Boolean {
        return sp.getBoolean(R.string.omnipod_common_preferences_automatically_silence_alerts, false)
    }

    private fun isSuspendDeliveryButtonEnabled(): Boolean {
        return sp.getBoolean(R.string.key_omnipod_common_suspend_delivery_button_enabled, false)
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        context?.let {
            ErrorHelperActivity.runAlarm(it, message, title, if (withSound) R.raw.boluserror else 0)
        }
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
            seconds < 10 -> {
                return resourceHelper.gs(R.string.omnipod_common_moments_ago)
            }

            seconds < 60 -> {
                return resourceHelper.gs(R.string.omnipod_common_less_than_a_minute_ago)
            }

            seconds < 60 * 60 -> { // < 1 hour
                return resourceHelper.gs(
                    R.string.omnipod_common_time_ago,
                    resourceHelper.gq(R.plurals.omnipod_common_minutes, minutes, minutes)
                )
            }

            seconds < 24 * 60 * 60 -> { // < 1 day
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return resourceHelper.gs(
                        R.string.omnipod_common_time_ago,
                        resourceHelper.gs(
                            R.string.omnipod_common_composite_time,
                            resourceHelper.gq(R.plurals.omnipod_common_hours, hours, hours),
                            resourceHelper.gq(R.plurals.omnipod_common_minutes, minutesLeft, minutesLeft)
                        )
                    )
                return resourceHelper.gs(
                    R.string.omnipod_common_time_ago,
                    resourceHelper.gq(R.plurals.omnipod_common_hours, hours, hours)
                )
            }

            else -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return resourceHelper.gs(
                        R.string.omnipod_common_time_ago,
                        resourceHelper.gs(
                            R.string.omnipod_common_composite_time,
                            resourceHelper.gq(R.plurals.omnipod_common_days, days, days),
                            resourceHelper.gq(R.plurals.omnipod_common_hours, hoursLeft, hoursLeft)
                        )
                    )
                return resourceHelper.gs(
                    R.string.omnipod_common_time_ago,
                    resourceHelper.gq(R.plurals.omnipod_common_days, days, days)
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
                R.string.key_pump_unreachable_threshold_minutes,
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
                    displayOkDialog(resourceHelper.gs(R.string.omnipod_common_confirmation), messageOnSuccess)
                }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(
                    resourceHelper.gs(R.string.omnipod_common_warning),
                    resourceHelper.gs(
                        R.string.omnipod_common_two_strings_concatenated_by_colon,
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
