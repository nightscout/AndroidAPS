package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefsStatusImpl

/**
 * Turning stored preferences into an export and back, with no file access and no platform in it.
 *
 * [PrefsFormatCodec] owns the shape of the file. This owns the two steps on either side of it -
 * which settings go in, and what happens to the ones that come out - and they are the same
 * questions on every platform. Keeping them here means the answers cannot drift between shells,
 * and it means they can be tested against a store held in memory rather than a device.
 *
 * The shell above this still differs, and should: picking a file is Android's storage access
 * framework, a Documents directory on iOS and a file dialog on the desktop.
 */
class PrefsTransfer(
    private val codec: PrefsFormatCodec,
    private val store: KeyValueStore,
    private val isExportable: (String) -> Boolean
) {

    /**
     * The text of an export holding every setting that is meant to travel.
     *
     * A preference that is not registered as exportable is left out deliberately. Those describe the
     * device rather than the user's therapy - a paired pump's address, a cached token - and carrying
     * them to another phone would move a fact that is not true there.
     */
    fun exportContents(metadata: Map<PrefsMetadataKey, PrefMetadata>, password: String): String =
        codec.encode(Prefs(exportableValues(), metadata), password)

    private fun exportableValues(): Map<String, String> =
        store.getAll()
            .filterKeys { isExportable(it) }
            .mapNotNull { (key, value) -> value?.let { key to it.toString() } }
            .toMap()

    /**
     * Reads a file the user picked and says whether it can be imported.
     *
     * A wrong password is told apart from a damaged file on purpose: only one of the two is worth
     * offering to try again, and a user who mistyped should not be told their backup is broken.
     */
    fun importResult(contents: String, password: String, engineeringMode: Boolean): ImportDecryptResult =
        try {
            val prefs = codec.decode(contents, password)
            val importOk = prefs.metadata.values.none { it.status == PrefsStatusImpl.ERROR }
            val encryptionFailed = prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status == PrefsStatusImpl.ERROR
            if (!importOk && encryptionFailed) ImportDecryptResult.WrongPassword
            else ImportDecryptResult.Success(prefs, importOk, (importOk || engineeringMode) && prefs.values.isNotEmpty())
        } catch (e: Throwable) {
            ImportDecryptResult.Error(e.message ?: "Unknown error")
        }

    /**
     * Replaces what is stored with what the file holds.
     *
     * The store is cleared first, so an import is the file and not the file merged over whatever was
     * there. A setting the old configuration had and the new one does not would otherwise survive an
     * import that was meant to replace it.
     *
     * Booleans are written as booleans. They come back from the file as the strings `true` and
     * `false`, and a store that kept them as text would answer the wrong type to every later read.
     */
    fun applyImported(prefs: Prefs) {
        store.clear()
        prefs.values.forEach { (key, value) ->
            if (value == "true" || value == "false") store.putBoolean(key, value.toBoolean()) else store.putString(key, value)
        }
    }
}
