package info.nightscout.androidaps.plugins.general.maintenance.formats

import java.io.File

enum class PrefsMetadataKey(val key: String) {
    FILE_FORMAT("fileFormat")
}

data class PrefMetadata(var value : String, var status : PrefsStatus)

data class Prefs(val values : Map<String, String>, val metadata : Map<PrefsMetadataKey, PrefMetadata>)

interface PrefsFormat {
    fun savePreferences(file: File, prefs: Prefs)
    fun loadPreferences(file: File) : Prefs
}

enum class PrefsStatus {
    OK,
    WARN,
    ERROR
}

class PrefFileNotFoundError(message: String) : Exception(message)
class PrefIOError(message: String) : Exception(message)
class PrefFormatError(message: String) : Exception(message)