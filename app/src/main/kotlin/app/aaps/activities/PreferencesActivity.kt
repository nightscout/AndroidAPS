package app.aaps.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import app.aaps.R
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.utils.extensions.safeGetSerializableExtra
import app.aaps.databinding.ActivityPreferencesBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult

class PreferencesActivity : DaggerAppCompatActivityWithResult(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    private var pluginName: String? = null
    private var customPreference: UiInteraction.Preferences? = null
    private var myPreferenceFragment: MyPreferenceFragment? = null
    private var searchView: SearchView? = null
    private var preferencesMenuProvider: MenuProvider? = null

    private lateinit var binding: ActivityPreferencesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(app.aaps.plugins.configuration.R.string.nav_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        myPreferenceFragment = MyPreferenceFragment()
        pluginName = intent.getStringExtra(UiInteraction.PLUGIN_NAME)
        customPreference = intent?.safeGetSerializableExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences::class.java)
        myPreferenceFragment?.arguments = Bundle().also {
            it.putString(UiInteraction.PLUGIN_NAME, pluginName)
            it.putSerializable(UiInteraction.PREFERENCE, customPreference)
        }
        if (savedInstanceState == null)
            @Suppress("CommitTransaction")
            supportFragmentManager.beginTransaction().replace(R.id.frame_layout, myPreferenceFragment!!).commit()

        // Add menu items without overriding methods in the Activity
        preferencesMenuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu_preferences, menu)
                val searchItem = menu.findItem(R.id.menu_search)
                searchView = searchItem.actionView as SearchView
                searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                    override fun onQueryTextChange(newText: String): Boolean {
                        myPreferenceFragment?.setFilter(newText)
                        return false
                    }

                    override fun onQueryTextSubmit(query: String): Boolean = false
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        }
        preferencesMenuProvider?.let { addMenuProvider(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchView?.setOnQueryTextListener(null)
        preferencesMenuProvider?.let { removeMenuProvider(it) }
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat, pref: PreferenceScreen): Boolean {
        val fragment = MyPreferenceFragment()
        fragment.arguments = Bundle().also {
            it.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
            it.putString(UiInteraction.PLUGIN_NAME, pluginName)
        }
        @Suppress("CommitTransaction")
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, fragment, pref.key).addToBackStack(pref.key).commit()
        return true
    }
}