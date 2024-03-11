package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.utils.extensions.safeGetSerializableExtra
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import javax.inject.Inject

class EquilPairActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin

    companion object {

        const val KEY_TYPE = "EquilType"
        const val KEY_START_DESTINATION = "startDestination"
    }

    enum class Type {
        PAIR,
        CHANGE_INSULIN
    }

    @IdRes
    private var startDestination: Int = R.id.startEquilActivationFragment
    var pair: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.equil_pair_activity)

        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        exitActivityAfterConfirmation()
                        true
                    }

                    else              -> false
                }
        })


        startDestination = savedInstanceState?.getInt(KEY_START_DESTINATION, R.id.startEquilActivationFragment)
            ?: if (intent.safeGetSerializableExtra(KEY_TYPE, Type::class.java) == null) {
                R.id.startEquilActivationFragment
            } else if (intent.safeGetSerializableExtra(KEY_TYPE, Type::class.java) == Type.PAIR) {
                R.id.startEquilActivationFragment
            } else {
                R.id.startEquilChangeInsulinFragment
            }
        pair = startDestination == R.id.startEquilActivationFragment
        setStartDestination(startDestination)
    }

    private fun setStartDestination(@IdRes startDestination: Int) {
        this.startDestination = startDestination
        val navController = getNavController()
        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.equil_pair_navigation_graph)
        graph.setStartDestination(startDestination)
        navController.graph = graph
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_START_DESTINATION, startDestination)
    }

    fun exitActivityAfterConfirmation() {
        if (getNavController().previousBackStackEntry == null) {
            finish()
        } else {
            AlertDialog.Builder(this, app.aaps.core.ui.R.style.DialogTheme)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.equil_common_wizard_exit_confirmation_title))
                .setMessage(getString(R.string.equil_common_wizard_exit_confirmation_text))
                .setPositiveButton(getString(app.aaps.core.ui.R.string.yes)) { _, _ -> finish() }
                .setNegativeButton(getString(app.aaps.core.ui.R.string.no), null)
                .show()
        }
    }

    private fun getNavController(): NavController =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    fun getTotalDefinedNumberOfSteps(): Int {
        return 6
    }

    fun getActualNumberOfSteps(): Int {
        return 6
    }
}