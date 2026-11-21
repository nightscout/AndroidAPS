package app.aaps.plugins.eversense.connection

import app.aaps.plugins.eversense.MemoryStore
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The store of work that can outlive its reason: once it is no longer valid, nothing reads or writes through it. */
class GuardedStoreTest {

    private val delegate = MemoryStore()
    private var valid = true
    private val store = GuardedStore(delegate) { valid }

    @Test fun whileValidItReadsAndWritesTheStore() {
        delegate.values["old"] = "value"
        assertEquals("value", store.getString("old", null))
        assertTrue(store.edit().putLong("counter", 2).putBoolean("flag", true).commit())
        store.edit().putInt("days", 14).apply()
        assertEquals(2, delegate.getLong("counter", 0))
        assertTrue(delegate.getBoolean("flag", false))
        assertEquals(14, store.getInt("days", 0))
        assertTrue(store.contains("counter"))
    }

    /** An editor taken before a sign-out must not bring back the login or the token the sign-out removed. */
    @Test fun anEditorTakenBeforeTheEndCannotWriteAfterIt() {
        val pending = store.edit().putString("username", "old").putString("access_token", "stale")
        valid = false
        assertFailsWith<CancellationException> { pending.apply() }
        assertFailsWith<CancellationException> { pending.commit() }
        assertEquals(emptyMap(), delegate.values)
        assertEquals(0, delegate.commits)
    }

    @Test fun noReadOrNewEditAfterTheEnd() {
        delegate.values["token"] = "secret"
        valid = false
        assertFailsWith<CancellationException> { store.getString("token", null) }
        assertFailsWith<CancellationException> { store.getLong("token", 0) }
        assertFailsWith<CancellationException> { store.getInt("token", 0) }
        assertFailsWith<CancellationException> { store.getBoolean("token", false) }
        assertFailsWith<CancellationException> { store.contains("token") }
        assertFailsWith<CancellationException> { store.edit() }
    }

    /** The check is made on each use, not once: work that started valid stops at the first use after the end. */
    @Test fun validityIsCheckedOnEachUse() {
        assertEquals("none", store.getString("token", "none"))
        valid = false
        assertFailsWith<CancellationException> { store.getString("token", "none") }
        valid = true
        assertEquals("none", store.getString("token", "none"))
    }
}
