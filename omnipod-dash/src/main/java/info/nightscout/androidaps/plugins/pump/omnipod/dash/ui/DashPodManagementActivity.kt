package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandPlayTestBeep
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.databinding.OmnipodDashPodManagementBinding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.DashPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.DashPodDeactivationWizardActivity
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

/**
 * Created by andy on 30/08/2019
 */
class DashPodManagementActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var context: Context
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var podStateManager: OmnipodDashPodStateManager

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
                resourceHelper.gs(R.string.omnipod_common_pod_management_discard_pod_confirmation),
                Thread {
                    // TODO discard Pod
                }
            )
        }

        binding.buttonPlayTestBeep.setOnClickListener {
            // TODO
            binding.buttonPlayTestBeep.isEnabled = false
            binding.buttonPlayTestBeep.setText(R.string.omnipod_common_pod_management_button_playing_test_beep)

            commandQueue.customCommand(
                CommandPlayTestBeep(),
                object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            displayErrorDialog(resourceHelper.gs(R.string.omnipod_common_warning), resourceHelper.gs(R.string.omnipod_common_two_strings_concatenated_by_colon, resourceHelper.gs(R.string.omnipod_common_error_failed_to_play_test_beep), result.comment), false)
                        }
                    }
                }
            )
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
        // TODO update button state from Pod state
    }

    private fun displayErrorDialog(title: String, message: String, @Suppress("SameParameterValue") withSound: Boolean) {
        context.let {
            ErrorHelperActivity.runAlarm(it, message, title, if (withSound) R.raw.boluserror else 0)
        }
    }
}
