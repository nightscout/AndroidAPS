package info.nightscout.androidaps.plugins.general.maintenance.formats

import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs
import java.io.*


object ClassicPrefsFormat : PrefsFormat {

    const val FORMAT_KEY = "old"

    override fun savePreferences(file:File, prefs: Prefs)  {
        try {
            val fw = FileWriter(file)
            val pw = PrintWriter(fw)
            for ((key, value) in prefs.values) {
                pw.println(key + "::" + value)
            }
            pw.close()
            fw.close()
        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadPreferences(file:File): Prefs {
        var line: String
        var lineParts: Array<String>
        val entries: MutableMap<String, String> = mutableMapOf()
        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()
        try {
            val reader = BufferedReader(FileReader(file))
            while (reader.readLine().also { line = it } != null) {
                lineParts = line.split("::").toTypedArray()
                if (lineParts.size == 2) {
                    entries[lineParts[0]] = lineParts[1]
                }
            }
            reader.close()

            metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(FORMAT_KEY, PrefsStatus.WARN)

            return Prefs(entries, metadata)

        }  catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }


}