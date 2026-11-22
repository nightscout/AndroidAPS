package app.aaps.implementation.maintenance

import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.autotune.AutotuneFileStore
import app.aaps.core.interfaces.autotune.AutotuneFolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.time.Clock

/**
 * The Autotune folders in `Documents/autotune`, which the Files app shows to the user.
 *
 * A phone running Android keeps them in its log folder; on iOS `Documents` is the place a user can
 * reach, the same place the settings exports go (see [IosPrefsFileAccess]). The zip is written by
 * [StoredZip], because iOS has no zip API to call.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosAutotuneFileStore(
    /** Overridden in a test so it neither writes into the user's Documents nor leaves files behind. */
    private val base: String? = defaultDocumentsSubdirectory("autotune")
) : AutotuneFileStore {

    private val manager get() = NSFileManager.defaultManager

    override val directory: String get() = base ?: "(no Documents directory)"

    private fun folder(folder: AutotuneFolder): String? = base?.let { "$it/${folder.folderName}" }

    private fun isDirectory(path: String): Boolean? = memScoped {
        val directory = alloc<BooleanVar>()
        if (manager.fileExistsAtPath(path, directory.ptr)) directory.value else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun names(path: String): List<String> = manager.contentsOfDirectoryAtPath(path, null) as? List<String> ?: emptyList()

    override fun createFolder(folder: AutotuneFolder): Boolean {
        val path = folder(folder) ?: return false
        if (isDirectory(path) == true) return false
        manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return true
    }

    override fun deleteFiles(folder: AutotuneFolder) {
        val path = folder(folder) ?: return
        for (name in names(path)) {
            val file = "$path/$name"
            if (isDirectory(file) == false) manager.removeItemAtPath(file, null)
        }
    }

    override fun write(folder: AutotuneFolder, name: String, text: String): Boolean {
        val path = folder(folder) ?: return false
        return NSString.create(string = text + "\n").writeToFile("$path/$name", atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun zip(name: String): Boolean {
        val base = base ?: return false
        val entries = mutableListOf<Pair<String, ByteArray>>()
        for (folder in AutotuneFolder.entries) collect("$base/${folder.folderName}", folder.folderName, entries)
        val modified = Clock.System.now().toLocalDateTime(SystemTimeZone.current())
        return StoredZip.build(entries, modified).toNSData().writeToFile("$base/$name", atomically = true)
    }

    /** The files of [path] and of the folders inside it, each named under [prefix], as the JVM zip does. */
    private fun collect(path: String, prefix: String, entries: MutableList<Pair<String, ByteArray>>) {
        for (name in names(path)) {
            val file = "$path/$name"
            when (isDirectory(file)) {
                true  -> collect(file, "$prefix/$name", entries)
                false -> NSData.dataWithContentsOfFile(file)?.let { entries += "$prefix/$name" to it.toByteArray() }
                null  -> Unit
            }
        }
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = bytes ?: return ByteArray(0)
        return if (size == 0) ByteArray(0) else bytes.reinterpret<ByteVar>().readBytes(size)
    }
}
