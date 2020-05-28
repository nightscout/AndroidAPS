package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class PreferencesActivity : NoSplashAppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP
    var preferenceId = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sets theme and color
        val newTheme: Int = sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        setTheme(ThemeUtil.getThemeId(newTheme))

        setContentView(R.layout.activity_single_fragment)
        title = resourceHelper.gs(R.string.nav_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        val myPreferenceFragment = MyPreferenceFragment()
        preferenceId = intent.getIntExtra("id", -1)
        val args = Bundle()
        args.putInt("id", preferenceId)
        myPreferenceFragment.arguments = args
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, myPreferenceFragment).commit()
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat, pref: PreferenceScreen): Boolean {
        val fragment = MyPreferenceFragment()
        val args = Bundle()
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
        args.putInt("id", preferenceId)
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment, pref.key)
            .addToBackStack(pref.key)
            .commit()
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}