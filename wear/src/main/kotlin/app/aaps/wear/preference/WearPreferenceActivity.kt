/*
 * Modernized WearPreferenceActivity using AndroidX Preferences
 * Replaces deprecated PreferenceActivity with FragmentActivity + PreferenceFragmentCompat
 */

package app.aaps.wear.preference

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat

/**
 * Base activity for Wear OS preference screens using AndroidX Preferences
 * Subclasses must implement createPreferenceFragment() to provide their preference fragment
 */
abstract class WearPreferenceActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply preference theme before calling super
        setTheme(app.aaps.wear.R.style.WearPreferenceTheme)

        super.onCreate(savedInstanceState)

        // Set content view with a container for the fragment
        setContentView(app.aaps.wear.R.layout.activity_preference)

        // Load the preference fragment if not already added
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(app.aaps.wear.R.id.preference_container, createPreferenceFragment())
                .commit()
        }
    }

    /**
     * Subclasses override this to provide their specific PreferenceFragmentCompat implementation
     */
    abstract fun createPreferenceFragment(): PreferenceFragmentCompat
}
