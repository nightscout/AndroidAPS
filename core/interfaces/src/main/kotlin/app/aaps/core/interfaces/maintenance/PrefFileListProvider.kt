package app.aaps.core.interfaces.maintenance

import app.aaps.core.interfaces.rx.weardata.CwfData
import java.io.File

interface PrefFileListProvider {

    val logsPath: String
    fun ensureTempDirExists(): File
    fun ensureExportDirExists(): File
    fun ensureExtraDirExists(): File
    fun newExportFile(): File
    fun newExportCsvFile(): File
    fun newCwfFile(filename: String, withDate: Boolean = true): File
    fun listPreferenceFiles(loadMetadata: Boolean = false): MutableList<PrefsFile>
    fun listCustomWatchfaceFiles(): MutableList<CwfData>
    fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata>
    fun formatExportedAgo(utcTime: String): String
}