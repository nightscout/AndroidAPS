package app.aaps.settingsstore

import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyStoreException

/**
 * The phone's store on real files, with a key in memory in place of the Keystore and a map in place of the
 * old SharedPreferences file.
 */
class AndroidStorageTest {

    @TempDir lateinit var dir: File

    private val keys = MemoryKeys()
    private val store get() = File(dir, "store")
    private val oldFile get() = File(dir, "old.xml")
    private var oldValues: Map<String, *> = emptyMap<String, Any>()

    private fun setup() = AndroidStorage.Setup(store, keys, oldFile, { oldValues })

    /** Opens, runs [block], and closes again, so the next open of the same file is allowed. */
    private fun <T> opened(block: (AndroidStorage.Opened) -> T): T {
        val opened = AndroidStorage.openWith(setup())
        try {
            return block(opened)
        } finally {
            opened.close()
        }
    }

    private fun storeFile() = File(store, "preferences.store")

    @Test
    fun `the first open moves the old settings, with a float as the double it was meant to be`() {
        oldFile.writeText("<map/>")
        oldValues = mapOf("units" to "mmol", "isf" to 0.1f, "count" to 3, "since" to 1_700_000_000_000L, "on" to true)

        opened { opened ->
            assertThat(opened.failure).isNull()
            assertThat(opened.store.getString("units", "")).isEqualTo("mmol")
            assertThat(opened.store.getDouble("isf", 0.0)).isEqualTo(0.1)
            assertThat(opened.store.getInt("count", 0)).isEqualTo(3)
            assertThat(opened.store.getLong("since", 0L)).isEqualTo(1_700_000_000_000L)
            assertThat(opened.store.getBoolean("on", false)).isTrue()
            assertThat(opened.notes.last()).contains("Moved 5 settings")
        }
        // Kept for one release, unchanged.
        assertThat(oldFile.readText()).isEqualTo("<map/>")
    }

    @Test
    fun `the settings are in the encrypted file, not in plain text`() {
        oldFile.writeText("<map/>")
        oldValues = mapOf("nsclient_token" to "secret-token-123")

        opened { }

        assertThat(storeFile().readBytes().decodeToString()).doesNotContain("secret-token-123")
    }

    @Test
    fun `the move runs once - a later change of the old file is not moved again`() {
        oldFile.writeText("<map/>")
        oldValues = mapOf("units" to "mmol")
        opened { it.store.edit(commit = true) { putString("units", "mg/dl") } }

        oldValues = mapOf("units" to "mmol", "extra" to "x")
        opened { opened ->
            assertThat(opened.store.getString("units", "")).isEqualTo("mg/dl")
            assertThat(opened.store.contains("extra")).isFalse()
            assertThat(opened.notes).isEmpty()
        }
    }

    @Test
    fun `a fresh install has nothing to move and starts empty`() {
        opened { opened ->
            assertThat(opened.failure).isNull()
            assertThat(opened.store.getAll()).isEmpty()
        }
    }

    @Test
    fun `what is saved is there after the next open`() {
        opened { it.store.edit(commit = true) { putString("a", "1"); putDouble("b", 2.5) } }

        opened { opened ->
            assertThat(opened.store.getString("a", "")).isEqualTo("1")
            assertThat(opened.store.getDouble("b", 0.0)).isEqualTo(2.5)
        }
    }

    @Test
    fun `a store whose key is gone is unreadable, runs in memory and leaves the file alone`() {
        opened { it.store.edit(commit = true) { putString("a", "1") } }
        val before = storeFile().readBytes()
        keys.delete()

        opened { opened ->
            assertThat(opened.failure).isInstanceOf(StoreUnreadableException::class.java)
            assertThat(opened.store.getAll()).isEmpty()
            opened.store.edit(commit = true) { putString("b", "2") }
            assertThat(opened.store.getString("b", "")).isEqualTo("2")
        }
        assertThat(storeFile().readBytes()).isEqualTo(before)
        // Making a key there would replace one that may only be missing for now.
        assertThat(keys.key).isNull()
    }

    @Test
    fun `a Keystore that does not answer is not unreadable`() {
        opened { it.store.edit(commit = true) { putString("a", "1") } }
        keys.unavailable = KeyStoreException("busy")

        opened { opened ->
            assertThat(opened.failure).isNotNull()
            assertThat(opened.failure).isNotInstanceOf(StoreUnreadableException::class.java)
            assertThat(opened.store.getAll()).isEmpty()
        }
    }

    @Test
    fun `start fresh sets the file aside, removes the key, and the next open is empty - without the old settings`() {
        oldFile.writeText("<map/>")
        oldValues = mapOf("units" to "mmol")
        opened { }
        keys.delete()
        opened { assertThat(it.failure).isInstanceOf(StoreUnreadableException::class.java) }

        val setAside = AndroidStorage.startFreshWith(setup())

        assertThat(setAside.exists()).isTrue()
        assertThat(setAside.name).startsWith("preferences.store.unreadable-")
        opened { opened ->
            assertThat(opened.failure).isNull()
            // Not the settings of the day of the move: those can be dangerously old.
            assertThat(opened.store.getAll()).isEmpty()
        }
    }
}
