package info.nightscout.androidaps.plugins.pump.omnipod.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepConfigType
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandPlayTestBeep
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandReadPulseLog
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.PodDeactivationWizardActivity
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnipod_pod_management.*
import javax.inject.Inject

/**
 * Created by andy on 30/08/2019
 */
class PodManagementActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var aapsOmnipodManager: AapsOmnipodManager
    @Inject lateinit var context: Context
    @Inject lateinit var omnipodPumpPlugin: OmnipodPumpPlugin
    @Inject lateinit var serviceTaskExecutor: ServiceTaskExecutor

    private var disposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_pod_management)

        omnipod_pod_management_button_activate_pod.setOnClickListener {
            startActivity(Intent(this, PodActivationWizardActivity::class.java))
        }

        omnipod_pod_management_button_deactivate_pod.setOnClickListener {
            startActivity(Intent(this, PodDeactivationWizardActivity::class.java))
        }

        omnipod_pod_management_button_discard_pod.setOnClickListener {
            OKDialog.showConfirmation(this,
                resourceHelper.gs(R.string.omnipod_pod_management_discard_pod_confirmation), Thread {
                aapsOmnipodManager.discardPodState()
            })
        }

        omnipod_pod_management_button_rileylink_stats.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        omnipod_pod_management_button_reset_rileylink_config.setOnClickListener {
            // TODO improvement: properly disable button until task is finished
            serviceTaskExecutor.startTask(ResetRileyLinkConfigurationTask(injector))
        }

        omnipod_pod_management_button_play_test_beep.setOnClickListener {
            omnipod_pod_management_button_play_test_beep.isEnabled = false
            omnipod_pod_management_button_play_test_beep.setText(R.string.omnipod_pod_management_button_playing_test_beep)

            commandQueue.customCommand(CommandPlayTestBeep(BeepConfigType.BEEEP), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(resourceHelper.gs(R.string.omnipod_warning), resourceHelper.gs(R.string.omnipod_two_strings_concatenated_by_colon, resourceHelper.gs(R.string.omnipod_error_failed_to_play_test_beep), result.comment), false)
                    }
                }
            })
        }

        omnipod_pod_management_button_pulse_log.setOnClickListener {
            omnipod_pod_management_button_pulse_log.isEnabled = false
            omnipod_pod_management_button_pulse_log.setText(R.string.omnipod_pod_management_button_reading_pulse_log)

            commandQueue.customCommand(CommandReadPulseLog(), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(resourceHelper.gs(R.string.omnipod_warning), resourceHelper.gs(R.string.omnipod_two_strings_concatenated_by_colon, resourceHelper.gs(R.string.omnipod_error_failed_to_read_pulse_log), result.comment), false)
                    }
                }
            })
        }

        omnipod_pod_management_button_pod_history.setOnClickListener {
            startActivity(Intent(this, PodHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ refreshButtons() }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventOmnipodPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ refreshButtons() }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ refreshButtons() }, { fabricPrivacy.logException(it) })

        refreshButtons()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    private fun refreshButtons() {
        // Only show the discard button to reset a cached Pod address before the Pod has actually been initialized
        // Otherwise, users should use the Deactivate Pod Wizard. In case proper deactivation fails,
        // they will get an option to discard the Pod state there
        val discardButtonEnabled = podStateManager.hasPodState() && !podStateManager.isPodInitialized
        omnipod_pod_management_button_discard_pod.visibility = discardButtonEnabled.toVisibility()

        val pulseLogButtonEnabled = aapsOmnipodManager.isPulseLogButtonEnabled
        omnipod_pod_management_button_pulse_log.visibility = pulseLogButtonEnabled.toVisibility()

        omnipod_pod_management_button_rileylink_stats.visibility = aapsOmnipodManager.isRileylinkStatsButtonEnabled.toVisibility()
        omnipod_pod_management_waiting_for_rl_layout.visibility = (!rileyLinkServiceData.rileyLinkServiceState.isReady).toVisibility()

        if (rileyLinkServiceData.rileyLinkServiceState.isReady) {
            omnipod_pod_management_button_activate_pod.isEnabled = !podStateManager.isPodActivationCompleted
            omnipod_pod_management_button_deactivate_pod.isEnabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

            if (podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
                if (commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java)) {
                    omnipod_pod_management_button_play_test_beep.isEnabled = false
                    omnipod_pod_management_button_play_test_beep.setText(R.string.omnipod_pod_management_button_playing_test_beep)
                } else {
                    omnipod_pod_management_button_play_test_beep.isEnabled = true
                    omnipod_pod_management_button_play_test_beep.setText(R.string.omnipod_pod_management_button_play_test_beep)
                }
            } else {
                omnipod_pod_management_button_play_test_beep.isEnabled = false
                omnipod_pod_management_button_play_test_beep.setText(R.string.omnipod_pod_management_button_play_test_beep)
            }

            if (discardButtonEnabled) {
                omnipod_pod_management_button_discard_pod.isEnabled = true
            }
            if (pulseLogButtonEnabled) {
                if (podStateManager.isPodActivationCompleted) {
                    if (commandQueue.isCustomCommandInQueue(CommandReadPulseLog::class.java)) {
                        omnipod_pod_management_button_pulse_log.isEnabled = false
                        omnipod_pod_management_button_pulse_log.setText(R.string.omnipod_pod_management_button_reading_pulse_log)
                    } else {
                        omnipod_pod_management_button_pulse_log.isEnabled = true
                        omnipod_pod_management_button_pulse_log.setText(R.string.omnipod_pod_management_button_read_pulse_log)
                    }
                } else {
                    omnipod_pod_management_button_pulse_log.isEnabled = false
                    omnipod_pod_management_button_pulse_log.setText(R.string.omnipod_pod_management_button_read_pulse_log)
                }
            }
        } else {
            omnipod_pod_management_button_play_test_beep.setText(R.string.omnipod_pod_management_button_play_test_beep)
            omnipod_pod_management_button_activate_pod.isEnabled = false
            omnipod_pod_management_button_deactivate_pod.isEnabled = false
            omnipod_pod_management_button_play_test_beep.isEnabled = false

            if (discardButtonEnabled) {
                omnipod_pod_management_button_discard_pod.isEnabled = false
            }
            if (pulseLogButtonEnabled) {
                omnipod_pod_management_button_pulse_log.isEnabled = false
                omnipod_pod_management_button_pulse_log.setText(R.string.omnipod_pod_management_button_read_pulse_log)
            }
        }
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        context.let {
            val i = Intent(it, ErrorHelperActivity::class.java)
            i.putExtra("soundid", if (withSound) R.raw.boluserror else 0)
            i.putExtra("status", message)
            i.putExtra("title", title)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.startActivity(i)
        }
    }

    private fun displayNotConfiguredDialog() {
        context.let {
            UIRunnable(Runnable {
                OKDialog.show(it, resourceHelper.gs(R.string.omnipod_warning),
                    resourceHelper.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null)
            }).run()
        }
    }
}
