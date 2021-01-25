package info.nightscout.androidaps.setupwizard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.events.EventProfileStoreChanged
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.setupwizard.elements.SWItem
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.locale.LocaleHelper.update
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_setupwizard.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SetupWizardActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var swDefinition: SWDefinition
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()
    private lateinit var screens: List<SWScreen>
    private var currentWizardPage = 0

    private val intentMessage = "WIZZARDPAGE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        update(applicationContext)
        setContentView(R.layout.activity_setupwizard)
        screens = swDefinition.getScreens()
        val intent = intent
        currentWizardPage = intent.getIntExtra(intentMessage, 0)
        if (screens.isNotEmpty() && currentWizardPage < screens.size) {
            val currentScreen = screens[currentWizardPage]

            //Set screen name
            val screenName = findViewById<TextView>(R.id.sw_content)
            screenName.text = currentScreen.getHeader()
            swDefinition.activity = this
            //Generate layout first
            generateLayout()
            updateButtons()
        }
    }

    public override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        swDefinition.activity = this
        disposable.add(rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateButtons() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateButtons() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventNSClientStatus::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateButtons() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventProfileNeedsUpdate::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateButtons() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventProfileStoreChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateButtons() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventSWUpdate::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ event: EventSWUpdate ->
                if (event.redraw) generateLayout()
                updateButtons()
            }) { fabricPrivacy.logException(it) }
        )
        updateButtons()
    }

    private fun generateLayout() {
        val currentScreen = screens[currentWizardPage]
        val layout = SWItem(injector, SWItem.Type.NONE).generateLayout(findViewById(R.id.sw_content_fields))
        for (i in currentScreen.items.indices) {
            val currentItem = currentScreen.items[i]
            currentItem.generateDialog(layout)
        }
        sw_scrollview?.smoothScrollTo(0, 0)
    }

    override fun updateButtons() {
        runOnUiThread {
            val currentScreen = screens[currentWizardPage]
            if (currentScreen.validator == null || currentScreen.validator!!.isValid || currentScreen.skippable) {
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

    override fun onBackPressed() {
        if (currentWizardPage == 0) showConfirmation(this, resourceHelper.gs(R.string.exitwizard), Runnable { finish() }) else showPreviousPage(null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun exitPressed(view: View?) {
        sp.putBoolean(R.string.key_setupwizard_processed, true)
        showConfirmation(this, resourceHelper.gs(R.string.exitwizard), Runnable { finish() })
    }

    @Suppress("UNUSED_PARAMETER")
    fun showNextPage(view: View?) {
        finish()
        val intent = Intent(this, SetupWizardActivity::class.java)
        intent.putExtra(intentMessage, nextPage(null))
        startActivity(intent)
    }

    @Suppress("UNUSED_PARAMETER")
    fun showPreviousPage(view: View?) {
        finish()
        val intent = Intent(this, SetupWizardActivity::class.java)
        intent.putExtra(intentMessage, previousPage(null))
        startActivity(intent)
    }

    // Go back to overview
    @Suppress("UNUSED_PARAMETER")
    fun finishSetupWizard(view: View?) {
        sp.putBoolean(R.string.key_setupwizard_processed, true)
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    @Suppress("UNUSED_PARAMETER", "SameParameterValue")
    private fun nextPage(view: View?): Int {
        var page = currentWizardPage + 1
        while (page < screens.size) {
            if (screens[page].visibility == null || screens[page].visibility!!.isValid) return page
            page++
        }
        return min(currentWizardPage, screens.size - 1)
    }

    @Suppress("UNUSED_PARAMETER", "SameParameterValue")
    private fun previousPage(view: View?): Int {
        var page = currentWizardPage - 1
        while (page >= 0) {
            if (screens[page].visibility == null || screens[page].visibility!!.isValid) return page
            page--
        }
        return max(currentWizardPage, 0)
    }
}