package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation

import android.os.Bundle
import androidx.annotation.IdRes
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.activity.OmnipodWizardActivityBase
import javax.inject.Inject

class PodActivationWizardActivity : OmnipodWizardActivityBase() {
    companion object {
        const val KEY_START_DESTINATION = "startDestination"
    }

    @Inject
    lateinit var podStateManager: PodStateManager

    @IdRes
    private var startDestination: Int = R.id.fillPodInfoFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_pod_activation_wizard_activity)

        startDestination = savedInstanceState?.getInt(KEY_START_DESTINATION, R.id.fillPodInfoFragment)
            ?: if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIMING_COMPLETED)) {
                R.id.fillPodInfoFragment
            } else {
                R.id.attachPodInfoFragment
            }

        setStartDestination(startDestination)
    }

    private fun setStartDestination(@IdRes startDestination: Int) {
        this.startDestination = startDestination
        val navController = getNavController()
        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.omnipod_pod_activation_wizard_navigation_graph)
        graph.startDestination = startDestination
        navController.graph = graph
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_START_DESTINATION, startDestination)
    }

    override fun getTotalDefinedNumberOfSteps(): Int = 5

    override fun getActualNumberOfSteps(): Int {
        if (startDestination == R.id.attachPodInfoFragment) {
            return 3
        }
        return 5
    }

}