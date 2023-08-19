package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandPlayTestBeep
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.databinding.OmnipodErosPodManagementBinding
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command.CommandReadPulseLog
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.ErosPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.ErosPodDeactivationWizardActivity
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventQueueChanged
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

/**
 * Created by andy on 30/08/2019
 */
class ErosPodManagementActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var podStateManager: ErosPodStateManager
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var aapsOmnipodManager: AapsOmnipodErosManager
    @Inject lateinit var context: Context
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var serviceTaskExecutor: ServiceTaskExecutor
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus

    private var disposables: CompositeDisposable = CompositeDisposable()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private lateinit var binding: OmnipodErosPodManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = OmnipodErosPodManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.buttonActivatePod.setOnClickListener {
            val type: PodActivationWizardActivity.Type = if (podStateManager.isPodInitialized
                and podStateManager.activationProgress.isAtLeast(ActivationProgress.PRIMING_COMPLETED)
            ) {
                PodActivationWizardActivity.Type.SHORT
            } else {
                PodActivationWizardActivity.Type.LONG
            }

            val intent = Intent(this, ErosPodActivationWizardActivity::class.java)
            intent.putExtra(PodActivationWizardActivity.KEY_TYPE, type)
            startActivity(intent)
        }

        binding.buttonDeactivatePod.setOnClickListener {
            startActivity(Intent(this, ErosPodDeactivationWizardActivity::class.java))
        }

        binding.buttonDiscardPod.setOnClickListener {
            OKDialog.showConfirmation(this,
                                      rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_discard_pod_confirmation), Thread {
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
            handler.post { serviceTaskExecutor.startTask(ResetRileyLinkConfigurationTask(injector)) }
        }

        binding.buttonPlayTestBeep.setOnClickListener {
            binding.buttonPlayTestBeep.isEnabled = false
            binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)

            commandQueue.customCommand(CommandPlayTestBeep(), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(
                            rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_warning),
                            rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon, rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_play_test_beep), result.comment),
                            false
                        )
                    }
                }
            })
        }

        binding.buttonPulseLog.setOnClickListener {
            binding.buttonPulseLog.isEnabled = false
            binding.buttonPulseLog.setText(R.string.omnipod_eros_pod_management_button_reading_pulse_log)

            commandQueue.customCommand(CommandReadPulseLog(), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(
                            rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_warning),
                            rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon, rh.gs(R.string.omnipod_eros_error_failed_to_read_pulse_log), result.comment),
                            false
                        )
                    }
                }
            })
        }

        binding.buttonPodHistory.setOnClickListener {
            startActivity(Intent(this, ErosPodHistoryActivity::class.java))
        }
        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        })
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshButtons() }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventOmnipodErosPumpValuesChanged::class.java)
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
        // Milos Kozak: allow to show button by activating engineering mode
        val discardButtonEnabled = podStateManager.hasPodState() && (!podStateManager.isPodInitialized || config.isEngineeringMode())
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
                    binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)
                } else {
                    binding.buttonPlayTestBeep.isEnabled = true
                    binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
                }
            } else {
                binding.buttonPlayTestBeep.isEnabled = false
                binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
            }

            if (discardButtonEnabled) {
                binding.buttonDiscardPod.isEnabled = true
            }
            if (pulseLogButtonEnabled) {
                if (podStateManager.isPodActivationCompleted) {
                    if (commandQueue.isCustomCommandInQueue(CommandReadPulseLog::class.java)) {
                        binding.buttonPulseLog.isEnabled = false
                        binding.buttonPulseLog.setText(R.string.omnipod_eros_pod_management_button_reading_pulse_log)
                    } else {
                        binding.buttonPulseLog.isEnabled = true
                        binding.buttonPulseLog.setText(R.string.omnipod_eros_pod_management_button_read_pulse_log)
                    }
                } else {
                    binding.buttonPulseLog.isEnabled = false
                    binding.buttonPulseLog.setText(R.string.omnipod_eros_pod_management_button_read_pulse_log)
                }
            }
        } else {
            binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
            binding.buttonActivatePod.isEnabled = false
            binding.buttonDeactivatePod.isEnabled = false
            binding.buttonPlayTestBeep.isEnabled = false

            if (discardButtonEnabled) {
                binding.buttonDiscardPod.isEnabled = false
            }
            if (pulseLogButtonEnabled) {
                binding.buttonPulseLog.isEnabled = false
                binding.buttonPulseLog.setText(R.string.omnipod_eros_pod_management_button_read_pulse_log)
            }
        }
    }

    private fun displayErrorDialog(title: String, message: String, @Suppress("SameParameterValue") withSound: Boolean) {
        uiInteraction.runAlarm(message, title, if (withSound) info.nightscout.core.ui.R.raw.boluserror else 0)
    }

    private fun displayNotConfiguredDialog() {
        context.let {
            info.nightscout.core.ui.UIRunnable {
                OKDialog.show(
                    it, rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_warning),
                    rh.gs(R.string.omnipod_eros_error_operation_not_possible_no_configuration), null
                )
            }.run()
        }
    }
}
