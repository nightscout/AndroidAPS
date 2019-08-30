package info.nightscout.androidaps.plugins.pump.omnipod.dialogs

import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashActivity
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

    }


    fun initPodAction() {

    }

    fun removePodAction() {

    }

    fun resetPodAction() {

    }

}