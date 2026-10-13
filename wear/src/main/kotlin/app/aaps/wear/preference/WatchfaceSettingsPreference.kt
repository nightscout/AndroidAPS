package app.aaps.wear.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import app.aaps.wear.R

/**
 * Preference for accessing watchface settings
 */
@Suppress("unused")
class WatchfaceSettingsPreference(context: Context, attrs: AttributeSet?) : WearListPreference(context, attrs) {

    private val prefMoreWatchfaceSettings: String = context.getString(R.string.pref_moreWatchfaceSettings)
    private val prefLookInYourWatchfaceConfiguration: String = context.getString(R.string.pref_lookInYourWatchfaceConfiguration)

    override fun getSummaryText(context: Context): CharSequence = ""

    override fun onPreferenceClick(context: Context) {
        Toast.makeText(context, prefLookInYourWatchfaceConfiguration, Toast.LENGTH_LONG).show()
    }

    init {
        entries = arrayOf<CharSequence>(prefMoreWatchfaceSettings)
        entryValues = arrayOf<CharSequence>("")
    }
}