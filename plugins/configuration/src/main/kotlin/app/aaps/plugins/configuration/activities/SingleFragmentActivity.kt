package app.aaps.plugins.configuration.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.configuration.R
import javax.inject.Inject

class SingleFragmentActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private var plugin: PluginBase? = null

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

        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (plugin?.preferencesId != -1) menuInflater.inflate(R.menu.menu_single_fragment, menu)
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
                                .putExtra("id", plugin?.preferencesId)
                            startActivity(i)
                        }, null)
                        true
                    }

                    else                        -> false
                }
        })
    }
}