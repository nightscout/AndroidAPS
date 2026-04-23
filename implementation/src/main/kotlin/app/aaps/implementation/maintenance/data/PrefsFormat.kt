package app.aaps.implementation.maintenance.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.maintenance.PrefMetadataMap
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsStatus
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

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
enum class PrefsStatusImpl : PrefsStatus {

    OK, WARN, ERROR, UNKNOWN, DISABLED;

    @IgnoredOnParcel
    override val icon: ImageVector
        get() = when (this) {
            OK                       -> Icons.Default.Check
            WARN                     -> Icons.Default.Warning
            ERROR, UNKNOWN, DISABLED -> Icons.Default.Error
        }

    override val isOk: Boolean get() = this == OK
    override val isWarning: Boolean get() = this == WARN
    override val isError: Boolean get() = this == ERROR
}

class PrefFileNotFoundError(message: String) : Exception(message)
class PrefIOError(message: String) : Exception(message)
class PrefFormatError(message: String) : Exception(message)
