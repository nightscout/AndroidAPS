package info.nightscout.androidaps.interaction.utils

import android.content.Context
import android.util.AttributeSet
import preference.WearListPreference
import android.widget.Toast
import info.nightscout.androidaps.BuildConfig

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