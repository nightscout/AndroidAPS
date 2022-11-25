package info.nightscout.interfaces.maintenance

import java.io.File

interface PrefFileListProvider {

    val logsPath: String
    fun ensureTempDirExists(): File
    fun ensureExportDirExists(): File
    fun ensureExtraDirExists(): File
    fun newExportFile(): File
    fun newExportCsvFile(): File
    fun listPreferenceFiles(loadMetadata: Boolean = false): MutableList<PrefsFile>
    fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata>
    fun formatExportedAgo(utcTime: String): String
}