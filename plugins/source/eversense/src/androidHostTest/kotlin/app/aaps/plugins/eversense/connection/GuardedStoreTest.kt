package app.aaps.plugins.eversense.connection

import app.aaps.plugins.eversense.MemoryStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** The store of work that can outlive its reason: once it is no longer valid, nothing reads or writes through it. */
class GuardedStoreTest {

    private val delegate = MemoryStore()
    private var valid = true
    private val store = GuardedStore(delegate) { valid }

    @Test fun whileValidItReadsAndWritesTheStore() {
        delegate.values["old"] = "value"
        assertThat(store.getString("old", null)).isEqualTo("value")
        assertThat(store.edit().putLong("counter", 2).putBoolean("flag", true).commit()).isTrue()
        store.edit().putInt("days", 14).apply()
        assertThat(delegate.getLong("counter", 0)).isEqualTo(2)
        assertThat(delegate.getBoolean("flag", false)).isTrue()
        assertThat(store.getInt("days", 0)).isEqualTo(14)
        assertThat(store.contains("counter")).isTrue()
    }

    /** An editor taken before a sign-out must not bring back the login or the token the sign-out removed. */
    @Test fun anEditorTakenBeforeTheEndCannotWriteAfterIt() {
        val pending = store.edit().putString("username", "old").putString("access_token", "stale")
        valid = false
        assertThrows(CancellationException::class.java) { pending.apply() }
        assertThrows(CancellationException::class.java) { pending.commit() }
        assertThat(delegate.values).isEmpty()
        assertThat(delegate.commits).isEqualTo(0)
    }

    @Test fun noReadOrNewEditAfterTheEnd() {
        delegate.values["token"] = "secret"
        valid = false
        assertThrows(CancellationException::class.java) { store.getString("token", null) }
        assertThrows(CancellationException::class.java) { store.getLong("token", 0) }
        assertThrows(CancellationException::class.java) { store.getInt("token", 0) }
        assertThrows(CancellationException::class.java) { store.getBoolean("token", false) }
        assertThrows(CancellationException::class.java) { store.contains("token") }
        assertThrows(CancellationException::class.java) { store.edit() }
    }

    /** The check is made on each use, not once: work that started valid stops at the first use after the end. */
    @Test fun validityIsCheckedOnEachUse() {
        assertThat(store.getString("token", "none")).isEqualTo("none")
        valid = false
        assertThrows(CancellationException::class.java) { store.getString("token", "none") }
        valid = true
        assertThat(store.getString("token", "none")).isEqualTo("none")
    }
}
