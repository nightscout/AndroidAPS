package app.aaps.implementation.maintenance.data

import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.maintenance.PrefMetadataMap
import app.aaps.core.interfaces.maintenance.Prefs

/**
 * Reading and writing an export through Android's storage access framework.
 *
 * Only the file access is here. The format itself - the JSON layout, the hashes and the encryption -
 * is `PrefsFormatCodec` in commonMain, so that iOS reads and writes the same files rather than a
 * second format that happens to look similar. See `PrefsFormatCommon.kt` for the shared vocabulary.
 */
interface PrefsFormat {
    companion object {

        const val FORMAT_KEY_ENC = PrefsFormatKey.FORMAT_KEY_ENC
    }

    fun savePreferences(file: DocumentFile, prefs: Prefs, masterPassword: String? = null)
    fun loadPreferences(contents: String, masterPassword: String? = null): Prefs
    fun loadMetadata(contents: String? = null): PrefMetadataMap
    fun isPreferencesFile(file: DocumentFile, preloadedContents: String? = null): Boolean
}
