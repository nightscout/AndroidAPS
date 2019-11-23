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
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodCancelAction
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodWizardModel
import info.nightscout.androidaps.utils.OKDialog
import kotlinx.android.synthetic.main.omnipod_pod_mgmt.*

class PodManagementActivity : NoSplashActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_pod_mgmt)

        initpod_init_pod.setOnClickListener {
            initPodAction()
        }

        initpod_remove_pod.setOnClickListener {
            removePodAction()
        }

        initpod_reset_pod.setOnClickListener {
            resetPodAction()
        }


        initpod_pod_history.setOnClickListener {
            showPodHistory()
        }

    }


    fun initPodAction() {

        // TODO check if RL is running

        val pagerSettings = WizardPagerSettings()

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.setCancelAction(InitPodCancelAction())
        pagerSettings.setTheme(R.style.AppTheme_NoActionBar)


        WizardPagerContext.getInstance().pagerSettings = pagerSettings
        WizardPagerContext.getInstance().wizardModel = InitPodWizardModel(applicationContext)

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)





        //OKDialog.showConfirmation(this,
        //        MainApp.gs(R.string.omnipod_cmd_init_pod_na), null)
    }

    fun removePodAction() {
        OKDialog.showConfirmation(this,
                MainApp.gs(R.string.omnipod_cmd_deactivate_pod_na), null)

    }

    fun resetPodAction() {
        OKDialog.showConfirmation(this,
                MainApp.gs(R.string.omnipod_cmd_reset_pod_na), null)

    }

    fun showPodHistory() {
        OKDialog.showConfirmation(this,
                MainApp.gs(R.string.omnipod_cmd_pod_history_na), null)
    }

}