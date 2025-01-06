package app.aaps.pump.omnipod.common.ui.wizard.common.activity

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.omnipod.common.R

abstract class OmnipodWizardActivityBase : TranslatedDaggerAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitActivityAfterConfirmation()
            }
        })
    }

    fun exitActivityAfterConfirmation() {
        if (getNavController().previousBackStackEntry == null) {
            finish()
        } else {
            AlertDialog.Builder(this, app.aaps.core.ui.R.style.DialogTheme)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.omnipod_common_wizard_exit_confirmation_title))
                .setMessage(getString(R.string.omnipod_common_wizard_exit_confirmation_text))
                .setPositiveButton(getString(R.string.omnipod_common_yes)) { _, _ -> finish() }
                .setNegativeButton(getString(R.string.omnipod_common_no), null)
                .show()
        }
    }

    protected fun getNavController(): NavController =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    abstract fun getTotalDefinedNumberOfSteps(): Int

    abstract fun getActualNumberOfSteps(): Int
}