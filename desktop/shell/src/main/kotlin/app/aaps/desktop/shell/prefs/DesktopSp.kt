package app.aaps.desktop.shell.prefs

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.implementation.maintenance.DesktopFolders
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * The desktop side of AAPS preference storage, backed by a properties file.
 *
 * `SP` is the only genuinely platform specific part of preferences. The layer above it,
 * `PreferencesImpl`, is plain Kotlin - key lookup, defaults and range clamping - so desktop does not
 * need another `Preferences`, it needs a store for that one to sit on, which is this.
 *
 * ## Why a file rather than `java.util.prefs`
 *
 * `java.util.prefs.Preferences` writes to the Windows registry and to opaque per-user stores
 * elsewhere. A plain properties file next to the database is predictable, portable, and something a
 * user can read, back up or delete when supporting themselves - which matters more here than the few
 * lines it costs. It sits beside `aaps-desktop.db` for the same reason.
 *
 * ## The one thing worth reading carefully
 *
 * Every getter returns the caller's default when the key is absent, and also when the stored text
 * cannot be read as the type asked for. A stored value is text, so a `getInt` on something that is
 * not a number has to answer something: answering 0 would quietly replace an AAPS default with zero,
 * which is the difference between a preference screen that opens with sensible values and one that
 * opens with zeros.
 *
 * Not thread safe beyond the synchronized writes below, which is the same promise Android's
 * `SharedPreferences` makes for its own file.
 */
class DesktopSp(
    private val file: File = File(DesktopFolders.data, "preferences.properties")
) : KeyValueStore {

    private val properties = Properties().also { loaded ->
        if (file.isFile) file.inputStream().use(loaded::load)
    }

    /**
     * Writes the whole file. Either the new content lands or the old content stays - never a mixture.
     *
     * This used to open the real file and write into it, and opening a file for writing truncates it
     * at once. Anything that stopped the write after that point - a crash, a kill, a full disk, a
     * power cut - left a preferences file that was empty or cut in half. There is no second copy, so
     * the next start would read it, find nothing, and every AAPS setting on that machine would be
     * back to its default.
     *
     * So the content goes to a sibling file first, is forced to the disk, and only then replaces the
     * real one. A move inside one directory is atomic where the filesystem supports it, which means
     * a reader sees the old file or the new file and never a half-written one. Where it is not
     * supported (some network shares) the plain replacing move is the fallback - still better than
     * writing in place, because the content is already complete before the move starts.
     */
    private fun persist() {
        file.parentFile?.mkdirs()
        // Written whole each time. The file is a few hundred short lines, so the simplicity is worth
        // more than an incremental write would save.
        synchronized(properties) {
            val temp = File(file.parentFile, "${file.name}.new")
            FileOutputStream(temp).use { out ->
                properties.store(out, "AAPS desktop preferences")
                out.flush()
                // The bytes must be on the disk before the move publishes them, or a power cut can
                // leave the real name pointing at an empty file - the thing this is here to prevent.
                out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun raw(key: String): String? = properties.getProperty(key)

    private fun put(key: String, value: String) {
        properties.setProperty(key, value)
        persist()
    }

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        // One write at the end rather than one per key, which is what `edit` is for.
        object : KeyValueStore.Editor {
            override fun clear() = properties.clear()
            override fun remove(key: String) { properties.remove(key) }
            override fun putBoolean(key: String, value: Boolean) { properties.setProperty(key, value.toString()) }
            override fun putDouble(key: String, value: Double) { properties.setProperty(key, value.toString()) }
            override fun putLong(key: String, value: Long) { properties.setProperty(key, value.toString()) }
            override fun putInt(key: String, value: Int) { properties.setProperty(key, value.toString()) }
            override fun putString(key: String, value: String) { properties.setProperty(key, value) }
        }.block()
        persist()
    }

    override fun getAll(): Map<String, *> = properties.entries.associate { it.key.toString() to it.value }

    override fun clear() {
        properties.clear()
        persist()
    }

    override fun contains(key: String): Boolean = properties.containsKey(key)

    override fun remove(key: String) {
        properties.remove(key)
        persist()
    }

    override fun getString(key: String, defaultValue: String): String = raw(key) ?: defaultValue
    override fun getStringOrNull(key: String, defaultValue: String?): String? = raw(key) ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = raw(key)?.toBooleanStrictOrNull() ?: defaultValue
    override fun getDouble(key: String, defaultValue: Double): Double = raw(key)?.toDoubleOrNull() ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = raw(key)?.toIntOrNull() ?: defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = raw(key)?.toLongOrNull() ?: defaultValue

    override fun putString(key: String, value: String) = put(key, value)
    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())
    override fun putDouble(key: String, value: Double) = put(key, value.toString())
    override fun putInt(key: String, value: Int) = put(key, value.toString())
    override fun putLong(key: String, value: Long) = put(key, value.toString())

    override fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)
    override fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1L)
}
