package app.aaps.plugins.configuration.setupwizard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper.update
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.databinding.ActivitySetupwizardBinding
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SetupWizardActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var swDefinition: SWDefinition
    @Inject lateinit var sp: SP
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction

    private val disposable = CompositeDisposable()
    private lateinit var screens: List<SWScreen>
    private var currentWizardPage = 0

    private val intentMessage = "WIZZARDPAGE"

    private lateinit var binding: ActivitySetupwizardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        update(applicationContext)
        binding = ActivitySetupwizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.nav_setupwizard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        swDefinition.activity = this
        screens = swDefinition.getScreens()
        val intent = intent
        currentWizardPage = intent.getIntExtra(intentMessage, 0)
        if (screens.isNotEmpty() && currentWizardPage < screens.size) {
            val currentScreen = screens[currentWizardPage]

            //Set screen name
            val screenName = findViewById<TextView>(R.id.sw_content)
            screenName.text = currentScreen.getHeader()
            //Generate layout first
            generateLayout()
            updateButtons()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentWizardPage == 0)
                    OKDialog.showConfirmation(this@SetupWizardActivity, rh.gs(R.string.exitwizard)) { finish() } else showPreviousPage(null)
            }
        })
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        sp.putBoolean(R.string.key_setupwizard_processed, true)
                        OKDialog.showConfirmation(this@SetupWizardActivity, rh.gs(R.string.exitwizard)) { finish() }
                        true
                    }

                    else              -> false
                }
        })
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        swDefinition.activity = this
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateButtons() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventSWRLStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateButtons() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventSWSyncStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateButtons() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateButtons() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventProfileStoreChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateButtons() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventSWUpdate::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event: EventSWUpdate ->
                           if (event.redraw) generateLayout()
                           updateButtons()
                       }, fabricPrivacy::logException)
        updateButtons()
    }

    private fun generateLayout() {
        val currentScreen = screens[currentWizardPage]
        val layout = SWItem(injector, SWItem.Type.NONE).generateLayout(findViewById(R.id.sw_content_fields))
        for (i in currentScreen.items.indices) {
            val currentItem = currentScreen.items[i]
            currentItem.generateDialog(layout)
        }
        binding.swScrollview.smoothScrollTo(0, 0)
    }

    override fun updateButtons() {
        runOnUiThread {
            val currentScreen = screens[currentWizardPage]
            if (currentScreen.validator == null || currentScreen.validator?.invoke() == true || currentScreen.skippable) {
                if (currentWizardPage == nextPage(null)) {
                    findViewById<View>(R.id.finish_button).visibility = View.VISIBLE
                    findViewById<View>(R.id.next_button).visibility = View.GONE
                } else {
                    findViewById<View>(R.id.finish_button).visibility = View.GONE
                    findViewById<View>(R.id.next_button).visibility = View.VISIBLE
                }
            } else {
                findViewById<View>(R.id.finish_button).visibility = View.GONE
                findViewById<View>(R.id.next_button).visibility = View.GONE
            }
            if (currentWizardPage == 0) findViewById<View>(R.id.previous_button).visibility = View.GONE else findViewById<View>(R.id.previous_button).visibility = View.VISIBLE
            currentScreen.processVisibility()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun showNextPage(view: View?) {
        finish()
        val intent = Intent(this, SetupWizardActivity::class.java).setAction("app.aaps.plugins.configuration.setupwizard.SetupWizardActivity")
        intent.putExtra(intentMessage, nextPage(null))
        startActivity(intent)
    }

    @Suppress("UNUSED_PARAMETER")
    fun showPreviousPage(view: View?) {
        finish()
        val intent = Intent(this, SetupWizardActivity::class.java).setAction("app.aaps.plugins.configuration.setupwizard.SetupWizardActivity")
        intent.putExtra(intentMessage, previousPage(null))
        startActivity(intent)
    }

    // Go back to overview
    @Suppress("UNUSED_PARAMETER")
    fun finishSetupWizard(view: View?) {
        sp.putBoolean(R.string.key_setupwizard_processed, true)
        val intent = Intent(this, uiInteraction.mainActivity).setAction("app.aaps.plugins.configuration.setupwizard.SetupWizardActivity")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    @Suppress("UNUSED_PARAMETER", "SameParameterValue")
    private fun nextPage(view: View?): Int {
        var page = currentWizardPage + 1
        while (page < screens.size) {
            if (screens[page].visibility == null || screens[page].visibility?.invoke() == true) return page
            page++
        }
        return min(currentWizardPage, screens.size - 1)
    }

    @Suppress("UNUSED_PARAMETER", "SameParameterValue")
    private fun previousPage(view: View?): Int {
        var page = currentWizardPage - 1
        while (page >= 0) {
            if (screens[page].visibility == null || screens[page].visibility?.invoke() == true) return page
            page--
        }
        return max(currentWizardPage, 0)
    }
}