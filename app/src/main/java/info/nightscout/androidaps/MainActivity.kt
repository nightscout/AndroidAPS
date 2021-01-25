package info.nightscout.androidaps

import android.annotation.SuppressLint
import android.app.TaskStackBuilder
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_ALWAYS
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_CENTER
import com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_END
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import com.ms_square.etsyblur.BlurSupport
import dagger.android.HasAndroidInjector
import dev.doubledot.doki.ui.DokiActivity
import info.nightscout.androidaps.activities.*
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.activities.ProfileHelperActivity
import info.nightscout.androidaps.activities.SingleFragmentActivity
import info.nightscout.androidaps.activities.StatsActivity
import info.nightscout.androidaps.databinding.ActivityMainBinding
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.historyBrowser.HistoryBrowseActivity
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.plugins.general.overview.OverviewMenus
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.general.themeselector.ScrollingActivity
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.source.*
import info.nightscout.androidaps.setupwizard.SetupWizardActivity
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.isRunningRealPumpTest
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.IconsProvider
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.tabs.TabPageAdapter
import info.nightscout.androidaps.utils.ui.UIRunnable
import info.nightscout.androidaps.utils.wizard.QuickWizard
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.overview_statuslights
import kotlinx.android.synthetic.main.main_bottom_fab_menu.*
import kotlinx.android.synthetic.main.status_fragment.*
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.system.exitProcess

open class MainActivity : NoSplashAppCompatActivity() {

    private val disposable = CompositeDisposable()
    private var scheduledUpdate: ScheduledFuture<*>? = null
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var loopHandler = Handler()
    private var refreshLoop: Runnable? = null
    private var deltashort = ""
    private var avgdelta = ""
    private var isRotate = false

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var androidPermission: AndroidPermission
    @Inject lateinit var sp: SP
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var config: Config
    @Inject lateinit var dexcomPlugin: DexcomPlugin
    @Inject lateinit var xdripPlugin: XdripPlugin
    @Inject lateinit var glimpPlugin: GlimpPlugin
    @Inject lateinit var tomatoPlugin: TomatoPlugin
    @Inject lateinit var randomPlugin: RandomBgPlugin
    @Inject lateinit var nSClientSourcePlugin: NSClientSourcePlugin
    @Inject lateinit var pochTechPlugin: PoctechPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var databaseHelper: DatabaseHelperInterface
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var overviewMenus: OverviewMenus

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private lateinit var binding: ActivityMainBinding

    // change to selected theme in theme manager
    open fun changeTheme(newTheme: Int) {
        setNewTheme(newTheme)
        refreshActivities()
    }

