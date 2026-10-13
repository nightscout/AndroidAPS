package app.aaps.plugins.configuration.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import androidx.core.view.MenuProvider
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.configuration.R
import javax.inject.Inject

class SingleFragmentActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var overview: Overview

    private var plugin: PluginBase? = null
    private var singleFragmentMenuProvider: MenuProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(app.aaps.core.ui.R.style.AppTheme)
        setContentView(R.layout.activity_single_fragment)
        plugin = activePlugin.getPluginsList()[intent.getIntExtra("plugin", -1)]
        title = plugin?.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(
                R.id.frame_layout,
                supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), plugin?.pluginDescription?.fragmentClass!!)
            ).commit()
        }

        overview.setVersionView(findViewById<TextView>(R.id.version))
        // Add menu items without overriding methods in the Activity
        singleFragmentMenuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if ((plugin?.preferencesId ?: return) == PluginDescription.PREFERENCE_NONE) return
                if ((preferences.simpleMode && plugin?.pluginDescription?.preferencesVisibleInSimpleMode != true)) return
                menuInflater.inflate(R.menu.menu_single_fragment, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home           -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    R.id.nav_plugin_preferences -> {
                        protectionCheck.queryProtection(this@SingleFragmentActivity, ProtectionCheck.Protection.PREFERENCES, {
                            val i = Intent(this@SingleFragmentActivity, uiInteraction.preferencesActivity)
                                .setAction("app.aaps.plugins.configuration.activities.SingleFragmentActivity")
                                .putExtra(UiInteraction.PLUGIN_NAME, plugin?.javaClass?.simpleName)
                            startActivity(i)
                        }, null)
                        true
                    }

                    else                        -> false
                }
        }
        singleFragmentMenuProvider?.let { addMenuProvider(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        singleFragmentMenuProvider?.let { removeMenuProvider(it) }
    }
}