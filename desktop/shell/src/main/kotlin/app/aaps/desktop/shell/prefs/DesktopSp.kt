package app.aaps.desktop.shell.prefs

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.implementation.maintenance.DesktopFolders
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
            moveIntoPlace(temp)
        }
    }

    /**
     * Replaces the real file with [temp], retrying a few times before giving up.
     *
     * On Windows a replacing move fails with [java.nio.file.AccessDeniedException] whenever anything
     * else holds the target open, and on a normal machine something does: the virus scanner or the
     * search indexer opens a file that was just written. It is not a permission problem and it does
     * not stay - the next attempt a few milliseconds later works.
     *
     * This used to catch [AtomicMoveNotSupportedException] alone, so that failure escaped. It did not
     * only lose the one write: the exception travelled up and the whole incoming settings sync step
     * was reported as failed, which is how a preference arriving from the master went missing on a
     * Windows client (seen on 2026-10-14, `Load step SETTINGS: Failure`). The same trap is written up
     * in `AAPSLoggerDesktop.rotate`, which hit it first and handles it there.
     *
     * A move that still fails after the retries is thrown, as before. The old content stays on disk
     * and the new value is already in [properties], so the running app is right either way - only the
     * next start would read the older value.
     */
    private fun moveIntoPlace(temp: File) {
        var lastFailure: IOException? = null
        repeat(MOVE_ATTEMPTS) { attempt ->
            try {
                replace(temp)
                return
            } catch (e: IOException) {
                lastFailure = e
                // Doubling, and only between attempts, so a run that succeeds waits not at all and the
                // worst case stays about 60 ms - short enough to sit in a preference setter.
                if (attempt < MOVE_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS shl attempt)
            }
        }
        throw lastFailure ?: IOException("Cannot replace ${file.path}")
    }

    /** One attempt, atomic where the filesystem allows it. */
    private fun replace(temp: File) {
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            // Some network shares. Retrying this one is pointless, but the caller's loop costs nothing
            // because a share that cannot move atomically cannot start to, and the plain move below is
            // what answers from here on.
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
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

    companion object {

        /** How many times [moveIntoPlace] tries before it gives up. */
        const val MOVE_ATTEMPTS = 3

        /** The wait before the second attempt, doubled for each one after it. */
        const val RETRY_DELAY_MS = 20L
    }
}
