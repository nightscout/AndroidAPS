package info.nightscout.androidaps.plugins.pump.omnipod.dialogs

import android.content.Intent
import android.os.Bundle
import com.atech.android.library.wizardpager.WizardPagerActivity
import com.atech.android.library.wizardpager.WizardPagerContext
import com.atech.android.library.wizardpager.data.WizardPagerSettings
import com.atech.android.library.wizardpager.defs.WizardStepsWayType
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashActivity
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodCancelAction
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.removepod.RemovePodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil
import info.nightscout.androidaps.utils.OKDialog
import kotlinx.android.synthetic.main.omnipod_pod_mgmt.*

/**
 * Created by andy on 30/08/2019
 */
class PodManagementActivity : NoSplashActivity() {

    private var initPodChanged = false
    private var podSessionFullyInitalized = false

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
            RxBus.send(EventOmnipodPumpValuesChanged())
            RxBus.send(EventRefreshOverview("Omnipod Pod Management"))
        }
    }


    fun initPodAction() {

        val pagerSettings = WizardPagerSettings()
        var refreshAction = InitPodRefreshAction(this)

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.cancelAction = refreshAction
        pagerSettings.finishAction = refreshAction

        val wizardPagerContext = WizardPagerContext.getInstance()

        wizardPagerContext.clearContext()
        wizardPagerContext.pagerSettings = pagerSettings
        wizardPagerContext.wizardModel = InitPodWizardModel(applicationContext, OmnipodUtil.getPodSessionState() == null)

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)
    }

    fun removePodAction() {
        val pagerSettings = WizardPagerSettings()
        var refreshAction = InitPodRefreshAction(this)

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.cancelAction = refreshAction
        pagerSettings.finishAction = refreshAction

        val wizardPagerContext = WizardPagerContext.getInstance();

        wizardPagerContext.clearContext()
        wizardPagerContext.pagerSettings = pagerSettings
        wizardPagerContext.wizardModel = RemovePodWizardModel(applicationContext)

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)

    }

    fun resetPodAction() {
        OKDialog.showConfirmation(this,
                MainApp.gs(R.string.omnipod_cmd_reset_pod_desc), Thread {
            AapsOmnipodManager.getInstance().resetPodStatus()
            refreshButtons()
        })
    }

    fun showPodHistory() {
        OKDialog.showConfirmation(this,
                MainApp.gs(R.string.omnipod_cmd_pod_history_na), null)
    }


    fun refreshButtons() {
        initpod_init_pod.isEnabled = (OmnipodUtil.getPodSessionState() == null ||
                OmnipodUtil.getPodSessionState().getSetupProgress().isBefore(SetupProgress.COMPLETED))

        val isPodSessionActive = (OmnipodUtil.getPodSessionState()!=null)

        initpod_remove_pod.isEnabled = isPodSessionActive
        initpod_reset_pod.isEnabled = isPodSessionActive
    }

}