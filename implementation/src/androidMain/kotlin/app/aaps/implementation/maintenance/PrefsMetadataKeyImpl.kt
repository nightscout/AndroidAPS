package app.aaps.implementation.maintenance

import app.aaps.implementation.ImplementationStrings
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
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.R
import app.aaps.implementation.maintenance.data.PrefsFormat

enum class PrefsMetadataKeyImpl(override val key: String, override val icon: ImageVector, override val label: TextRef) : PrefsMetadataKey {

    FILE_FORMAT("format", Icons.Default.Description, ImplementationStrings.metadata_label_format),
    CREATED_AT("created_at", Icons.Default.Event, ImplementationStrings.metadata_label_created_at),
    AAPS_VERSION("aaps_version", Icons.Default.Info, ImplementationStrings.metadata_label_aaps_version),
    AAPS_FLAVOUR("aaps_flavour", Icons.Default.Style, ImplementationStrings.metadata_label_aaps_flavour),
    DEVICE_NAME("device_name", Icons.Default.Badge, ImplementationStrings.metadata_label_device_name),
    DEVICE_MODEL("device_model", Icons.Default.PhoneAndroid, ImplementationStrings.metadata_label_device_model),
    ENCRYPTION("encryption", Icons.Default.Lock, ImplementationStrings.metadata_label_encryption);

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

    override fun formatForDisplay(value: String): TextRef =
        when (this) {
            FILE_FORMAT -> when (value) {
                PrefsFormat.FORMAT_KEY_ENC -> TextRef.AndroidRes(R.string.metadata_format_new)
                else                       -> TextRef.AndroidRes(R.string.metadata_format_other)
            }

            CREATED_AT  -> TextRef.Literal(value.replace("T", " ").replace("Z", " (UTC)"))
            else        -> TextRef.Literal(value)
        }
}
