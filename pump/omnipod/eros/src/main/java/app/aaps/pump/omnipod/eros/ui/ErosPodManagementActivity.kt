package app.aaps.pump.omnipod.eros.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.databinding.OmnipodErosPodManagementBinding
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.queue.command.CommandReadPulseLog
import app.aaps.pump.omnipod.eros.ui.wizard.activation.ErosPodActivationWizardActivity
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.ErosPodDeactivationWizardActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Provider

/**
 * Created by andy on 30/08/2019
 */
class ErosPodManagementActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var podStateManager: ErosPodStateManager
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
    @Inject lateinit var resetRileyLinkConfigurationTaskProvider: Provider<ResetRileyLinkConfigurationTask>

    private var disposables: CompositeDisposable = CompositeDisposable()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private lateinit var binding: OmnipodErosPodManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = OmnipodErosPodManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_title)
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
            OKDialog.showConfirmation(
                this,
                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_discard_pod_confirmation), Thread {
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
            handler.post { serviceTaskExecutor.startTask(resetRileyLinkConfigurationTaskProvider.get()) }
        }

        binding.buttonPlayTestBeep.setOnClickListener {
            binding.buttonPlayTestBeep.isEnabled = false
            binding.buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)

            commandQueue.customCommand(CommandPlayTestBeep(), object : Callback() {
                override fun run() {
                    if (!result.success) {
                        displayErrorDialog(
                            rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                            rh.gs(
                                app.aaps.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon,
                                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_play_test_beep),
                                result.comment
                            ),
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
                            rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                            rh.gs(
                                app.aaps.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon,
                                rh.gs(R.string.omnipod_eros_error_failed_to_read_pulse_log),
                                result.comment
                            ),
                            false
                        )
                    }
                }
            })
        }

        binding.buttonPodHistory.setOnClickListener {
            startActivity(Intent(this, ErosPodHistoryActivity::class.java))
        }
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
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
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
        binding.waitingForRlLayout.visibility = (!rileyLinkServiceData.rileyLinkServiceState.isReady()).toVisibility()

        if (rileyLinkServiceData.rileyLinkServiceState.isReady()) {
            binding.buttonActivatePod.isEnabled = !podStateManager.isPodActivationCompleted
            binding.buttonDeactivatePod.isEnabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

            if (podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
                if (commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java)) {
                    binding.buttonPlayTestBeep.isEnabled = false
                    binding.buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)
                } else {
                    binding.buttonPlayTestBeep.isEnabled = true
                    binding.buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
                }
            } else {
                binding.buttonPlayTestBeep.isEnabled = false
                binding.buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
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
            binding.buttonPlayTestBeep.setText(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
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
        uiInteraction.runAlarm(message, title, if (withSound) app.aaps.core.ui.R.raw.boluserror else 0)
    }

    private fun displayNotConfiguredDialog() {
        context.let {
            app.aaps.core.ui.UIRunnable {
                OKDialog.show(
                    it, rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                    rh.gs(R.string.omnipod_eros_error_operation_not_possible_no_configuration)
                )
            }.run()
        }
    }
}
