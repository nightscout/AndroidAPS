package info.nightscout.androidaps.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import javax.inject.Inject

class SingleFragmentActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var protectionCheck: ProtectionCheck

    private var plugin: PluginBase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_fragment)
        plugin = pluginStore.plugins[intent.getIntExtra("plugin", -1)]
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
                    val i = Intent(this, PreferencesActivity::class.java)
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}