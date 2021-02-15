package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui

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
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.databinding.OmnipodPodManagementBinding
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.BeepConfigType
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command.CommandPlayTestBeep
import info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command.CommandReadPulseLog
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.PodDeactivationWizardActivity
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
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
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var serviceTaskExecutor: ServiceTaskExecutor
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var disposables: CompositeDisposable = CompositeDisposable()

    private lateinit var binding: OmnipodPodManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OmnipodPodManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonActivatePod.setOnClickListener {
            startActivity(Intent(this, PodActivationWizardActivity::class.java))
        }

        binding.buttonDeactivatePod.setOnClickListener {
            startActivity(Intent(this, PodDeactivationWizardActivity::class.java))
        }

        binding.buttonDiscardPod.setOnClickListener {
            OKDialog.showConfirmation(this,
                resourceHelper.gs(R.string.omnipod_pod_management_discard_pod_confirmation), Thread {
                aapsOmnipodManager.discardPodState()
            })
        }

        binding.buttonRileylinkStats.setOnClickListener {
            if (omnipodErosPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        binding.buttonResetRileylinkConfig.setOnClickListener {
            // TODO improvement: properly disable button until task is finished
            serviceTaskExecutor.startTask(ResetRileyLinkConfigurationTask(injector))
        }

        binding.buttonPlayTestBeep.setOnClickListener {
            binding.buttonPlayTestBeep.isEnabled = false
            binding.buttonPlayTestBeep.setText(R.string.omnipod_pod_management_button_playing_test_beep)

            commandQueue.customCommand(CommandPlayTestBeep(BeepConfigType.BEEEP), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(resourceHelper.gs(R.string.omnipod_warning), resourceHelper.gs(R.string.omnipod_two_strings_concatenated_by_colon, resourceHelper.gs(R.string.omnipod_error_failed_to_play_test_beep), result.comment), false)
                    }
                }
            })
        }

        binding.buttonPulseLog.setOnClickListener {
            binding.buttonPulseLog.isEnabled = false
            binding.buttonPulseLog.setText(R.string.omnipod_pod_management_button_reading_pulse_log)

            commandQueue.customCommand(CommandReadPulseLog(), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(resourceHelper.gs(R.string.omnipod_warning), resourceHelper.gs(R.string.omnipod_two_strings_concatenated_by_colon, resourceHelper.gs(R.string.omnipod_error_failed_to_read_pulse_log), result.comment), false)
                    }
                }
            })
        }

        binding.buttonPodHistory.setOnClickListener {
            startActivity(Intent(this, PodHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshButtons() }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventOmnipodPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshButtons() }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshButtons() }, fabricPrivacy::logException)

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
        binding.buttonDiscardPod.visibility = discardButtonEnabled.toVisibility()

        val pulseLogButtonEnabled = aapsOmnipodManager.isPulseLogButtonEnabled
        binding.buttonPulseLog.visibility = pulseLogButtonEnabled.toVisibility()

        binding.buttonRileylinkStats.visibility = aapsOmnipodManager.isRileylinkStatsButtonEnabled.toVisibility()
        binding.waitingForRlLayout.visibility = (!rileyLinkServiceData.rileyLinkServiceState.isReady).toVisibility()

        if (rileyLinkServiceData.rileyLinkServiceState.isReady) {
            binding.buttonActivatePod.isEnabled = !podStateManager.isPodActivationCompleted
            binding.buttonDeactivatePod.isEnabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

            if (podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
                if (commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java)) {
                    binding.buttonPlayTestBeep.isEnabled = false
                    binding.buttonPlayTestBeep.setText(R.string.omnipod_pod_management_button_playing_test_beep)
                } else {
                    binding.buttonPlayTestBeep.isEnabled = true
                    binding.buttonPlayTestBeep.setText(R.string.omnipod_pod_management_button_play_test_beep)
                }
            } else {
                binding.buttonPlayTestBeep.isEnabled = false
                binding.buttonPlayTestBeep.setText(R.string.omnipod_pod_management_button_play_test_beep)
            }

            if (discardButtonEnabled) {
                binding.buttonDiscardPod.isEnabled = true
            }
            if (pulseLogButtonEnabled) {
                if (podStateManager.isPodActivationCompleted) {
                    if (commandQueue.isCustomCommandInQueue(CommandReadPulseLog::class.java)) {
                        binding.buttonPulseLog.isEnabled = false
                        binding.buttonPulseLog.setText(R.string.omnipod_pod_management_button_reading_pulse_log)
                    } else {
                        binding.buttonPulseLog.isEnabled = true
                        binding.buttonPulseLog.setText(R.string.omnipod_pod_management_button_read_pulse_log)
                    }
                } else {
                    binding.buttonPulseLog.isEnabled = false
                    binding.buttonPulseLog.setText(R.string.omnipod_pod_management_button_read_pulse_log)
                }
            }
        } else {
            binding.buttonPlayTestBeep.setText(R.string.omnipod_pod_management_button_play_test_beep)
            binding.buttonActivatePod.isEnabled = false
            binding.buttonDeactivatePod.isEnabled = false
            binding.buttonPlayTestBeep.isEnabled = false

            if (discardButtonEnabled) {
                binding.buttonDiscardPod.isEnabled = false
            }
            if (pulseLogButtonEnabled) {
                binding.buttonPulseLog.isEnabled = false
                binding.buttonPulseLog.setText(R.string.omnipod_pod_management_button_read_pulse_log)
            }
        }
    }

    private fun displayErrorDialog(title: String, message: String, @Suppress("SameParameterValue") withSound: Boolean) {
        context.let {
            ErrorHelperActivity.runAlarm(it, message, title, if (withSound) R.raw.boluserror else 0)
        }
    }

    private fun displayNotConfiguredDialog() {
        context.let {
            UIRunnable {
                OKDialog.show(it, resourceHelper.gs(R.string.omnipod_warning),
                    resourceHelper.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null)
            }.run()
        }
    }
}
