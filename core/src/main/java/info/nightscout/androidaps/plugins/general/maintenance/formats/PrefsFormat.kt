package info.nightscout.androidaps.plugins.general.maintenance.formats

import android.content.Context
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.core.R
import kotlinx.parcelize.Parcelize
import java.io.File

enum class PrefsMetadataKey(val key: String, @DrawableRes val icon: Int, @StringRes val label: Int) {

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
            for (value in values()) {
                keyToEnumMap.put(value.key, value)
            }
        }

        fun fromKey(key: String): PrefsMetadataKey? {
            if (keyToEnumMap.containsKey(key)) {
                return keyToEnumMap.get(key)
            } else {
                return null
            }
        }

    }

    fun formatForDisplay(context: Context, value: String): String {
        return when (this) {
            FILE_FORMAT -> when (value) {
                ClassicPrefsFormat.FORMAT_KEY         -> context.getString(R.string.metadata_format_old)
                EncryptedPrefsFormat.FORMAT_KEY_ENC   -> context.getString(R.string.metadata_format_new)
                EncryptedPrefsFormat.FORMAT_KEY_NOENC -> context.getString(R.string.metadata_format_debug)
                else                                  -> context.getString(R.string.metadata_format_other)
            }
            CREATED_AT  -> value.replace("T", " ").replace("Z", " (UTC)")
            else        -> value
        }
    }

}

@Parcelize
data class PrefMetadata(var value: String, var status: PrefsStatus, var info: String? = null) : Parcelable

typealias PrefMetadataMap = Map<PrefsMetadataKey, PrefMetadata>

data class Prefs(val values: Map<String, String>, var metadata: PrefMetadataMap)

interface PrefsFormat {
    fun savePreferences(file: File, prefs: Prefs, masterPassword: String? = null)
    fun loadPreferences(file: File, masterPassword: String? = null): Prefs
    fun loadMetadata(contents: String? = null): PrefMetadataMap
    fun isPreferencesFile(file: File, preloadedContents: String? = null): Boolean
}

enum class PrefsStatus(@DrawableRes val icon: Int) {
    OK(R.drawable.ic_meta_ok),
    WARN(R.drawable.ic_meta_warning),
    ERROR(R.drawable.ic_meta_error),
    UNKNOWN(R.drawable.ic_meta_error),
    DISABLED(R.drawable.ic_meta_error)
}

class PrefFileNotFoundError(message: String) : Exception(message)
class PrefIOError(message: String) : Exception(message)
class PrefFormatError(message: String) : Exception(message)
