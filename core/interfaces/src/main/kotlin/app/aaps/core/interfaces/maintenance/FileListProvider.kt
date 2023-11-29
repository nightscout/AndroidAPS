package app.aaps.core.interfaces.maintenance

import app.aaps.core.interfaces.rx.weardata.CwfFile
import java.io.File

interface FileListProvider {

    val resultPath: File
    fun ensureTempDirExists(): File
    fun ensureExportDirExists(): File
    fun ensureExtraDirExists(): File
    fun ensureResultDirExists(): File
    fun newExportFile(): File
    fun newExportCsvFile(): File
    fun newResultFile(): File
    fun newCwfFile(filename: String, withDate: Boolean = true): File
    fun listPreferenceFiles(): MutableList<PrefsFile>
    fun listCustomWatchfaceFiles(): MutableList<CwfFile>
    fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata>
    fun formatExportedAgo(utcTime: String): String
}