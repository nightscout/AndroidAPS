package info.nightscout.androidaps.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityPreferencesBinding
import info.nightscout.configuration.activities.DaggerAppCompatActivityWithResult

class PreferencesActivity : DaggerAppCompatActivityWithResult(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    private var preferenceId = 0
    private var myPreferenceFragment: MyPreferenceFragment? = null
    private var searchView: SearchView? = null

    private lateinit var binding: ActivityPreferencesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(info.nightscout.core.ui.R.style.AppTheme)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(info.nightscout.configuration.R.string.nav_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        myPreferenceFragment = MyPreferenceFragment()
        preferenceId = intent.getIntExtra("id", -1)
        myPreferenceFragment?.arguments = Bundle().also {
            it.putInt("id", preferenceId)
        }
        if (savedInstanceState == null)
            @Suppress("CommitTransaction")
            supportFragmentManager.beginTransaction().replace(R.id.frame_layout, myPreferenceFragment!!).commit()

        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
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
        })
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat, pref: PreferenceScreen): Boolean {
        val fragment = MyPreferenceFragment()
        fragment.arguments = Bundle().also {
            it.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
            it.putInt("id", preferenceId)
        }
        @Suppress("CommitTransaction")
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, fragment, pref.key).addToBackStack(pref.key).commit()
        return true
    }
}