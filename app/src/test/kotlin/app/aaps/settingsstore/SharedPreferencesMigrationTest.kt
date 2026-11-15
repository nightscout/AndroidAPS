package app.aaps.settingsstore

import app.aaps.shared.impl.sharedPreferences.StoreCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The move on its own. The DataStore around it is in [AndroidStorageTest]; here the test plays DataStore:
 * it calls migrate, writes the result as DataStore would, then calls cleanUp.
 */
class SharedPreferencesMigrationTest {

    @TempDir lateinit var dir: File

    private val cipher = KeystoreStoreCipher(MemoryKeys())
    private val oldFile get() = File(dir, "old.xml")
    private val storeFile get() = File(dir, "preferences.store")
    private val marker get() = File(dir, "moved")
    private val log = mutableListOf<String>()
    private val skipped = mutableListOf<String>()

    private fun storable(old: Map<String, *>) = SharedPreferencesMigration.storable(old) { key, _ -> skipped += key }
    private fun migration(old: Map<String, *>) = SharedPreferencesMigration(oldFile, { old }, storeFile, cipher, marker) { log += it }

    /** What DataStore does around a migration. Returns what it would store. */
    private fun run(sut: SharedPreferencesMigration, current: Map<String, Any> = emptyMap()): Map<String, Any> = runBlocking {
        if (!sut.shouldMigrate(current)) return@runBlocking current
        val result = sut.migrate(current)
        if (result != current) storeFile.writeBytes(StoreCodec.encode(result, cipher))
        sut.cleanUp()
        result
    }

    @Test
    fun `text, true or false and whole numbers keep their type`() {
        val moved = storable(mapOf("s" to "text", "b" to true, "i" to 7, "l" to 1_700_000_000_000L))

        assertThat(moved).containsExactly("s", "text", "b", true, "i", 7, "l", 1_700_000_000_000L)
        assertThat(moved["i"]).isInstanceOf(Int::class.javaObjectType)
        assertThat(moved["l"]).isInstanceOf(Long::class.javaObjectType)
    }

    @Test
    fun `a float becomes the double it was meant to be, not its binary neighbour`() {
        // What SPImpl.putDouble(0.1) left in the file. A plain toDouble() gives 0.10000000149011612.
        val moved = storable(mapOf("isf" to 0.1f, "target" to 5.5f))

        assertThat(moved["isf"]).isEqualTo(0.1)
        assertThat(moved["target"]).isEqualTo(5.5)
    }

    @Test
    fun `a set of strings is left behind and named, everything else still moves`() {
        val moved = storable(mapOf("set" to setOf("a", "b"), "s" to "text"))

        assertThat(moved).containsExactly("s", "text")
        assertThat(skipped).containsExactly("set")
    }

    @Test
    fun `a null value is dropped quietly`() {
        assertThat(storable(mapOf("n" to null))).isEmpty()
        assertThat(skipped).isEmpty()
    }

    @Test
    fun `the move reads back every value it wrote and marks itself done, leaving the old file alone`() {
        oldFile.writeText("<map/>")

        val stored = run(migration(mapOf("a" to "x", "isf" to 0.1f, "n" to 3)))

        assertThat(stored).containsExactly("a", "x", "isf", 0.1, "n", 3)
        assertThat(marker.exists()).isTrue()
        assertThat(oldFile.readText()).isEqualTo("<map/>")
        assertThat(log.single()).isEqualTo("Moved 3 settings from old.xml into the encrypted store, 3 read back the same. The old file is kept for one release.")
    }

    @Test
    fun `once marked, nothing moves again - also when the old file changed`() {
        oldFile.writeText("<map/>")
        run(migration(mapOf("a" to "x")))

        val stored = run(migration(mapOf("a" to "old", "b" to "new")), current = mapOf("a" to "y"))

        assertThat(stored).containsExactly("a", "y")
    }

    @Test
    fun `a fresh install has nothing to move, and is marked so a later old file is not moved either`() {
        assertThat(run(migration(mapOf("a" to "x")))).isEmpty()
        assertThat(marker.exists()).isTrue()

        oldFile.writeText("<map/>")
        assertThat(run(migration(mapOf("a" to "x")))).isEmpty()
    }

    @Test
    fun `only the backup of the old file is there - it is still moved`() {
        // A process killed while SharedPreferences wrote leaves only the .bak, which it puts back on reading.
        File(oldFile.path + ".bak").writeText("<map/>")

        assertThat(run(migration(mapOf("a" to "x")))).containsExactly("a", "x")
    }

    @Test
    fun `an old file that gives no settings is not marked, so the next start tries again`() {
        // SharedPreferences answers a damaged file with an empty map, not with an error.
        oldFile.writeText("damaged")

        run(migration(emptyMap<String, Any>()))

        assertThat(marker.exists()).isFalse()
        assertThat(log.single()).contains("gave no settings")
    }

    @Test
    fun `after a start cut short, values already in the store win and the move is marked done`() {
        oldFile.writeText("<map/>")

        val stored = run(migration(mapOf("a" to "old", "b" to "old")), current = mapOf("a" to "newer", "b" to "old"))

        assertThat(stored).containsExactly("a", "newer", "b", "old")
        assertThat(marker.exists()).isTrue()
    }
}
