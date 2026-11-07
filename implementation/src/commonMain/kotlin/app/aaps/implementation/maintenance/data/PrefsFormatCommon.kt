package app.aaps.implementation.maintenance.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.maintenance.PrefsStatus

/**
 * The parts of the export format that carry no file access, so every platform can share them.
 *
 * The `PrefsFormat` interface next to this one still speaks `DocumentFile` and stays on Android.
 * What is here is the vocabulary the format is written in - the name it stamps into a file, how the
 * status of a loaded file is shown, and the errors reading one can raise. None of that is Android's,
 * and iOS needs all of it to read a file written on a phone.
 */
object PrefsFormatKey {

    /** The value written into a file's `format` field, and the only one this reader accepts. */
    const val FORMAT_KEY_ENC = "aaps_encrypted"
}

enum class PrefsStatusImpl : PrefsStatus {

    OK, WARN, ERROR, UNKNOWN, DISABLED;

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
