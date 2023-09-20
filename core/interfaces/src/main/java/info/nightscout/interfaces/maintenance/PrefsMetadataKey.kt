package info.nightscout.interfaces.maintenance

import android.content.Context

interface PrefsMetadataKey {

    val key: String
    val icon: Int
    val label: Int
    fun formatForDisplay(context: Context, value: String): String
}