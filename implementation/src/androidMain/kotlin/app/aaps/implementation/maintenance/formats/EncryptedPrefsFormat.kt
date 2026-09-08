package app.aaps.implementation.maintenance.formats

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.maintenance.PrefMetadataMap
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.R
import app.aaps.implementation.maintenance.data.PrefFileNotFoundError
import app.aaps.implementation.maintenance.data.PrefIOError
import app.aaps.implementation.maintenance.data.PrefsFormat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The export format on Android: the file access, and nothing else.
 *
 * The format itself moved to [PrefsFormatCodec] in commonMain, so Android and iOS write and read
 * the same files instead of each carrying its own copy of the layout, the hashes and the cipher.
 * What is left here is genuinely Android's - `DocumentFile` and the storage access framework, and
 * the permission failure that comes with them, which has no counterpart on a platform where the app
 * owns its own directory.
 *
 * `EncryptedPrefsFormatTest` still points at this class, so the frozen export it has guarded for
 * years now guards the shared codec through it.
 */
@SingleIn(AppScope::class)
class EncryptedPrefsFormat @Inject constructor(
    private val rh: ResourceHelper,
    private val storage: Storage,
    private val context: Context,
    private val rxBus: RxBus,
    secureEncrypt: SecureEncrypt
) : PrefsFormat {

    private val codec = PrefsFormatCodec(platformCryptoPrimitives(), rh, secureEncrypt)

    override fun isPreferencesFile(file: DocumentFile, preloadedContents: String?): Boolean {
        if (file.name?.endsWith(".json") != true) return false
        return try {
            codec.looksLikePreferences(preloadedContents ?: storage.getFileContents(context.contentResolver, file))
        } catch (_: SecurityException) {
            reportNoAccess()
            false
        }
    }

    override fun savePreferences(file: DocumentFile, prefs: Prefs, masterPassword: String?) {
        try {
            storage.putFileContents(context.contentResolver, file, codec.encode(prefs, masterPassword))
        } catch (_: FileNotFoundException) {
            throw PrefFileNotFoundError(file.name ?: "UNKNOWN")
        } catch (_: IOException) {
            throw PrefIOError(file.name ?: "UNKNOWN")
        } catch (_: SecurityException) {
            reportNoAccess()
            throw PrefFileNotFoundError(file.name ?: "UNKNOWN")
        }
    }

    override fun loadPreferences(contents: String, masterPassword: String?): Prefs = codec.decode(contents, masterPassword)

    override fun loadMetadata(contents: String?): PrefMetadataMap = codec.decodeMetadata(contents)

    /** The user picked a directory AAPS may not read. Only Android can be in this state. */
    private fun reportNoAccess() {
        rxBus.send(
            EventShowSnackbar(
                rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly),
                EventShowSnackbar.Type.Error
            )
        )
    }
}
