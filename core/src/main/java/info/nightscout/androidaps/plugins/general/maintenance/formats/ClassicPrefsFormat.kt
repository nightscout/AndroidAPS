package info.nightscout.androidaps.plugins.general.maintenance.formats

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.storage.Storage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassicPrefsFormat @Inject constructor(
    private var resourceHelper: ResourceHelper,
    private var storage: Storage
) : PrefsFormat {

    companion object {

        val FORMAT_KEY = "aaps_old"
    }

    override fun isPreferencesFile(file: File, preloadedContents: String?): Boolean {
        val contents = preloadedContents ?: storage.getFileContents(file)
        return contents.contains("units::" + Constants.MGDL) || contents.contains("units::" + Constants.MMOL) || contents.contains("language::") || contents.contains("I_understand::")
    }

    override fun savePreferences(file: File, prefs: Prefs, masterPassword: String?) {
        try {
            val contents = prefs.values.entries.joinToString("\n") { entry ->
                "${entry.key}::${entry.value}"
            }
            storage.putFileContents(file, contents)
        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadPreferences(file: File, masterPassword: String?): Prefs {
        var lineParts: Array<String>
        val entries: MutableMap<String, String> = mutableMapOf()
        try {

            val rawLines = storage.getFileContents(file).split("\n")
            rawLines.forEach { line ->
                lineParts = line.split("::").toTypedArray()
                if (lineParts.size == 2) {
                    entries[lineParts[0]] = lineParts[1]
                }
            }

            return Prefs(entries, loadMetadata())

        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadMetadata(contents: String?): PrefMetadataMap {
        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()
        metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(FORMAT_KEY, PrefsStatus.WARN, resourceHelper.gs(R.string.metadata_warning_outdated_format))
        return metadata
    }

}