    // change to a new theme selected in theme manager
    open fun setNewTheme(newTheme: Int) {
        sp.putInt("theme", newTheme)
        if ( sp.getBoolean("daynight", true)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", info.nightscout.androidaps.core.R.color.background_dark))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", info.nightscout.androidaps.core.R.color.background_light))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        }

        delegate.applyDayNight()
        setTheme(newTheme)
        ThemeUtil.setActualTheme(newTheme)
    }

    // restart activities if something like theme change happens
    open fun refreshActivities() {
        TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, MainActivity::class.java))
            .addNextIntent(this.intent)
            .startActivities()
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // sets the main theme and color
        if ( sp.getBoolean("daynight", true)) {
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_dark)))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        } else {
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_light)))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        }
        delegate.applyDayNight()
        setTheme(ThemeUtil.getThemeId(sp.getInt("theme", THEME_DARKSIDE)))
        ThemeUtil.setActualTheme(ThemeUtil.getThemeId(sp.getInt("theme", THEME_DARKSIDE)))
        Iconify.with(FontAwesomeModule())
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.bottomAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        //bluring for navigation drawer
        BlurSupport.addTo(main_drawer_layout)

        var downX = 0F
        var downY = 0F
        var dx = 0F
        var dy = 0F
        //remember 3 dot icon for switching fab icon from center to right and back
        val overflowIcon = bottom_app_bar.overflowIcon

        // detect single tap like click
        class SingleTapDetector : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                return true
            }
        }
        val gestureDetector = GestureDetector(this, SingleTapDetector())
        // set on touch listener for move detetction
        fab.setOnTouchListener(fun(view: View, event: MotionEvent): Boolean {
            if (gestureDetector.onTouchEvent(event)) {
                // code for single tap or onclick
                onClick(view)
            } else {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                    }

                    MotionEvent.ACTION_MOVE -> {
                        dx += event.x - downX
                        dy += event.y - downY
                        fab.translationX = dx
                    }

                    MotionEvent.ACTION_UP   -> {
                        if (bottom_app_bar.fabAlignmentMode == FAB_ALIGNMENT_MODE_CENTER) {
                            bottom_app_bar.fabAlignmentMode = FAB_ALIGNMENT_MODE_END
                            bottom_navigation.menu.findItem(R.id.placeholder)?.isVisible = false
                            bottom_app_bar.overflowIcon = null
                        } else {
                            bottom_app_bar.fabAlignmentMode = FAB_ALIGNMENT_MODE_CENTER
                            bottom_navigation.menu.findItem(R.id.placeholder)?.isVisible = true
                            bottom_app_bar.overflowIcon = overflowIcon
                        }
                    }
                }
            }
            return true
        })

        overview_bg?.setOnClickListener {
            val fullText = avgdelta
            this.let {
                OKDialog.show(it, "Delta", fullText, null, sp)
            }
        }


        overview_apsmode?.setOnClickListener  { view: View? -> onClick(view!!) }
        overview_apsmode?.setOnLongClickListener{ view: View? -> onLongClick(view!!) }

        treatmentButton.setOnClickListener { view: View? -> onClick(view!!) }
        calibrationButton.setOnClickListener { view: View? -> onClick(view!!) }
        quickwizardButton.setOnClickListener { view: View? -> onClick(view!!) }
        quickwizardButton.setOnLongClickListener { view: View? -> onLongClick(view!!) }

        setupBottomNavigationView()

        //fab menu
        //hide the fab menu icons and label
        ViewAnimation.init(calibrationButton)
        ViewAnimation.init(quickwizardButton)
        if (main_bottom_fab_menu != null) {
            main_bottom_fab_menu.visibility = View.GONE
        }

        // initialize screen wake lock
        processPreferenceChange(EventPreferenceChange(resourceHelper.gs(R.string.key_keep_screen_on)))
        binding.mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                // do the trick to show bottombar >> performHide and than performShow
                bottom_app_bar.performHide()
                bottom_app_bar.performShow()
                setPluginPreferenceMenuName()
                checkPluginPreferences(binding.mainPager)
                setPluginPreferenceMenuName()
                setDisabledMenuItemColorPluginPreferences()
            }
        })

        //Check here if loop plugin is disabled. Else check via constraints
        if (!loopPlugin.isEnabled(PluginType.LOOP)) versionCheckerUtils.triggerCheckVersion()
        setUserStats()
        setupViews()
        disposable.add(rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (it.recreate) recreate()
                else setupViews()
                setWakeLock()
            }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        )
        if (!sp.getBoolean(R.string.key_setupwizard_processed, false) && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java))
            })
        }
        androidPermission.notifyForStoragePermission(this)
        androidPermission.notifyForBatteryOptimizationPermission(this)
        androidPermission.notifyForLocationPermissions(this)
        if (config.PUMPDRIVERS) {
            androidPermission.notifyForSMSPermissions(this, smsCommunicatorPlugin)
            androidPermission.notifyForSystemWindowPermissions(this)
        }

        // set BG in header are for small display like Unihertz Atom
        //check screen width and choose main dialog
        val dm = DisplayMetrics()
        this@MainActivity.windowManager.defaultDisplay.getMetrics(dm)
        val screenheight: Int = dm.heightPixels
        val smallHeight = screenheight <= Constants.SMALL_HEIGHT

        // Special settings for small displays like atom
        if (smallHeight) {
            overview_bg?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28F)
           // overview_arrow?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24F)
            timeago?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            overview_delta?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            careportal_sensorage?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            careportal_insulinage?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            careportal_reservoirlevel?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            careportal_canulaage?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            careportal_pbage?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
            careportal_batterylevel?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12F)
        }

    }


    open fun onClick(view: View) {
        action(view, view.id, supportFragmentManager)
    }

    private fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.quickwizardButton -> {
                val i = Intent(v.context, QuickWizardListActivity::class.java)
                startActivity(i)
                return true
            }
            R.id.overview_apsmode     -> {
                val args = Bundle()
                args.putInt("showOkCancel", 0)                  // 0-> false
                val pvd = LoopDialog()
                pvd.arguments = args
                pvd.show(supportFragmentManager, "Overview")
            }
        }
        return false
    }

    open fun changeHeaderElements() {
        val pump = activePlugin.activePump
        if ( pump.model() == PumpType.Insulet_Omnipod ) {
            reservoir?.visibility = View.VISIBLE
            canula?.visibility = View.GONE
            battery?.visibility = View.GONE
        } else {
            reservoir?.visibility = View.VISIBLE
            canula?.visibility = View.VISIBLE
            battery?.visibility = View.VISIBLE
        }

        //set sensor icon if possible
        if(dexcomPlugin.isEnabled()) {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_dexcom_g6))
        } else if(tomatoPlugin.isEnabled()) {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_sensor))
        } else if(pochTechPlugin.isEnabled()) {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_poctech))
        } else if(glimpPlugin.isEnabled()) {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_glimp))
        } else if(nSClientSourcePlugin.isEnabled()) {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_nsclient_bg))
        } else if(randomPlugin.isEnabled()) {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_dice))
        }

        else if(xdripPlugin.isEnabled()) {
            if( xdripPlugin.advancedFilteringSupported())
                sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_dexcom_g6))
            else
                sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_xdrip))
        } else {
            sensorage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_generic_cgm))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        val lastBG = iobCobCalculatorPlugin.lastBg()
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val xDripIsBgSource = xdripPlugin.isEnabled(PluginType.BGSOURCE)
        val dexcomIsSource = dexcomPlugin.isEnabled(PluginType.BGSOURCE)

        changeHeaderElements()

        bottom_navigation?.menu?.findItem(R.id.insulinButton)?.isVisible   = (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_insulin_button, true))
        bottom_navigation?.menu?.findItem(R.id.carbsButton)?.isVisible  =  (!activePlugin.activePump.pumpDescription.storesCarbInfo || pump.isInitialized && !pump.isSuspended) && profile != null && sp.getBoolean(R.string.key_show_carbs_button, true)
        bottom_navigation?.menu?.findItem(R.id.wizardButton)?.isVisible  = (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_wizard_button, true))
        bottom_navigation?.menu?.findItem(R.id.cgmButton)?.isVisible =     (sp.getBoolean(R.string.key_show_cgm_button, false) && (xDripIsBgSource || dexcomIsSource))

        if(  (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_treatment_button, false)) ) {
            treatment?.visibility = View.VISIBLE
            treatmentButton?.show()
            treatmentbutton_label?.visibility = View.VISIBLE
        }
        else {
            treatmentButton?.hide()
            treatmentbutton_label?.visibility = View.GONE
            treatment?.visibility = View.GONE
        }

        if( (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_calibration_button, false)) ) {
            calibration?.visibility = View.VISIBLE
            calibrationButton?.show()
            calibrationbutton_label?.visibility = View.VISIBLE
        }
        else{
            calibrationButton?.hide()
            calibrationbutton_label?.visibility = View.GONE
            calibration?.visibility = View.GONE
        }

        // QuickWizard button
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && lastBG != null && profile != null && pump.isInitialized && !pump.isSuspended) {
            quickwizard?.visibility = View.VISIBLE
            quickwizardButton?.show()
            quickwizardbutton_label?.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, lastBG, false)
            quickwizardbutton_label?.text = quickWizardEntry.buttonText() + "\n" + resourceHelper.gs(R.string.format_carbs, quickWizardEntry.carbs()) +
                " " + resourceHelper.gs(R.string.formatinsulinunits, wizard.calculatedTotalInsulin)
            if (wizard.calculatedTotalInsulin <= 0) quickwizardButton?.visibility = View.GONE
        } else{
            quickwizardButton?.hide()
            quickwizardbutton_label?.visibility = View.GONE
            quickwizard?.visibility = View.GONE
        }


        // fab menu is empty -> so we do not need the fab menu button for opening fab menu
        if ( treatmentButton?.visibility == View.GONE && calibrationButton?.visibility == View.GONE && quickwizardButton?.visibility == View.GONE ) {
            fab.hide()
            bottom_navigation.menu.findItem(R.id.placeholder)?.isVisible = false
        } else {
            bottom_navigation.menu.findItem(R.id.placeholder)?.isVisible = true
            fab.show()
        }
    }

    fun action(view: View?, id: Int, manager: FragmentManager?) {
        val fillDialog = FillDialog()
        val newCareDialog = CareDialog()

        this.let {
            when (id) {
                R.id.sensorage, R.id.careportal_sensorage -> {
                    newCareDialog.setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(manager!!, "Actions")
                    return
                }

                R.id.reservoirView, R.id.canulaage -> {
                    fillDialog.show(manager!!, "FillDialog")
                    return
                }

                R.id.batteryage -> {
                    newCareDialog.setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(manager!!, "Actions")
                    return
                }

                R.id.fab -> {
                    isRotate = ViewAnimation.rotateFab(view, !isRotate)
                    if (isRotate) {
                        main_bottom_fab_menu.visibility = View.VISIBLE
                        ViewAnimation.showIn(calibrationButton)
                        ViewAnimation.showIn(quickwizardButton)
                        ViewAnimation.showIn(treatmentButton)
                    } else {
                        ViewAnimation.showOut(calibrationButton)
                        ViewAnimation.showOut(quickwizardButton)
                        ViewAnimation.showOut(treatmentButton)
                        main_bottom_fab_menu.visibility = View.GONE
                    }
                    bottom_app_bar.performHide()
                    bottom_app_bar.performShow()
                    return
                }

                R.id.treatmentButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { TreatmentDialog().show(manager!!, "MainActivity") })
                R.id.quickwizardButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { onClickQuickWizard() })

                R.id.cgmButton -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE))
                        openCgmApp("com.eveningoutpost.dexdrip")
                    else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        dexcomPlugin.findDexcomPackageName()?.let {
                            openCgmApp(it)
                        }
                                ?: ToastUtils.showToastInUiThread(this, resourceHelper.gs(R.string.dexcom_app_not_installed))
                    }
                }

                R.id.calibrationButton -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE)) {
                        CalibrationDialog().show(supportFragmentManager, "CalibrationDialog")
                    } else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        try {
                            dexcomPlugin.findDexcomPackageName()?.let {
                                startActivity(Intent("com.dexcom.cgm.activities.MeterEntryActivity").setPackage(it))
                            }
                                    ?: ToastUtils.showToastInUiThread(this, resourceHelper.gs(R.string.dexcom_app_not_installed))
                        } catch (e: ActivityNotFoundException) {
                            ToastUtils.showToastInUiThread(this, resourceHelper.gs(R.string.g5appnotdetected))
                        }
                    }
                }

                R.id.overview_apsmode     -> {
                    val args = Bundle()
                    args.putInt("showOkCancel", 1)
                    val pvd = LoopDialog()
                    pvd.arguments = args
                    pvd.show(manager!!, "Overview")
                }
            }
        }
    }

    private fun setupBottomNavigationView() {
        val manager = supportFragmentManager
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (manager.isStateSaved) return
        bottom_navigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.wizardButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { WizardDialog().show(manager, "Main") })
                R.id.insulinButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { InsulinDialog().show(manager, "Main") })
                R.id.carbsButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { CarbsDialog().show(manager, "Main") })

                R.id.cgmButton -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE))
                        openCgmApp("com.eveningoutpost.dexdrip")
                    else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        dexcomPlugin.findDexcomPackageName()?.let {
                            openCgmApp(it)
                        }
                                ?: ToastUtils.showToastInUiThread(this, resourceHelper.gs(R.string.dexcom_app_not_installed))
                    }
                }
            }
            true
        }
    }

    private fun openCgmApp(packageName: String) {
        this.let {
            val packageManager = it.packageManager
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw ActivityNotFoundException()
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                it.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                OKDialog.show(it, "", resourceHelper.gs(R.string.error_starting_cgm), null, sp)
            }
        }
    }

    private fun onClickQuickWizard() {
        val actualBg = iobCobCalculatorPlugin.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && actualBg != null && profile != null) {
            quickwizardButton?.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true)
            if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(quickWizardEntry.carbs())).value()
                this.let {
                    if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints) || carbsAfterConstraints != quickWizardEntry.carbs()) {
                        OKDialog.show(it, resourceHelper.gs(R.string.treatmentdeliveryerror), resourceHelper.gs(R.string.constraints_violation) + "\n" + resourceHelper.gs(R.string.changeyourinput), null, sp)
                        return
                    }
                    wizard.confirmAndExecute(it)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun upDateGlucose() {
        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        val actualBG = iobCobCalculatorPlugin.actualBg()
        val lastBG = iobCobCalculatorPlugin.lastBg()
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()

        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            val color = when {
                lastBG.valueToUnits(units) < lowLine  -> resourceHelper.getAttributeColor(this, R.attr.bgLow)
                lastBG.valueToUnits(units) > highLine -> resourceHelper.getAttributeColor(this, R.attr.bgHigh)
                else                                  -> resourceHelper.getAttributeColor(this, R.attr.bgInRange)
            }

            overview_bg?.text = lastBG.valueToUnitsToString(units)
            overview_bg?.setTextColor(color)
            overview_bg?.text = lastBG.valueToUnitsToString(units)
            overview_bg?.setTextColor(color)
            overview_arrow?.setImageResource(lastBG.directionToIcon(databaseHelper))
            overview_arrow?.setColorFilter(color)

            val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
            if (glucoseStatus != null) {
                overview_delta?.text = "Δ ${Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)}"
                deltashort = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                avgdelta = "Δ15m: ${Profile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units)}\nΔ40m: ${Profile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units)}"
            } else {
                overview_delta?.text = "Δ " + resourceHelper.gs(R.string.notavailable)
                deltashort = "---"
                avgdelta = ""
            }

            // strike through if BG is old
            overview_bg?.let { overview_bg ->
                var flag = overview_bg.paintFlags
                flag = if (actualBG == null) {
                    flag or Paint.STRIKE_THRU_TEXT_FLAG
                } else flag and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                overview_bg.paintFlags = flag
            }
            timeago?.text = DateUtil.minAgo(resourceHelper, lastBG.date)
        }
    }

    private fun upDateStatusLight() {
        // Status lights
        overview_statuslights?.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || config.NSCLIENT).toVisibility()
        statusLightHandler.updateStatusLights(
                careportal_canulaage,
                careportal_insulinage,
                careportal_reservoirlevel,
                careportal_sensorage,
            null,
                careportal_pbage,
                careportal_batterylevel,
                resourceHelper.getAttributeColor(this, R.attr.statuslight_normal),
                resourceHelper.getAttributeColor(this, R.attr.statuslight_Warning),
                resourceHelper.getAttributeColor(this, R.attr.statuslight_alarm))
    }

    private fun upDateLoop() { 
        // open loop mode
        val pump = activePlugin.activePump
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
         if (config.APS && pump.pumpDescription.isTempBasalCapable) {
            overview_apsmode?.visibility = View.VISIBLE
            when {
                loopPlugin.isEnabled() && loopPlugin.isSuperBolus                       -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_superbolus)
                    overview_apsmode_text?.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                }

                loopPlugin.isDisconnected                                               -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_disconnected)
                    overview_apsmode_text?.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                }

                loopPlugin.isEnabled() && loopPlugin.isSuspended                        -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_paused)
                    overview_apsmode_text?.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                }

                pump.isSuspended                                                        -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_paused)
                    overview_apsmode_text?.text = ""
                }

                loopPlugin.isEnabled() && closedLoopEnabled.value() && loopPlugin.isLGS -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_lgs)
                    overview_apsmode_text?.text = ""
                }

                loopPlugin.isEnabled() && closedLoopEnabled.value()                     -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_closed)
                    overview_apsmode_text?.text = ""
                }

                loopPlugin.isEnabled() && !closedLoopEnabled.value()                    -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_open)
                    overview_apsmode_text?.text = ""
                }

                else                                                                    -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_disabled)
                    overview_apsmode_text?.text = ""
                }
            }
        } else {
            overview_apsmode_text?.visibility = View.GONE
        }
    }

    private fun setDisabledMenuItemColorPluginPreferences() {
        if( pluginPreferencesMenuItem?.isEnabled == false){
            val spanString = SpannableString(this.menu?.findItem(R.id.nav_plugin_preferences)?.title.toString())
            spanString.setSpan(ForegroundColorSpan( getColor(R.color.concinnity_grey)) , 0,spanString.length, 0)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = spanString
        }
    }


    private fun checkPluginPreferences(viewPager: ViewPager2) {
       if (viewPager.currentItem >= 0) pluginPreferencesMenuItem?.isEnabled = (viewPager.adapter as TabPageAdapter).getPluginAt(viewPager.currentItem).preferencesId != -1
    }

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        actionBarDrawerToggle.syncState()
    }

    public override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, null,
            UIRunnable { OKDialog.show(this, "", resourceHelper.gs(R.string.authorizationfailed), { finish() }, sp) },
            UIRunnable { OKDialog.show(this, "", resourceHelper.gs(R.string.authorizationfailed), { finish() }, sp) }
        )
        disposable.add(rxBus
                .toObservable(EventRefreshOverview::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.now) updateGUI(it.from)
                    else scheduleUpdateGUI(it.from)
                }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventExtendedBolusChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventExtendedBolusChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventTempBasalChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventTreatmentChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventTreatmentChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventTempTargetChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventTempTargetChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventAcceptOpenLoopChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventAcceptOpenLoopChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventCareportalEventChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventCareportalEventChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventInitializationChanged::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventInitializationChanged") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventAutosensCalculationFinished::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventAutosensCalculationFinished") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventProfileNeedsUpdate::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventProfileNeedsUpdate") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventPreferenceChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventPreferenceChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
                .toObservable(EventNewOpenLoopNotification::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ scheduleUpdateGUI("EventNewOpenLoopNotification") }) { fabricPrivacy.logException(it) })
        refreshLoop = Runnable {
            scheduleUpdateGUI("refreshLoop")
            loopHandler.postDelayed(refreshLoop, 60 * 1000L)
        }
        loopHandler.postDelayed(refreshLoop, 60 * 1000L)

        upDateStatusLight()
        upDateLoop()
        scheduleUpdateGUI("onResume")
    }

    private fun scheduleUpdateGUI(from: String) {
        class UpdateRunnable : Runnable {
            override fun run() {
                runOnUiThread {
                    updateGUI(from)
                    scheduledUpdate = null
                }
            }
        }
        // prepare task for execution in 500 milliseconds
        // cancel waiting task to prevent multiple updates
        scheduledUpdate?.cancel(false)
        val task: Runnable = UpdateRunnable()
        scheduledUpdate = worker.schedule(task, 500, TimeUnit.MILLISECONDS)
    }

    @SuppressLint("SetTextI18n")
    fun updateGUI(from: String) {
        aapsLogger.debug("UpdateGUI in MainActivity from $from")
        upDateGlucose()
        upDateStatusLight()
        upDateLoop()
        processButtonsVisibility()
    }

    /*
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        overviewMenus.createContextMenu(menu, v)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return if (overviewMenus.onContextItemSelected(item, supportFragmentManager)) true else super.onContextItemSelected(item)
    }
     */

        private fun setWakeLock() {
        val keepScreenOn = sp.getBoolean(R.string.key_keep_screen_on, false)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun processPreferenceChange(ev: EventPreferenceChange) {
        if (ev.isChanged(resourceHelper, R.string.key_keep_screen_on)) setWakeLock()
        if (ev.isChanged(resourceHelper, R.string.key_skin)) recreate()
    }

    private fun setupViews() {
        // Menu
        val pageAdapter = TabPageAdapter(this)
        binding.mainNavigationView.setNavigationItemSelectedListener { true }
        val menu = binding.mainNavigationView.menu.also { it.clear() }
        var itemId = 0
        for (p in activePlugin.getPluginsList()) {
            pageAdapter.registerNewFragment(p)
            if (
                p.hasFragment() && p.isFragmentVisible() && p.isEnabled(p.pluginDescription.type) && !p.pluginDescription.neverVisible) {
                val menuItem = menu.add(Menu.NONE, itemId++, Menu.NONE, p.name)
                if(p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else
                {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.isCheckable = true
                if (p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.setOnMenuItemClickListener {
                    binding.mainDrawerLayout.closeDrawers()
                    main_pager.setCurrentItem(it.itemId, true)
                    true
                }
            }
        }
        binding.mainPager.adapter = pageAdapter
        binding.mainPager.offscreenPageLimit = 8 // This may cause more memory consumption
        checkPluginPreferences(binding.mainPager)

        // Tabs
        if (sp.getBoolean(R.string.key_short_tabtitles, false)) {
            binding.tabsNormal.visibility = View.GONE
            binding.tabsCompact.visibility = View.VISIBLE
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            TabLayoutMediator(binding.tabsNormal, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).name
            }.attach()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun setPluginPreferenceMenuName() {
        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = resourceHelper.gs(R.string.nav_preferences_plugin, plugin.name)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //show all selected plugins not selected for hamburger menu in option menu
        this.menu = menu
        var itemId = 0
        for (p in activePlugin.getPluginsList()) {
            if (p.hasFragment() && !p.isFragmentVisible() && p.isEnabled(p.pluginDescription.type) && !p.pluginDescription.neverVisible) {
                val menuItem = menu.add(Menu.NONE, itemId++, Menu.NONE, p.name )
                if(p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else
                {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.setOnMenuItemClickListener {
                    val intent = Intent(this, SingleFragmentActivity::class.java)
                    intent.putExtra("plugin", activePlugin.getPluginsList().indexOf(p))
                    startActivity(intent)
                    true
                }
            }
        }
        menuInflater.inflate(R.menu.menu_main, menu)
        pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
        setPluginPreferenceMenuName()
        checkPluginPreferences(main_pager)
        setDisabledMenuItemColorPluginPreferences()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_preferences -> {
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    val i = Intent(this, PreferencesActivity::class.java)
                    i.putExtra("id", -1)
                    startActivity(i)
                })
                return true
            }

            R.id.nav_historybrowser -> {
                startActivity(Intent(this, HistoryBrowseActivity::class.java))
                return true
            }

            R.id.nav_themeselector -> {
                startActivity(Intent(this, ScrollingActivity::class.java))
                return true
            }
            R.id.nav_setupwizard -> {
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    startActivity(Intent(this, SetupWizardActivity::class.java))
                })
                return true
            }

            R.id.nav_about -> {
                var message = "Build: ${BuildConfig.BUILDVERSION}\n"
                message += "Flavor: ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE}\n"
                message += "${resourceHelper.gs(R.string.configbuilder_nightscoutversion_label)} ${nsSettingsStatus.nightscoutVersionName}"
                if (buildHelper.isEngineeringMode()) message += "\n${resourceHelper.gs(R.string.engineering_mode_enabled)}"
                if (!fabricPrivacy.fabricEnabled()) message += "\n${resourceHelper.gs(R.string.fabric_upload_disabled)}"
                message += resourceHelper.gs(R.string.about_link_urls)
                val messageSpanned = SpannableString(message)
                Linkify.addLinks(messageSpanned, Linkify.WEB_URLS)
                AlertDialog.Builder(this)
                    .setTitle(resourceHelper.gs(R.string.app_name) + " " + BuildConfig.VERSION + "\nNew GUI")
                    .setIcon(iconsProvider.getIcon())
                    .setMessage(messageSpanned)
                    .setPositiveButton(resourceHelper.gs(R.string.ok), null)
                    .setNeutralButton(resourceHelper.gs(R.string.cta_dont_kill_my_app_info)) { _, _ -> DokiActivity.start(context = this@MainActivity) }
                    .create().apply {
                        show()
                        findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                    }
                return true
            }

            R.id.nav_exit -> {
                aapsLogger.debug(LTag.CORE, "Exiting")
                rxBus.send(EventAppExit())
                finish()
                System.runFinalization()
                exitProcess(0)
            }

            R.id.nav_plugin_preferences -> {
                val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    val i = Intent(this, PreferencesActivity::class.java)
                    i.putExtra("id", plugin.preferencesId)
                    startActivity(i)
                })
                return true
            }
/*
            R.id.nav_survey             -> {
                startActivity(Intent(this, SurveyActivity::class.java))
                return true
            }
*/
            R.id.nav_defaultprofile -> {
                startActivity(Intent(this, ProfileHelperActivity::class.java))
                return true
            }

            R.id.nav_stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                return true
            }
        }
        return actionBarDrawerToggle.onOptionsItemSelected(item)
    }

    // Correct place for calling setUserStats() would be probably MainApp
    // but we need to have it called at least once a day. Thus this location

    @SuppressLint("Range")
    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        // Size is limited to 36 chars
        val remote = BuildConfig.REMOTE.toLowerCase(Locale.getDefault())
            .replace("https://", "")
            .replace("http://", "")
            .replace(".git", "")
            .replace(".com/", ":")
            .replace(".org/", ":")
            .replace(".net/", ":")
        fabricPrivacy.firebaseAnalytics.setUserProperty("Mode", BuildConfig.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.firebaseAnalytics.setUserProperty("Language", sp.getString(R.string.key_language, Locale.getDefault().language))
        fabricPrivacy.firebaseAnalytics.setUserProperty("Version", BuildConfig.VERSION)
        fabricPrivacy.firebaseAnalytics.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.firebaseAnalytics.setUserProperty("Remote", remote)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.firebaseAnalytics.setUserProperty("Hash", hashes[0])
        activePlugin.activePump.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.NSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.firebaseAnalytics.setUserProperty("BgSource", it::class.java.simpleName) }
        fabricPrivacy.firebaseAnalytics.setUserProperty("Profile", activePlugin.activeProfileInterface.javaClass.simpleName)
        activePlugin.activeSensitivity.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Sensitivity", it::class.java.simpleName) }
        activePlugin.activeInsulin.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Insulin", it::class.java.simpleName) }
        // Add to crash log too
        FirebaseCrashlytics.getInstance().setCustomKey("HEAD", BuildConfig.HEAD)
        FirebaseCrashlytics.getInstance().setCustomKey("Version", BuildConfig.VERSION)
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", BuildConfig.COMMITTED)
        FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
    }

}
