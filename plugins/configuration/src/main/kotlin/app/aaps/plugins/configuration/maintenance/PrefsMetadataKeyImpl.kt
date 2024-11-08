package app.aaps.plugins.configuration.maintenance

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.data.PrefsFormat

enum class PrefsMetadataKeyImpl(override val key: String, @DrawableRes override val icon: Int, @StringRes override val label: Int) : PrefsMetadataKey {

    FILE_FORMAT("format", R.drawable.ic_meta_format, R.string.metadata_label_format),
    CREATED_AT("created_at", R.drawable.ic_meta_date, R.string.metadata_label_created_at),
    AAPS_VERSION("aaps_version", R.drawable.ic_meta_version, R.string.metadata_label_aaps_version),
    AAPS_FLAVOUR("aaps_flavour", R.drawable.ic_meta_flavour, R.string.metadata_label_aaps_flavour),
    DEVICE_NAME("device_name", R.drawable.ic_meta_name, R.string.metadata_label_device_name),
    DEVICE_MODEL("device_model", R.drawable.ic_meta_model, R.string.metadata_label_device_model),
    ENCRYPTION("encryption", R.drawable.ic_meta_encryption, R.string.metadata_label_encryption);

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
                PrefsFormat.FORMAT_KEY_ENC   -> context.getString(R.string.metadata_format_new)
                else                         -> context.getString(R.string.metadata_format_other)
            }

            CREATED_AT  -> value.replace("T", " ").replace("Z", " (UTC)")
            else        -> value
        }
    }
}
