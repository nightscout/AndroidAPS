package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.activity

import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.plugins.pump.omnipod.R

abstract class OmnipodWizardActivityBase : NoSplashAppCompatActivity() {
    override fun onBackPressed() {
        exitActivityAfterConfirmation()
    }

    fun exitActivityAfterConfirmation() {
        if (getNavController().previousBackStackEntry == null) {
            finish()
        } else {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.omnipod_wizard_exit_confirmation_title))
                .setMessage(getString(R.string.omnipod_wizard_exit_confirmation_text))
                .setPositiveButton(getString(R.string.omnipod_yes)) { _, _ -> finish() }
                .setNegativeButton(getString(R.string.omnipod_no), null)
                .show()
        }
    }

    protected fun getNavController(): NavController =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    abstract fun getTotalDefinedNumberOfSteps(): Int

    abstract fun getActualNumberOfSteps(): Int
}