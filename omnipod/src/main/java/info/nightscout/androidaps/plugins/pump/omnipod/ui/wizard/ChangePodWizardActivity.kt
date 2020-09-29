package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard

import android.app.AlertDialog
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import javax.inject.Inject

class ChangePodWizardActivity : NoSplashAppCompatActivity() {
    companion object {
        const val KEY_START_DESTINATION = "startDestination"
    }

    @Inject
    lateinit var podStateManager: PodStateManager

    @IdRes
    private var startDestination: Int = R.id.deactivatePodInfoFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_change_pod_wizard_activity)

        startDestination = savedInstanceState?.getInt(KEY_START_DESTINATION, R.id.deactivatePodInfoFragment)
            ?: if (!podStateManager.isPodActivationCompleted) {
                if (!podStateManager.isPodInitialized || podStateManager.podProgressStatus.isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
                    R.id.fillPodInfoFragment
                } else {
                    R.id.attachPodInfoFragment
                }
            } else {
                R.id.deactivatePodInfoFragment
            }

        setStartDestination(startDestination)
    }

    fun setStartDestination(@IdRes startDestination: Int) {
        this.startDestination = startDestination
        val navController = getNavController()
        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.omnipod_change_pod_wizard_navigation_graph)
        graph.startDestination = startDestination
        navController.graph = graph
    }

    override fun onBackPressed() {
        exitActivityAfterConfirmation()
    }

    fun exitActivityAfterConfirmation() {
        if (getNavController().previousBackStackEntry == null) {
            finish()
        } else {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.omnipod_change_pod_wizard_change_pod))
                .setMessage(getString(R.string.omnipod_change_pod_wizard_exit_confirmation))
                .setPositiveButton(getString(R.string.omnipod_yes)) { _, _ -> finish() }
                .setNegativeButton(getString(R.string.omnipod_no), null)
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_START_DESTINATION, startDestination)
    }

    private fun getNavController(): NavController =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

}