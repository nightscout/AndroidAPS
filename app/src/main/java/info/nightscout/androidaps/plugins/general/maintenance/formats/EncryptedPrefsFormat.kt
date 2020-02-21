package info.nightscout.androidaps.plugins.general.maintenance.formats

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

object EncryptedPrefsFormat : PrefsFormat {

    const val FORMAT_KEY = "new_v1"

    override fun savePreferences(file:File, prefs: Prefs)  {

        val container = JSONObject()

        try {
            for ((key, value) in prefs.values) {
                container.put(key, value)
            }

            file.writeText(container.toString(2));

        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadPreferences(file:File): Prefs {

        val entries: MutableMap<String, String> = mutableMapOf()
        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()
        try {

            val jsonBody = file.readText()
            val container = JSONObject(jsonBody)

            for (key in container.keys()) {
                entries.put(key, container[key].toString())
            }

            metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(FORMAT_KEY, PrefsStatus.OK)

            return Prefs(entries, metadata)

        }  catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        } catch (e: JSONException){
            throw PrefFormatError("Mallformed preferences JSON file: "+e)
        }
    }


}