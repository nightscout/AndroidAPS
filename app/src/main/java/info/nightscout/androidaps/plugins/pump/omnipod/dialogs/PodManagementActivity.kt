package info.nightscout.androidaps.plugins.pump.omnipod.dialogs

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentStatePagerAdapter
import com.atech.android.library.wizardpager.WizardPagerActivity
import com.atech.android.library.wizardpager.WizardPagerContext
import com.atech.android.library.wizardpager.data.WizardPagerSettings
import com.atech.android.library.wizardpager.defs.WizardStepsWayType
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.defs.PodActionType
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.model.FullInitPodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.model.RemovePodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.model.ShortInitPodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.omnipod_pod_mgmt.*
import javax.inject.Inject

/**
 * Created by andy on 30/08/2019
 */
class PodManagementActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var omnipodUtil: OmnipodUtil
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData

    private var initPodChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_pod_mgmt)

        initpod_init_pod.setOnClickListener {
            initPodAction()
            initPodChanged = true
        }

        initpod_remove_pod.setOnClickListener {
            removePodAction()
            initPodChanged = true
        }

        initpod_reset_pod.setOnClickListener {
            resetPodAction()
            initPodChanged = true
        }

        initpod_pod_history.setOnClickListener {
            showPodHistory()
        }

        refreshButtons();
    }

    override fun onDestroy() {
        super.onDestroy()

        if (initPodChanged) {
            rxBus.send(EventOmnipodPumpValuesChanged())
            rxBus.send(EventRefreshOverview("Omnipod Pod Management"))
        }
    }

    fun initPodAction() {

        val pagerSettings = WizardPagerSettings()
        var refreshAction = InitPodRefreshAction(injector, this, PodActionType.InitPod)

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.cancelAction = refreshAction
        pagerSettings.finishAction = refreshAction
        pagerSettings.pagerAdapterBehavior = FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT

        val wizardPagerContext = WizardPagerContext.getInstance()

        wizardPagerContext.clearContext()
        wizardPagerContext.pagerSettings = pagerSettings
        val isFullInit = !podStateManager.isPodInitialized || podStateManager.podProgressStatus.isBefore(PodProgressStatus.PRIMING_COMPLETED)
        if (isFullInit) {
            wizardPagerContext.wizardModel = FullInitPodWizardModel(applicationContext)
        } else {
            wizardPagerContext.wizardModel = ShortInitPodWizardModel(applicationContext)
        }

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)
    }

    fun removePodAction() {
        val pagerSettings = WizardPagerSettings()
        var refreshAction = InitPodRefreshAction(injector, this, PodActionType.RemovePod)

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.cancelAction = refreshAction
        pagerSettings.finishAction = refreshAction
        pagerSettings.pagerAdapterBehavior = FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT

        val wizardPagerContext = WizardPagerContext.getInstance();

        wizardPagerContext.clearContext()
        wizardPagerContext.pagerSettings = pagerSettings
        wizardPagerContext.wizardModel = RemovePodWizardModel(applicationContext)

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)

    }

    fun resetPodAction() {
        OKDialog.showConfirmation(this,
            resourceHelper.gs(R.string.omnipod_cmd_reset_pod_desc), Thread {
            AapsOmnipodManager.getInstance().resetPodStatus()
            omnipodUtil.setDriverState(OmnipodDriverState.Initalized_NoPod)
            refreshButtons()
        })
    }

    fun showPodHistory() {
//        OKDialog.showConfirmation(this,
//                MainApp.gs(R.string.omnipod_cmd_pod_history_na), null)

        startActivity(Intent(applicationContext, PodHistoryActivity::class.java))
    }

    fun refreshButtons() {
        initpod_init_pod.isEnabled = !podStateManager.isPodRunning()

        initpod_remove_pod.isEnabled = podStateManager.isPodInitialized
        initpod_reset_pod.isEnabled = podStateManager.hasPodState()

        if (!rileyLinkServiceData.rileyLinkServiceState.isReady) {
            // if rileylink is not running we disable all operations that require a RL connection
            initpod_init_pod.isEnabled = false
            initpod_remove_pod.isEnabled = false
            initpod_reset_pod.isEnabled = false
        }
    }

}
