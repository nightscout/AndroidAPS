package info.nightscout.configuration.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import info.nightscout.configuration.R
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.ui.UiInteraction
import javax.inject.Inject

class SingleFragmentActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private var plugin: PluginBase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(info.nightscout.core.ui.R.style.AppTheme)
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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home           -> {
                finish()
                true
            }

            R.id.nav_plugin_preferences -> {
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    val i = Intent(this, uiInteraction.preferencesActivity)
                    i.putExtra("id", plugin?.preferencesId)
                    startActivity(i)
                }, null)
                true
            }

            else                        -> super.onOptionsItemSelected(item)
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (plugin?.preferencesId != -1) menuInflater.inflate(R.menu.menu_single_fragment, menu)
        return super.onCreateOptionsMenu(menu)
    }
}