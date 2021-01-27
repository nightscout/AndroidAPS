package info.nightscout.androidaps.activities

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFileContract
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject


class SingleFragmentActivity : DaggerAppCompatActivityWithResult() {
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var sp: SP

    private var plugin: PluginBase? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_single_fragment)

        // Important to set theme here
        val themeToSet = sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            setTheme(themeToSet)
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if ( sp.getBoolean("daynight", true)) {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_dark)))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        } else {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_light)))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        }

        // set action bar background to primary color of theme
        val cd = ColorDrawable(resourceHelper.getAttributeColor(this, R.attr.colorPrimary))
        supportActionBar?.setBackgroundDrawable(cd)

        val toolbar: Toolbar = findViewById<Toolbar>(info.nightscout.androidaps.core.R.id.action_bar)
        toolbar.setTitleTextColor(resourceHelper.getAttributeColor(this, R.attr.colorOnPrimary))

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
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (plugin?.preferencesId != -1) menuInflater.inflate(R.menu.menu_single_fragment, menu)
        return super.onCreateOptionsMenu(menu)
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}