package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.activity

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity

abstract class OmnipodWizardActivityBase : TranslatedDaggerAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitActivityAfterConfirmation()
            }
        })
        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        })
    }

    fun exitActivityAfterConfirmation() {
        if (getNavController().previousBackStackEntry == null) {
            finish()
        } else {
            AlertDialog.Builder(this, info.nightscout.core.ui.R.style.DialogTheme)
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