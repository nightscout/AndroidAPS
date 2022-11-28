package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandPlayTestBeep
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.databinding.OmnipodDashPodManagementBinding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.DashPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.DashPodDeactivationWizardActivity
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
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

class DashPodManagementActivity : DaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var context: Context
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var podStateManager: OmnipodDashPodStateManager
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus

    private var disposables: CompositeDisposable = CompositeDisposable()

    private lateinit var binding: OmnipodDashPodManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = OmnipodDashPodManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonActivatePod.setOnClickListener {
            val type: PodActivationWizardActivity.Type =
                if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PRIME_COMPLETED)) {
                    PodActivationWizardActivity.Type.SHORT
                } else {
                    PodActivationWizardActivity.Type.LONG
                }

            val intent = Intent(this, DashPodActivationWizardActivity::class.java)
            intent.putExtra(PodActivationWizardActivity.KEY_TYPE, type)
            startActivity(intent)
        }

        binding.buttonDeactivatePod.setOnClickListener {
            startActivity(Intent(this, DashPodDeactivationWizardActivity::class.java))
        }

        binding.buttonDiscardPod.setOnClickListener {
            OKDialog.showConfirmation(
                this,
                rh.gs(R.string.omnipod_common_pod_management_discard_pod_confirmation),
                Thread {
                    podStateManager.reset()
                }
            )
        }

        binding.buttonPlayTestBeep.setOnClickListener {
            binding.buttonPlayTestBeep.isEnabled = false
            binding.buttonPlayTestBeep.setText(R.string.omnipod_common_pod_management_button_playing_test_beep)

            commandQueue.customCommand(
                CommandPlayTestBeep(),
                object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            displayErrorDialog(
                                rh.gs(R.string.omnipod_common_warning),
                                rh.gs(
                                    R.string.omnipod_common_two_strings_concatenated_by_colon,
                                    rh.gs(R.string.omnipod_common_error_failed_to_play_test_beep),
                                    result.comment
                                ),
                                false
                            )
                        }
                    }
                }
            )
        }

        binding.buttonPodHistory.setOnClickListener {
            startActivity(Intent(this, DashPodHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
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
        // Only show the discard button to reset a cached unique ID before the unique ID has actually been set
        // Otherwise, users should use the Deactivate Pod Wizard. In case proper deactivation fails,
        // they will get an option to discard the Pod there
        val discardButtonEnabled =
            podStateManager.uniqueId != null &&
                podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID)
        binding.buttonDiscardPod.visibility = discardButtonEnabled.toVisibility()

        binding.buttonActivatePod.isEnabled = podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)
        binding.buttonDeactivatePod.isEnabled = podStateManager.bluetoothAddress != null || podStateManager.ltk != null

        if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED)) {
            if (commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java)) {
                binding.buttonPlayTestBeep.isEnabled = false
                binding.buttonPlayTestBeep.setText(R.string.omnipod_common_pod_management_button_playing_test_beep)
            } else {
                binding.buttonPlayTestBeep.isEnabled = true
                binding.buttonPlayTestBeep.setText(R.string.omnipod_common_pod_management_button_play_test_beep)
            }
        } else {
            binding.buttonPlayTestBeep.isEnabled = false
            binding.buttonPlayTestBeep.setText(R.string.omnipod_common_pod_management_button_play_test_beep)
        }

        if (discardButtonEnabled) {
            binding.buttonDiscardPod.isEnabled = true
        }
    }

    private fun displayErrorDialog(title: String, message: String, @Suppress("SameParameterValue") withSound: Boolean) {
        uiInteraction.runAlarm(message, title, if (withSound) R.raw.boluserror else 0)
    }
}
