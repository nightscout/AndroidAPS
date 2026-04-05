package app.aaps

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.PersistableBundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.PreferencesActivity
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.utils.isRunningRealPumpTest
import app.aaps.databinding.ActivityMainBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.ui.tabs.TabPageAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : DaggerAppCompatActivityWithResult() {

    private val disposable = CompositeDisposable()
    private var scope: CoroutineScope? = null

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var notificationManager: NotificationManager

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private var menuOpen = false
    private var isProtectionCheckActive = false
    private lateinit var binding: ActivityMainBinding
    private var mainMenuProvider: MenuProvider? = null

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
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, app.aaps.core.ui.R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        // initialize screen wake lock
        setWakeLock()

        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate()
                           else setupViews()
                           setWakeLock()
                       }, fabricPrivacy::logException)
        val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = newScope
        preferences.observe(BooleanKey.OverviewKeepScreenOn).drop(1).onEach { setWakeLock() }.launchIn(newScope)
        preferences.observe(StringKey.GeneralSkin).drop(1).onEach { recreate() }.launchIn(newScope)
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
        mainMenuProvider = object : MenuProvider {
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

                    R.id.nav_setupwizard        -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }

                    R.id.nav_plugin_preferences -> {
                        val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
                        uiInteraction.runPreferencesForPlugin(this@MainActivity, plugin.javaClass.simpleName)
                        true
                    }

                    else                        ->
                        actionBarDrawerToggle.onOptionsItemSelected(menuItem)
                }
        }
        mainMenuProvider?.let { addMenuProvider(it) }
        // Setup views on 2nd and next activity start
        // On 1st start app is still initializing, start() is delayed and run from EventAppInitialized
        if (config.appInitialized) setupViews()
    }

    private fun start() {
        binding.splash.visibility = View.GONE
        setupViews()

        if (startWizard() && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
            })
        }
    }

    private fun startWizard(): Boolean =
        !preferences.get(BooleanNonKey.GeneralSetupWizardProcessed)

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        actionBarDrawerToggle.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mainPager.adapter = null
        binding.mainDrawerLayout.removeDrawerListener(actionBarDrawerToggle)
        mainMenuProvider?.let { removeMenuProvider(it) }
        scope?.cancel()
        scope = null
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        if (config.appInitialized) binding.splash.visibility = View.GONE
        if (!isProtectionCheckActive) {
            isProtectionCheckActive = true
            protectionCheck.queryProtection(
                this, ProtectionCheck.Protection.APPLICATION, UIRunnable { isProtectionCheckActive = false },
                UIRunnable { uiInteraction.showOkDialog(context = this, title = "", message = rh.gs(R.string.authorizationfailed), onFinish = { isProtectionCheckActive = false; finish() }) },
                UIRunnable { uiInteraction.showOkDialog(context = this, title = "", message = rh.gs(R.string.authorizationfailed), onFinish = { isProtectionCheckActive = false; finish() }) }
            )
        }
    }

    private fun setWakeLock() {
        val keepScreenOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

        // FAB to switch to Compose UI
        binding.fabSwitchUi.setOnClickListener {
            startActivity(Intent(this, ComposeMainActivity::class.java))
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

}