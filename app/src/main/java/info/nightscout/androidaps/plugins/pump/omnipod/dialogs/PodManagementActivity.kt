package info.nightscout.androidaps.plugins.pump.omnipod.dialogs

import android.os.Bundle
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashActivity
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
        OKDialog.showConfirmation(this,
                MainApp.gs(R.string.omnipod_cmd_init_pod_na), null)
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