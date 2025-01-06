package app.aaps

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.PreferencesActivity
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.isRunningRealPumpTest
import app.aaps.databinding.ActivityMainBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.ui.activities.ProfileHelperActivity
import app.aaps.ui.activities.StatsActivity
import app.aaps.ui.activities.TreatmentsActivity
import app.aaps.ui.tabs.TabPageAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Locale
import javax.inject.Inject
import kotlin.system.exitProcess

class MainActivity : DaggerAppCompatActivityWithResult() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var maitenancePlugin: MaintenancePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var uiInteraction: UiInteraction

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private var menuOpen = false
    private var isProtectionCheckActive = false
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Iconify.with(FontAwesomeModule())
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        // initialize screen wake lock
        processPreferenceChange(EventPreferenceChange(BooleanKey.OverviewKeepScreenOn.key))

        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate()
                           else setupViews()
                           setWakeLock()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           // 1st run of app
                           start()
                       }, fabricPrivacy::logException)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START))
                    binding.mainDrawerLayout.closeDrawers()
                else if (menuOpen)
                    menu?.close()
                else if (binding.mainPager.currentItem != 0)
                    binding.mainPager.currentItem = 0
                else finish()
            }
        })
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                MenuCompat.setGroupDividerEnabled(menu, true)
                this@MainActivity.menu = menu
                menuInflater.inflate(R.menu.menu_main, menu)
                pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.nav_preferences        -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(
                                Intent(this@MainActivity, PreferencesActivity::class.java)
                                    .setAction("info.nightscout.androidaps.MainActivity")
                            )
                        })
                        true
                    }

                    R.id.nav_historybrowser     -> {
                        startActivity(Intent(this@MainActivity, HistoryBrowseActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }

                    R.id.nav_treatments         -> {
                        startActivity(Intent(this@MainActivity, TreatmentsActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }

                    R.id.nav_setupwizard        -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }

                    R.id.nav_about              -> {
                        var message = "Build: ${config.BUILD_VERSION}\n"
                        message += "Flavor: ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE}\n"
                        message += "${rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_nightscoutversion_label)} ${activePlugin.activeNsClient?.detectedNsVersion() ?: rh.gs(app.aaps.plugins.main.R.string.not_available_full)}"
                        if (config.isEngineeringMode()) message += "\n${rh.gs(app.aaps.plugins.configuration.R.string.engineering_mode_enabled)}"
                        if (config.isUnfinishedMode()) message += "\nUnfinished mode enabled"
                        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
                        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)
                        val messageSpanned = SpannableString(message)
                        Linkify.addLinks(messageSpanned, Linkify.WEB_URLS)
                        MaterialAlertDialogBuilder(this@MainActivity, app.aaps.core.ui.R.style.DialogTheme)
                            .setTitle(rh.gs(R.string.app_name) + " " + config.VERSION)
                            .setIcon(iconsProvider.getIcon())
                            .setMessage(messageSpanned)
                            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok), null)
                            .setNeutralButton(rh.gs(app.aaps.core.ui.R.string.cta_dont_kill_my_app_info)) { _, _ ->
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://dontkillmyapp.com/" + Build.MANUFACTURER.lowercase().replace(" ", "-"))
                                    )
                                )
                            }
                            .create().apply {
                                show()
                                findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                            }
                        true
                    }

                    R.id.nav_exit               -> {
                        aapsLogger.debug(LTag.CORE, "Exiting")
                        uel.log(Action.EXIT_AAPS, Sources.Aaps)
                        rxBus.send(EventAppExit())
                        finish()
                        System.runFinalization()
                        exitProcess(0)
                    }

                    R.id.nav_plugin_preferences -> {
                        val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(
                                Intent(this@MainActivity, PreferencesActivity::class.java)
                                    .setAction("info.nightscout.androidaps.MainActivity")
                                    .putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName)
                            )
                        })
                        true
                    }
                    /*
                                R.id.nav_survey             -> {
                                    startActivity(Intent(this, SurveyActivity::class.java))
                                    return true
                                }
                    */
                    R.id.nav_defaultprofile     -> {
                        startActivity(Intent(this@MainActivity, ProfileHelperActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }

                    R.id.nav_stats              -> {
                        startActivity(Intent(this@MainActivity, StatsActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }

                    else                        ->
                        actionBarDrawerToggle.onOptionsItemSelected(menuItem)
                }
        })
        // Setup views on 2nd and next activity start
        // On 1st start app is still initializing, start() is delayed and run from EventAppInitialized
        if (config.appInitialized) setupViews()
    }

    private fun start() {
        binding.splash.visibility = View.GONE
        //Check here if loop plugin is disabled. Else check via constraints
        if (!loop.isEnabled()) versionCheckerUtils.triggerCheckVersion()
        setUserStats()
        setupViews()

        if (startWizard() && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
            })
        }
        androidPermission.notifyForStoragePermission(this)
        androidPermission.notifyForBatteryOptimizationPermission(this)
        if (!config.AAPSCLIENT) androidPermission.notifyForLocationPermissions(this)
        if (config.PUMPDRIVERS) {
            if (smsCommunicator.isEnabled())
                androidPermission.notifyForSMSPermissions(this)
            androidPermission.notifyForSystemWindowPermissions(this)
            androidPermission.notifyForBtConnectPermission(this)
        }
        passwordResetCheck(this)
        exportPasswordResetCheck(this)

        // check if identification is set
        if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
            uiInteraction.addNotificationWithAction(
                id = Notification.IDENTIFICATION_NOT_SET,
                text = rh.gs(R.string.identification_not_set),
                level = Notification.INFO,
                buttonText = R.string.set,
                action = Runnable {
                    preferences.put(BooleanKey.GeneralSimpleMode, false)
                    startActivity(
                        Intent(this@MainActivity, PreferencesActivity::class.java)
                            .setAction("info.nightscout.androidaps.MainActivity")
                            .putExtra(UiInteraction.PLUGIN_NAME, MaintenancePlugin::class.java.simpleName)
                    )
                },
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )

        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            uiInteraction.addNotificationWithAction(
                id = Notification.MASTER_PASSWORD_NOT_SET,
                text = rh.gs(app.aaps.core.ui.R.string.master_password_not_set),
                level = Notification.NORMAL,
                buttonText = R.string.set,
                action = { startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences.PROTECTION)) },
                validityCheck = { preferences.get(StringKey.ProtectionMasterPassword) == "" }
            )
        if (preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty())
            uiInteraction.addNotificationWithAction(
                id = Notification.AAPS_DIR_NOT_SELECTED,
                text = rh.gs(app.aaps.core.ui.R.string.aaps_directory_not_selected),
                level = Notification.IMPORTANCE_HIGH,
                buttonText = R.string.select,
                action = { maitenancePlugin.selectAapsDirectory(this) },
                validityCheck = { preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty() }
            )
    }

    private fun startWizard(): Boolean =
        !preferences.get(BooleanKey.GeneralSetupWizardProcessed)

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        actionBarDrawerToggle.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        if (config.appInitialized) binding.splash.visibility = View.GONE
        if (!isProtectionCheckActive) {
            isProtectionCheckActive = true
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, UIRunnable { isProtectionCheckActive = false },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed)) { isProtectionCheckActive = false; finish() } },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed)) { isProtectionCheckActive = false; finish() } }
            )
        }
    }

    private fun setWakeLock() {
        val keepScreenOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun processPreferenceChange(ev: EventPreferenceChange) {
        if (ev.isChanged(BooleanKey.OverviewKeepScreenOn.key)) setWakeLock()
        if (ev.isChanged(StringKey.GeneralSkin.key)) recreate()
    }

    private fun setupViews() {
        // Menu
        val pageAdapter = TabPageAdapter(this)
        binding.mainNavigationView.setNavigationItemSelectedListener { true }
        val menu = binding.mainNavigationView.menu.also { it.clear() }
        for (p in activePlugin.getPluginsList())
            if (p.isEnabled() && p.hasFragment() && p.showInList(p.getType())) {
                // Add to tabs if visible
                if (
                    preferences.simpleMode && p.pluginDescription.simpleModePosition == PluginDescription.Position.TAB ||
                    !preferences.simpleMode && p.isFragmentVisible()
                ) pageAdapter.registerNewFragment(p)
                // Add to menu if not visible
                if (
                    preferences.simpleMode && !p.pluginDescription.neverVisible && p.pluginDescription.simpleModePosition == PluginDescription.Position.MENU ||
                    !preferences.simpleMode && !p.pluginDescription.neverVisible && !p.isFragmentVisible()
                ) {
                    val menuItem = menu.add(p.name)
                    menuItem.isCheckable = true
                    if (p.menuIcon != -1) menuItem.setIcon(p.menuIcon)
                    else menuItem.setIcon(app.aaps.core.ui.R.drawable.ic_settings)
                    menuItem.setOnMenuItemClickListener {
                        startActivity(
                            Intent(this, SingleFragmentActivity::class.java)
                                .setAction(this::class.simpleName)
                                .putExtra("plugin", activePlugin.getPluginsList().indexOf(p))
                        )
                        binding.mainDrawerLayout.closeDrawers()
                        true
                    }
                }
            }
        binding.mainPager.adapter = pageAdapter
        binding.mainPager.offscreenPageLimit = 8 // This may cause more memory consumption

        // Tabs
        if (preferences.get(BooleanKey.OverviewShortTabTitles)) {
            binding.tabsNormal.visibility = View.GONE
            binding.tabsCompact.visibility = View.VISIBLE
            binding.toolbar.layoutParams = LinearLayout.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, resources.getDimension(app.aaps.core.ui.R.dimen.compact_height).toInt())
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            val typedValue = TypedValue()
            if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                binding.toolbar.layoutParams = LinearLayout.LayoutParams(
                    Toolbar.LayoutParams.MATCH_PARENT,
                    TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
                )
            }
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
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menuOpen = true
        if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.mainDrawerLayout.closeDrawers()
        }
        val result = super.onMenuOpened(featureId, menu)
        menu.findItem(R.id.nav_treatments)?.isEnabled = profileFunction.getProfile() != null
        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter?)?.getPluginAt(binding.mainPager.currentItem) ?: return result
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = rh.gs(R.string.nav_preferences_plugin, plugin.name)
            pluginPreferencesMenuItem?.isEnabled = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem).preferencesId != PluginDescription.PREFERENCE_NONE
        }
        if (pluginPreferencesMenuItem?.isEnabled == false) {
            val spanString = SpannableString(this.menu?.findItem(R.id.nav_plugin_preferences)?.title.toString())
            spanString.setSpan(ForegroundColorSpan(rh.gac(app.aaps.core.ui.R.attr.disabledTextColor)), 0, spanString.length, 0)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = spanString
        }
        return result
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        menuOpen = false
        super.onPanelClosed(featureId, menu)
    }

    // Correct place for calling setUserStats() would be probably MainApp
    // but we need to have it called at least once a day. Thus this location

    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        // Size is limited to 36 chars
        val remote = config.REMOTE.lowercase(Locale.getDefault())
            .replace("https://", "")
            .replace("http://", "")
            .replace(".git", "")
            .replace(".com/", ":")
            .replace(".org/", ":")
            .replace(".net/", ":")
        fabricPrivacy.setUserProperty("Mode", config.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.setUserProperty("Remote", remote)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePump.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        fabricPrivacy.setUserProperty("Profile", activePlugin.activeProfileSource.javaClass.simpleName)
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        activePlugin.activeInsulin.let { fabricPrivacy.setUserProperty("Insulin", it::class.java.simpleName) }
        // Add to crash log too
        FirebaseCrashlytics.getInstance().setCustomKey("HEAD", BuildConfig.HEAD)
        FirebaseCrashlytics.getInstance().setCustomKey("Version", config.VERSION_NAME)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildType", config.BUILD_TYPE)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildFlavor", config.FLAVOR)
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", BuildConfig.COMMITTED)
        FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
    }

    /**
     * Check for existing PasswordReset file and
     * reset password to SN of active pump if file exists
     */
    private fun passwordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            val sn = activePlugin.activePump.serialNumber()
            preferences.put(StringKey.ProtectionMasterPassword, cryptoUtil.hashPassword(sn))
            fh.delete()
            // Also clear any stored password
            exportPasswordDataStore.clearPasswordDataStore(context)
            ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.password_set))
        }
    }

    /**
     * Check for existing ExportPasswordReset file and
     * clear password stored in datastore if file exists
     */
    private fun exportPasswordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("ExportPasswordReset")
        if (fh?.exists() == true) {
            exportPasswordDataStore.clearPasswordDataStore(context)
            fh.delete()
            ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.datastore_password_cleared))
        }
    }

}