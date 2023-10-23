package app.aaps.wear.interaction.utils

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import app.aaps.wear.BuildConfig
import preference.WearListPreference

/**
 * Created by adrian on 07/08/17.
 */
@Suppress("unused")
class VersionPreference(context: Context?, attrs: AttributeSet?) : WearListPreference(context, attrs) {

    override fun getSummary(context: Context): CharSequence {
        return BuildConfig.BUILDVERSION
    }

    override fun onPreferenceClick(context: Context) {
        Toast.makeText(context, "Build version:" + BuildConfig.BUILDVERSION, Toast.LENGTH_LONG).show()
    }

    init {
        entries = arrayOf<CharSequence>(BuildConfig.BUILDVERSION)
        entryValues = arrayOf<CharSequence>(BuildConfig.BUILDVERSION)
    }
}