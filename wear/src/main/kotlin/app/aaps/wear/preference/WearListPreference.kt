/*
 * Modernized WearListPreference using AndroidX Preferences
 * Replaces deprecated android.preference.ListPreference with androidx.preference.ListPreference
 */

package app.aaps.wear.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

/**
 * Base class for custom Wear OS list preferences using AndroidX Preferences
 * Subclasses override getSummary() and onPreferenceClick() for custom behavior
 */
abstract class WearListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {

    /**
     * Get the summary text for this preference
     * Subclasses override this to provide custom summary text
     */
    abstract fun getSummaryText(context: Context): CharSequence

    /**
     * Called when the preference is clicked
     * Subclasses override this to handle clicks
     */
    abstract fun onPreferenceClick(context: Context)

    init {
        // Update summary
        summary = getSummaryText(context)

        // Set click listener
        setOnPreferenceClickListener {
            onPreferenceClick(context)
            true
        }
    }
}
