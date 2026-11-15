package app.aaps.implementation.sharedPreferences

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Registering keys while somebody reads the key set.
 *
 * On 4.0.0-rc1 `getSyncKeys` crashed with ConcurrentModificationException: a plugin registered its
 * keys on one thread while the Nightscout sync publisher walked the set on another. A unit test cannot
 * time two threads, so these tests register from INSIDE the walk, on the same thread. For a
 * LinkedHashSet that is the same failure, and it happens every time instead of sometimes.
 */
class PreferencesImplRegistrationTest {

    private fun preferences(): PreferencesImpl {
        val config = mock<Config>()
        whenever(config.APS).thenReturn(true)
        whenever(config.platform).thenReturn(AppPlatform.Android)
        return PreferencesImpl(FileKeyValueStore(), { mock() }, { mock() }, { mock() }, mock(), config, mock())
    }

    private class PlainKey(override val key: String) : NonPreferenceKey

    /** Runs [onSyncRead] each time its [sync] is read, which getSyncKeys does for every key. */
    private class RegisteringKey(override val key: String, private val onSyncRead: () -> Unit) : NonPreferenceKey {

        override val sync: SyncSpec?
            get() {
                onSyncRead()
                return null
            }
    }

    @Test
    fun `a key registered while the sync keys are read does not break the read`() {
        val prefs = preferences()
        val late = PlainKey("test_registered_during_read")
        var registered = false
        prefs.registerPreferences(
            listOf(
                RegisteringKey("test_registering") {
                    if (!registered) {
                        registered = true
                        prefs.registerPreferences(listOf(late))
                    }
                },
                // Not last on purpose. The walk must still have a next key after the registration,
                // otherwise a LinkedHashSet ends the walk without checking for the change.
                PlainKey("test_after")
            )
        )

        prefs.getSyncKeys()

        assertThat(registered).isTrue()
        assertThat(prefs.getAllKeys()).contains(late)
    }

    @Test
    fun `registering keeps the order and adds each key only once`() {
        val prefs = preferences()
        val first = PlainKey("test_first")
        val second = PlainKey("test_second")

        prefs.registerPreferences(listOf(first, second))
        prefs.registerPreferences(listOf(first))

        val keys = prefs.getAllKeys()
        assertThat(keys.count { it == first }).isEqualTo(1)
        assertThat(keys.takeLast(2)).containsExactly(first, second).inOrder()
    }
}
