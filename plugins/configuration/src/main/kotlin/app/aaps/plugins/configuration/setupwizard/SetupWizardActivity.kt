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
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper.update
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.databinding.ActivitySetupwizardBinding
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.max
import kotlin.math.min

class SetupWizardActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var swDefinition: SWDefinition
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var swItemProvider: Provider<SWItem>

    private val disposable = CompositeDisposable()
    private lateinit var screens: List<SWScreen>
    private var currentWizardPage = 0
    private var setupWizardMenuProvider: MenuProvider? = null

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
        currentWizardPage = intent.getIntExtra(intentMessage, 0)
        prepareLayout()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentWizardPage == 0)
                    OKDialog.showConfirmation(this@SetupWizardActivity, rh.gs(R.string.exitwizard)) { finish() } else {
                    currentWizardPage = previousPage(); prepareLayout()
                }
            }
        })
        setupWizardMenuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        preferences.put(BooleanKey.GeneralSetupWizardProcessed, true)
                        OKDialog.showConfirmation(this@SetupWizardActivity, rh.gs(R.string.exitwizard)) { finish() }
                        true
                    }

                    else              -> false
                }
        }
        setupWizardMenuProvider?.let { addMenuProvider(it) }
        binding.nextButton.setOnClickListener { currentWizardPage = nextPage(); prepareLayout() }
        binding.previousButton.setOnClickListener { currentWizardPage = previousPage(); prepareLayout() }
        binding.finishButton.setOnClickListener { finishSetupWizard() }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.nextButton.setOnClickListener(null)
        binding.previousButton.setOnClickListener(null)
        binding.finishButton.setOnClickListener(null)
        setupWizardMenuProvider?.let { removeMenuProvider(it) }
        swDefinition.activity = null
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

    fun prepareLayout() {
        if (screens.isNotEmpty() && currentWizardPage < screens.size) {
            val currentScreen = screens[currentWizardPage]

            //Set screen name
            val screenName = findViewById<TextView>(R.id.sw_content)
            screenName.text = currentScreen.getHeader()
            //Generate layout first
            generateLayout()
            updateButtons()
        }
    }

    // Go back to overview
    fun finishSetupWizard() {
        preferences.put(BooleanKey.GeneralSetupWizardProcessed, true)
        val intent = Intent(this, uiInteraction.mainActivity).setAction("SetupWizardActivity")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun generateLayout() {
        val currentScreen = screens[currentWizardPage]
        val layout = swItemProvider.get().generateLayout(findViewById(R.id.sw_content_fields))
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
                if (currentWizardPage == nextPage()) {
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
            currentScreen.processVisibility(this)
        }
    }

    private fun nextPage(): Int {
        var page = currentWizardPage + 1
        while (page < screens.size) {
            if (screens[page].visibility == null || screens[page].visibility?.invoke() == true) return page
            page++
        }
        return min(currentWizardPage, screens.size - 1)
    }

    private fun previousPage(): Int {
        var page = currentWizardPage - 1
        while (page >= 0) {
            if (screens[page].visibility == null || screens[page].visibility?.invoke() == true) return page
            page--
        }
        return max(currentWizardPage, 0)
    }
}