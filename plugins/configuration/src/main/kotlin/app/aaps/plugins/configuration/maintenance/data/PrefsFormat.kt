package app.aaps.plugins.configuration.maintenance.data

import androidx.annotation.DrawableRes
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.maintenance.PrefsStatus
import app.aaps.plugins.configuration.R
import kotlinx.parcelize.Parcelize

typealias PrefMetadataMap = Map<PrefsMetadataKey, PrefMetadata>

data class Prefs(val values: Map<String, String>, var metadata: PrefMetadataMap)

interface PrefsFormat {
    companion object {

        const val FORMAT_KEY_ENC = "aaps_encrypted"
    }

    fun savePreferences(file: DocumentFile, prefs: Prefs, masterPassword: String? = null)
    fun loadPreferences(contents: String, masterPassword: String? = null): Prefs
    fun loadMetadata(contents: String? = null): PrefMetadataMap
    fun isPreferencesFile(file: DocumentFile, preloadedContents: String? = null): Boolean
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
