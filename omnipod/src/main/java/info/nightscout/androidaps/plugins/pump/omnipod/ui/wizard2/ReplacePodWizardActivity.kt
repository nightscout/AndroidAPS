package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2

import android.os.Bundle
import android.os.PersistableBundle
import androidx.navigation.fragment.NavHostFragment
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import javax.inject.Inject

class ReplacePodWizardActivity : NoSplashAppCompatActivity() {
    companion object {
        const val KEY_START_DESTINATION = "startDestination"
    }

    @Inject
    lateinit var podStateManager: PodStateManager

    var startDestination: Int = R.id.deactivatePodInfoFragment
        get() = field

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            setContentView(R.layout.omnipod_replace_pod_wizard_activity)

            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            val navInflater = navController.navInflater
            val graph = navInflater.inflate(R.navigation.omnipod_replace_pod_wizard_navigation_graph)

            if (!podStateManager.isPodActivationCompleted) {
                if (!podStateManager.isPodInitialized || podStateManager.podProgressStatus.isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
                    startDestination = R.id.fillPodInfoFragment
                } else {
                    startDestination = R.id.attachPodInfoFragment
                }
            }

            graph.startDestination = startDestination;
            navController.graph = graph
        } else {
            startDestination = savedInstanceState.getInt(KEY_START_DESTINATION, R.id.deactivatePodInfoFragment)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState?.putInt(KEY_START_DESTINATION, startDestination)
    }
}