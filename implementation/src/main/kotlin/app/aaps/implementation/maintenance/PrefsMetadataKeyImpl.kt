package app.aaps.implementation.maintenance

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.implementation.R
import app.aaps.implementation.maintenance.data.PrefsFormat

enum class PrefsMetadataKeyImpl(override val key: String, override val icon: ImageVector, @StringRes override val label: Int) : PrefsMetadataKey {

    FILE_FORMAT("format", Icons.Default.Description, R.string.metadata_label_format),
    CREATED_AT("created_at", Icons.Default.Event, R.string.metadata_label_created_at),
    AAPS_VERSION("aaps_version", Icons.Default.Info, R.string.metadata_label_aaps_version),
    AAPS_FLAVOUR("aaps_flavour", Icons.Default.Style, R.string.metadata_label_aaps_flavour),
    DEVICE_NAME("device_name", Icons.Default.Badge, R.string.metadata_label_device_name),
    DEVICE_MODEL("device_model", Icons.Default.PhoneAndroid, R.string.metadata_label_device_model),
    ENCRYPTION("encryption", Icons.Default.Lock, R.string.metadata_label_encryption);

    companion object {

        private val keyToEnumMap = HashMap<String, PrefsMetadataKey>()

        init {
            for (value in PrefsMetadataKeyImpl.entries) keyToEnumMap[value.key] = value
        }

        fun fromKey(key: String): PrefsMetadataKey? =
            if (keyToEnumMap.containsKey(key)) {
                keyToEnumMap[key]
            } else {
                null
            }

    }

    override fun formatForDisplay(context: Context, value: String): String {
        return when (this) {
            FILE_FORMAT -> when (value) {
                PrefsFormat.FORMAT_KEY_ENC -> context.getString(R.string.metadata_format_new)
                else                       -> context.getString(R.string.metadata_format_other)
            }

            CREATED_AT  -> value.replace("T", " ").replace("Z", " (UTC)")
            else        -> value
        }
    }
}
