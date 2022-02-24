package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityPreferencesBinding
import info.nightscout.androidaps.events.EventThemeSwitch
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.locale.LocaleHelper

class PreferencesActivity : NoSplashAppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    private var preferenceId = 0
    private var myPreferenceFragment: MyPreferenceFragment? = null

    private lateinit var binding: ActivityPreferencesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // important to set the theme here again for preferences - normal way do not work here
        var themeToSet = spSplash.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            setTheme(themeToSet)
            val theme = super.getTheme()
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.prefFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filterPreferences()
            }

            override fun afterTextChanged(s: Editable) {}
        })

        title = rh.gs(R.string.nav_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        myPreferenceFragment = MyPreferenceFragment()
        preferenceId = intent.getIntExtra("id", -1)
        myPreferenceFragment?.arguments = Bundle().also {
            it.putInt("id", preferenceId)
            it.putString("filter", binding.prefFilter.text.toString())
        }
        if (savedInstanceState == null)
            supportFragmentManager.beginTransaction().replace(R.id.frame_layout, myPreferenceFragment!!).commit()
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat, pref: PreferenceScreen): Boolean {
        val fragment = MyPreferenceFragment()
        fragment.arguments = Bundle().also {
            it.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
            it.putInt("id", preferenceId)
        }
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, fragment, pref.key).addToBackStack(pref.key).commit()
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private fun filterPreferences() {
        myPreferenceFragment?.setFilter(binding.prefFilter.text.toString())
    }
}