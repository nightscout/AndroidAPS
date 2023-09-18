package info.nightscout.interfaces.maintenance

import androidx.annotation.DrawableRes
import info.nightscout.configuration.R
import kotlinx.parcelize.Parcelize
import java.io.File

typealias PrefMetadataMap = Map<PrefsMetadataKey, PrefMetadata>

data class Prefs(val values: Map<String, String>, var metadata: PrefMetadataMap)

interface PrefsFormat {
    companion object {

        const val FORMAT_KEY_ENC = "aaps_encrypted"
        const val FORMAT_KEY_NOENC = "aaps_structured"
    }

    fun savePreferences(file: File, prefs: Prefs, masterPassword: String? = null)
    fun loadPreferences(file: File, masterPassword: String? = null): Prefs
    fun loadMetadata(contents: String? = null): PrefMetadataMap
    fun isPreferencesFile(file: File, preloadedContents: String? = null): Boolean
}

@Parcelize
enum class PrefsStatusImpl(@DrawableRes override val icon: Int) : PrefsStatus {

    OK(R.drawable.ic_meta_ok),
    WARN(R.drawable.ic_meta_warning),
    ERROR(R.drawable.ic_meta_error),
    UNKNOWN(R.drawable.ic_meta_error),
    DISABLED(R.drawable.ic_meta_error)
}

class PrefFileNotFoundError(message: String) : Exception(message)
class PrefIOError(message: String) : Exception(message)
class PrefFormatError(message: String) : Exception(message)
