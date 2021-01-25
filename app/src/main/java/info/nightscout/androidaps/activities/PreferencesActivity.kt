package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import kotlinx.android.synthetic.main.activity_preferences.*

class PreferencesActivity : NoSplashAppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    var preferenceId = 0
    var myPreferenceFragment: MyPreferenceFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //setContentView(R.layout.activity_single_fragment)
        setContentView(R.layout.activity_preferences)

        pref_filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filterPreferences()
            }

            override fun afterTextChanged(s: Editable) {}
        })

        title = resourceHelper.gs(R.string.nav_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        myPreferenceFragment = MyPreferenceFragment()
        preferenceId = intent.getIntExtra("id", -1)
        myPreferenceFragment?.arguments = Bundle().also {
            it.putInt("id", preferenceId)
            it.putString("filter", pref_filter.text.toString())
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
        myPreferenceFragment?.setFilter(pref_filter.text.toString())
    }
}