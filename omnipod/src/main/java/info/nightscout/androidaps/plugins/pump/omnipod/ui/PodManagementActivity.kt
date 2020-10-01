package info.nightscout.androidaps.plugins.pump.omnipod.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.ChangePodWizardActivity
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
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

        omnipod_pod_management_button_change_pod.setOnClickListener {
            val myIntent = Intent(this@PodManagementActivity, ChangePodWizardActivity::class.java)
            this@PodManagementActivity.startActivity(myIntent)
        }

        omnipod_pod_management_button_discard_pod.setOnClickListener {
            discardPodAction()
        }

        omnipod_pod_management_button_pod_history.setOnClickListener {
            showPodHistory()
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

    private fun discardPodAction() {
        OKDialog.showConfirmation(this,
            resourceHelper.gs(R.string.omnipod_pod_management_discard_pod_state_confirmation), Thread {
            aapsOmnipodManager.discardPodState()
        })
    }

    private fun showPodHistory() {
        startActivity(Intent(applicationContext, PodHistoryActivity::class.java))
    }

    private fun refreshButtons() {
        omnipod_pod_management_button_change_pod.isEnabled = true
        omnipod_pod_management_button_discard_pod.isEnabled = podStateManager.hasPodState()

        val waitingForRlLayout = findViewById<LinearLayout>(R.id.omnipod_pod_management_waiting_for_rl_layout)

        if (rileyLinkServiceData.rileyLinkServiceState.isReady) {
            waitingForRlLayout.visibility = View.GONE
        } else {
            waitingForRlLayout.visibility = View.VISIBLE
            omnipod_pod_management_button_change_pod.isEnabled = false
            omnipod_pod_management_button_discard_pod.isEnabled = false
        }
    }

}
