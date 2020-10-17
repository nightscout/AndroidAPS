package info.nightscout.androidaps.plugins.pump.omnipod.ui

import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.PodDeactivationWizardActivity
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnipod_pod_management.*
import javax.inject.Inject

/**
 * Created by andy on 30/08/2019
 */
class PodManagementActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var aapsOmnipodManager: AapsOmnipodManager

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
                resourceHelper.gs(R.string.omnipod_pod_management_discard_pod_state_confirmation), Thread {
                aapsOmnipodManager.discardPodState()
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
        // TODO maybe rename this button and the confirmation dialog text (see onCreate)
        val discardButtonEnabled = podStateManager.hasPodState() && !podStateManager.isPodInitialized
        omnipod_pod_management_button_discard_pod.visibility = discardButtonEnabled.toVisibility()
        omnipod_pod_management_waiting_for_rl_layout.visibility = (!rileyLinkServiceData.rileyLinkServiceState.isReady).toVisibility()

        if (rileyLinkServiceData.rileyLinkServiceState.isReady) {
            omnipod_pod_management_button_activate_pod.isEnabled = !podStateManager.isPodActivationCompleted
            omnipod_pod_management_button_deactivate_pod.isEnabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)
            if (discardButtonEnabled) {
                omnipod_pod_management_button_discard_pod.isEnabled = true
            }
        } else {
            omnipod_pod_management_button_activate_pod.isEnabled = false
            omnipod_pod_management_button_deactivate_pod.isEnabled = false
            if (discardButtonEnabled) {
                omnipod_pod_management_button_discard_pod.isEnabled = false
            }
        }
    }

}
