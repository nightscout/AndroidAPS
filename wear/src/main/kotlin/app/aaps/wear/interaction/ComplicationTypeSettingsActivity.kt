package app.aaps.wear.interaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import app.aaps.wear.R
import app.aaps.wear.preference.WearPreferenceActivity

/**
 * Hosts one sub-screen of CustomWatchface's settings - today the complication type priority screen.
 *
 * A separate activity rather than a nested `PreferenceScreen` so that Back returns to the watch face
 * settings on its own, with no navigation state to manage. It deliberately knows nothing about which
 * rows it shows: the key arrives in the intent and
 * [CustomWatchfaceConfigurationFragment] asks the watch face what belongs behind it.
 *
 * Nothing here needs the editor session that [ConfigurationActivity] owns - these rows only read and
 * write preferences - so it is safe to open from either entry point.
 */
class ComplicationTypeSettingsActivity : WearPreferenceActivity() {

    private var subScreenKey = 0

    companion object {

        private const val EXTRA_SUB_SCREEN_KEY = "app.aaps.wear.interaction.EXTRA_SUB_SCREEN_KEY"

        /** Opens the sub-screen the watch face declares behind [subScreenKey]. */
        fun intent(context: Context, @StringRes subScreenKey: Int): Intent =
            Intent(context, ComplicationTypeSettingsActivity::class.java).putExtra(EXTRA_SUB_SCREEN_KEY, subScreenKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // MUST be read before super.onCreate(), which is what creates the fragment.
        subScreenKey = intent.getIntExtra(EXTRA_SUB_SCREEN_KEY, 0)

        super.onCreate(savedInstanceState)
        title = getString(R.string.pref_complication_type_priority)

        // Same background and padding treatment as every other watch settings screen.
        val view = window.decorView as ViewGroup
        removeBackgroundRecursively(view)
        view.background = ContextCompat.getDrawable(this, R.drawable.settings_background)
        view.requestFocus()
        findViewById<ViewGroup>(android.R.id.content)?.setPadding(0, 50, 0, 50)
    }

    override fun createPreferenceFragment(): PreferenceFragmentCompat =
        CustomWatchfaceConfigurationFragment.newInstance(subScreenKey)

    private fun removeBackgroundRecursively(parent: View) {
        if (parent is ViewGroup)
            for (i in 0 until parent.childCount)
                removeBackgroundRecursively(parent.getChildAt(i))
        parent.background = null
    }
}
