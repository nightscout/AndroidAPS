package info.nightscout.androidaps.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFileContract
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class SingleFragmentActivity : DaggerAppCompatActivity() {
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var resourceHelper: ResourceHelper

    private var plugin: PluginBase? = null

    val callForPrefFile = registerForActivityResult(PrefsFileContract()) {
        it?.let {
            importExportPrefs.importSharedPreferences(this, it)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_fragment)
        plugin = pluginStore.plugins[intent.getIntExtra("plugin", -1)]
        title = plugin?.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.frame_layout,
                supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), plugin?.pluginDescription?.fragmentClass!!)).commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        } else if (item.itemId == R.id.nav_plugin_preferences) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, Runnable {
                val i = Intent(this, PreferencesActivity::class.java)
                i.putExtra("id", plugin?.preferencesId)
                startActivity(i)
            }, null)
            return true
        } else if (item.itemId == R.id.nav_plugin_help) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = plugin?.helpUrl
            startActivity(intent)
            return true
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_single_fragment, menu)
        if (plugin?.preferencesId == -1) {
            val menuItem = menu.findItem(R.id.nav_plugin_preferences);
            menuItem.setVisible(false);
        }
        if (plugin?.helpUrl == null) {
            val menuItem = menu.findItem(R.id.nav_plugin_help);
            menuItem.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu)
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}