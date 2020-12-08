package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.activity_preferences.*
import javax.inject.Inject

class PreferencesActivity : NoSplashAppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    @Inject lateinit var resourceHelper: ResourceHelper

    var preferenceId = 0
    var myPreferenceFragment: MyPreferenceFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val args = Bundle()
        args.putInt("id", preferenceId)
        args.putString("filter", pref_filter.text.toString())
        myPreferenceFragment?.arguments = args
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, myPreferenceFragment!!).commit()
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

    private fun filterPreferences() {
        myPreferenceFragment?.setFilter(pref_filter.text.toString())
    }
}