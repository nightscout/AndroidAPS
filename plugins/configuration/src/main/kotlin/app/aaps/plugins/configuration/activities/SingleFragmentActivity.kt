package app.aaps.plugins.configuration.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.view.MenuProvider
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.preference.LocalCheckPassword
import app.aaps.core.ui.compose.preference.LocalHashPassword
import app.aaps.core.ui.compose.preference.PluginPreferencesScreen
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.plugins.configuration.R
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

@AndroidEntryPoint
class SingleFragmentActivity : AppCompatActivity() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var overview: Overview
    @Inject lateinit var config: Config
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var visibilityContext: PreferenceVisibilityContext
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var uiInteraction: UiInteraction

    private val compositeDisposable = CompositeDisposable()

    private var plugin: PluginBase? = null

    // Toolbar state for Compose content
    private var toolbarConfig by mutableStateOf(
        ToolbarConfig(
            title = "",
            navigationIcon = { },
            actions = { }
        )
    )

    // State to track if showing compose preferences (only in compose context)
    private var showingComposePreferences by mutableStateOf(false)

    // Toggle between Compose and Fragment rendering (debug only)
    private var forceFragment = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        forceFragment = savedInstanceState?.getBoolean(KEY_FORCE_FRAGMENT, false) ?: false

        plugin = activePlugin.getPluginsList()[intent.getIntExtra("plugin", -1)]
        val currentPlugin = plugin ?: return

        setupPluginContent(currentPlugin, savedInstanceState)
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FORCE_FRAGMENT, forceFragment)
    }

    private fun setupPluginContent(plugin: PluginBase, savedInstanceState: Bundle?) {
        val canToggle = plugin.hasComposeContent() && plugin.hasFragment() && config.isEngineeringMode()
        if (plugin.hasComposeContent() && !forceFragment) {
            setupComposeContent(plugin, canToggle)
        } else {
            setupFragmentContent(plugin, savedInstanceState, canToggle)
        }
    }

    private fun setupComposeContent(plugin: PluginBase, canToggle: Boolean = false) {
        val composeContent = plugin.getComposeContent() ?: return

        // Hide the system ActionBar - we use Compose TopAppBar instead
        supportActionBar?.hide()

        // Initialize toolbar with plugin name
        toolbarConfig = ToolbarConfig(
            title = plugin.name,
            navigationIcon = {
                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (canToggle) {
                    IconButton(onClick = { toggleToFragment() }) {
                        Icon(painterResource(id = app.aaps.core.ui.R.drawable.ic_swap_horiz), contentDescription = "Switch to Fragment")
                    }
                }
                // Settings button if plugin has preferences
                if (shouldShowPreferencesMenu(plugin)) {
                    IconButton(onClick = { openPluginPreferences(plugin) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        )

        setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalConfig provides config,
                LocalProfileUtil provides profileUtil,
                LocalCheckPassword provides cryptoUtil::checkPassword,
                LocalHashPassword provides cryptoUtil::hashPassword
            ) {
                AapsTheme {
                    if (showingComposePreferences) {
                        // Show compose preferences
                        PluginPreferencesScreen(
                            plugin = plugin,
                            visibilityContext = visibilityContext,
                            onBackClick = {
                                showingComposePreferences = false
                            }
                        )
                    } else {
                        // Show plugin content
                        Scaffold(
                            topBar = {
                                AapsTopAppBar(
                                    title = { Text(toolbarConfig.title) },
                                    navigationIcon = { toolbarConfig.navigationIcon() },
                                    actions = { toolbarConfig.actions(this) }
                                )
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                // Invoke the Compose content via ComposablePluginContent interface
                                if (composeContent is ComposablePluginContent) {
                                    composeContent.Render(
                                        setToolbarConfig = { config -> toolbarConfig = config },
                                        onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
                                        onSettings = if (shouldShowPreferencesMenu(plugin)) {
                                            { openPluginPreferences(plugin) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupFragmentContent(plugin: PluginBase, savedInstanceState: Bundle?, canToggle: Boolean = false) {
        setTheme(app.aaps.core.ui.R.style.AppTheme)
        setContentView(R.layout.activity_single_fragment)

        title = plugin.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        if (savedInstanceState == null || forceFragment) {
            supportFragmentManager.beginTransaction().replace(
                R.id.frame_layout,
                supportFragmentManager.fragmentFactory.instantiate(
                    ClassLoader.getSystemClassLoader(),
                    plugin.pluginDescription.fragmentClass!!
                )
            ).commit()
        }

        overview.setVersionView(findViewById(R.id.version))

        // Add menu items for Fragment-based plugins
        val singleFragmentMenuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (canToggle) {
                    menu.add(0, MENU_TOGGLE_COMPOSE, 0, "Compose")
                        .setIcon(app.aaps.core.ui.R.drawable.ic_swap_horiz)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                if (!shouldShowPreferencesMenu(plugin)) return
                menuInflater.inflate(R.menu.menu_single_fragment, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home           -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    MENU_TOGGLE_COMPOSE         -> {
                        toggleToCompose()
                        true
                    }

                    R.id.nav_plugin_preferences -> {
                        openPluginPreferences(plugin)
                        true
                    }

                    else                        -> false
                }
        }
        addMenuProvider(singleFragmentMenuProvider)
    }

    private fun toggleToFragment() {
        forceFragment = true
        recreate()
    }

    private fun toggleToCompose() {
        forceFragment = false
        recreate()
    }

    companion object {

        private const val MENU_TOGGLE_COMPOSE = 9999
        private const val KEY_FORCE_FRAGMENT = "force_fragment"
    }

    private fun shouldShowPreferencesMenu(plugin: PluginBase): Boolean {
        if ((plugin.preferencesId) == PluginDescription.PREFERENCE_NONE) return false
        if (preferences.simpleMode && !plugin.pluginDescription.preferencesVisibleInSimpleMode) return false
        return true
    }

    private fun openPluginPreferences(plugin: PluginBase) {
        protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
            // If we're in compose content AND plugin has compose preferences, show them in-place
            if (plugin.hasComposeContent() && plugin.getPreferenceScreenContent() is PreferenceSubScreenDef) {
                showingComposePreferences = true
            } else {
                // Otherwise use legacy PreferencesActivity
                val i = Intent(this, uiInteraction.preferencesActivity)
                    .setAction("app.aaps.plugins.configuration.activities.SingleFragmentActivity")
                    .putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName)
                startActivity(i)
            }
        }, null)
    }
}
